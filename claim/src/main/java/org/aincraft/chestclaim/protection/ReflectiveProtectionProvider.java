package org.aincraft.chestclaim.protection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Shared reflection and owner normalization for optional protection plugins. */
abstract class ReflectiveProtectionProvider implements ProtectionProvider {

  private static final int BLOCK_PARAMETER_COUNT = 1;
  private static final int COORDINATE_PARAMETER_COUNT = 4;
  private static final Object[] UNSUPPORTED_ARGUMENTS = {};

  private static final List<String> OWNER_KEYS =
      List.of("owner", "ownerUuid", "ownerUUID", "ownerId", "ownerName");
  private static final List<String> OWNER_ID_METHODS = List.of("getUniqueId", "getUUID", "getUuid");
  private static final List<String> OWNER_METHODS =
      List.of(
          "getOwner",
          "owner",
          "getOwnerUuid",
          "getOwnerUUID",
          "getOwnerId",
          "getOwnerName",
          "permissions",
          "getPermissions");

  private final String providerId;
  private final Object plugin;
  private final List<String> lookupMethods;
  private final List<String> rootMethods;
  private final List<String> staticApiClasses;

  protected ReflectiveProtectionProvider(
      String providerId,
      Object plugin,
      List<String> lookupMethods,
      List<String> rootMethods,
      List<String> staticApiClasses) {
    this.providerId = providerId;
    this.plugin = plugin;
    this.lookupMethods = List.copyOf(lookupMethods);
    this.rootMethods = List.copyOf(rootMethods);
    this.staticApiClasses = List.copyOf(staticApiClasses);
  }

  @Override
  public final String id() {
    return providerId;
  }

  @Override
  public final Optional<UUID> resolveOwner(Block block) throws ProtectionProviderException {
    if (block == null) {
      throw new ProtectionProviderException(providerId + " cannot resolve a null block");
    }
    List<Object> roots = new ArrayList<>();
    if (plugin != null) {
      roots.add(plugin);
      for (String rootMethod : rootMethods) {
        Object root = invokeNoArg(plugin, rootMethod);
        if (root != null) {
          roots.add(root);
        }
      }
    }

    for (Object root : roots) {
      InvocationResult result = invokeLookup(root, block);
      if (result.found()) {
        return ownerFromRecord(result.value());
      }
    }
    for (String apiClassName : staticApiClasses) {
      Optional<UUID> owner = resolveStaticApi(apiClassName, block);
      if (owner.isPresent()) {
        return owner;
      }
    }
    return Optional.empty();
  }

  private InvocationResult invokeLookup(Object target, Block block)
      throws ProtectionProviderException {
    Method matchingMethod = null;
    Object[] matchingArguments = UNSUPPORTED_ARGUMENTS;
    for (Method method : target.getClass().getMethods()) {
      if (matchingMethod == null
          && lookupMethods.contains(method.getName())
          && !Modifier.isStatic(method.getModifiers())) {
        Object[] arguments = argumentsFor(method.getParameterTypes(), block);
        if (arguments.length > 0) {
          matchingMethod = method;
          matchingArguments = arguments;
        }
      }
    }
    if (matchingMethod == null) {
      return new InvocationResult(false, null);
    }
    return new InvocationResult(true, invoke(matchingMethod, target, matchingArguments));
  }

  private Optional<UUID> resolveStaticApi(String className, Block block)
      throws ProtectionProviderException {
    final Class<?> apiClass;
    try {
      apiClass = Class.forName(className, false, pluginClassLoader());
    } catch (ClassNotFoundException | LinkageError ignored) {
      return Optional.empty();
    }
    Object service = Bukkit.getServicesManager().load(apiClass);
    if (service != null) {
      InvocationResult serviceResult = invokeLookup(service, block);
      if (serviceResult.found()) {
        return ownerFromRecord(serviceResult.value());
      }
    }
    Optional<UUID> staticRootOwner = resolveStaticRoot(apiClass, block);
    if (staticRootOwner.isPresent()) {
      return staticRootOwner;
    }
    Method matchingMethod = null;
    Object[] matchingArguments = UNSUPPORTED_ARGUMENTS;
    for (Method method : apiClass.getMethods()) {
      if (matchingMethod == null
          && lookupMethods.contains(method.getName())
          && Modifier.isStatic(method.getModifiers())) {
        Object[] arguments = argumentsFor(method.getParameterTypes(), block);
        if (arguments.length > 0) {
          matchingMethod = method;
          matchingArguments = arguments;
        }
      }
    }
    if (matchingMethod == null) {
      return Optional.empty();
    }
    Object result = invoke(matchingMethod, null, matchingArguments);
    return ownerFromRecord(result);
  }

  private Optional<UUID> resolveStaticRoot(Class<?> apiClass, Block block)
      throws ProtectionProviderException {
    Method matchingMethod = null;
    for (Method method : apiClass.getMethods()) {
      if (matchingMethod == null
          && rootMethods.contains(method.getName())
          && Modifier.isStatic(method.getModifiers())
          && method.getParameterCount() == 0) {
        matchingMethod = method;
      }
    }
    if (matchingMethod == null) {
      return Optional.empty();
    }
    Object root = invoke(matchingMethod, null);
    if (root == null) {
      return Optional.empty();
    }
    InvocationResult result = invokeLookup(root, block);
    return result.found() ? ownerFromRecord(result.value()) : Optional.empty();
  }

  @SuppressWarnings("PMD.UseProperClassLoader")
  private ClassLoader pluginClassLoader() {
    return plugin == null
        ? Thread.currentThread().getContextClassLoader()
        : plugin.getClass().getClassLoader();
  }

  private Object invokeNoArg(Object target, String methodName) throws ProtectionProviderException {
    Method matchingMethod = null;
    for (Method method : target.getClass().getMethods()) {
      if (matchingMethod == null
          && method.getName().equals(methodName)
          && method.getParameterCount() == 0) {
        matchingMethod = method;
      }
    }
    return matchingMethod == null ? null : invoke(matchingMethod, target);
  }

  private Object invoke(Method method, Object target, Object... arguments)
      throws ProtectionProviderException {
    try {
      if (!method.trySetAccessible()) {
        throw new ProtectionProviderException(
            providerId
                + " cannot access "
                + method.getDeclaringClass().getName()
                + "."
                + method.getName());
      }
      return method.invoke(target, arguments);
    } catch (IllegalAccessException exception) {
      throw new ProtectionProviderException(providerId + " rejected reflective access", exception);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause() == null ? exception : exception.getCause();
      throw new ProtectionProviderException(providerId + " lookup failed", cause);
    }
  }

  private Object[] argumentsFor(Class<?>[] parameterTypes, Block block) {
    if (parameterTypes.length == BLOCK_PARAMETER_COUNT) {
      if (parameterTypes[0].isInstance(block)) {
        return new Object[] {block};
      }
      if (parameterTypes[0].isAssignableFrom(Location.class)) {
        return new Object[] {block.getLocation()};
      }
    }
    if (parameterTypes.length == COORDINATE_PARAMETER_COUNT
        && parameterTypes[0].isAssignableFrom(World.class)
        && isInt(parameterTypes[1])
        && isInt(parameterTypes[2])
        && isInt(parameterTypes[3])) {
      Location location = block.getLocation();
      return new Object[] {
        block.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ()
      };
    }
    return UNSUPPORTED_ARGUMENTS;
  }

  private static boolean isInt(Class<?> type) {
    return type == int.class || type == Integer.class;
  }

  private Optional<UUID> ownerFromRecord(Object record) throws ProtectionProviderException {
    if (record == null) {
      return Optional.empty();
    }
    if (record instanceof Optional<?> optional) {
      return optional.isEmpty() ? Optional.empty() : ownerFromRecord(optional.get());
    }
    Object hasPdcData = invokeNoArg(record, "hasPdcData");
    if (Boolean.FALSE.equals(hasPdcData)) {
      return Optional.empty();
    }
    if (Boolean.TRUE.equals(hasPdcData) && Boolean.FALSE.equals(invokeNoArg(record, "isLocked"))) {
      return Optional.empty();
    }
    if (record instanceof UUID uuid) {
      return Optional.of(uuid);
    }
    if (record instanceof Player player) {
      return ownerFromUuid(player.getUniqueId());
    }
    if (record instanceof OfflinePlayer offlinePlayer) {
      return ownerFromUuid(offlinePlayer.getUniqueId());
    }
    if (record instanceof Map<?, ?> values) {
      return ownerFromMap(values);
    }
    if (record instanceof String || record.getClass().isEnum()) {
      return normalizeOwner(record);
    }
    Method matchingOwnerMethod = null;
    for (String ownerMethod : OWNER_METHODS) {
      for (Method method : record.getClass().getMethods()) {
        if (matchingOwnerMethod == null
            && method.getName().equals(ownerMethod)
            && method.getParameterCount() == 0) {
          matchingOwnerMethod = method;
        }
      }
    }
    if (matchingOwnerMethod != null) {
      return normalizeOwner(invoke(matchingOwnerMethod, record));
    }
    throw unusableOwner();
  }

  private Optional<UUID> normalizeOwner(Object value) throws ProtectionProviderException {
    if (value instanceof UUID uuid) {
      return ownerFromUuid(uuid);
    }
    if (value instanceof Player player) {
      return ownerFromUuid(player.getUniqueId());
    }
    if (value instanceof OfflinePlayer offlinePlayer) {
      return ownerFromUuid(offlinePlayer.getUniqueId());
    }
    if (value instanceof Optional<?> optional) {
      if (optional.isEmpty()) {
        throw unusableOwner();
      }
      return normalizeOwner(optional.get());
    }
    if (value instanceof Map<?, ?> values) {
      return ownerFromMap(values);
    }
    if (value instanceof String raw) {
      String owner = raw.trim();
      if (owner.isEmpty()) {
        throw unusableOwner();
      }
      try {
        return Optional.of(UUID.fromString(owner));
      } catch (IllegalArgumentException ignored) {
        return resolveKnownPlayerName(owner);
      }
    }
    if (value == null) {
      throw unusableOwner();
    }
    Method matchingIdMethod = null;
    for (String methodName : OWNER_ID_METHODS) {
      for (Method method : value.getClass().getMethods()) {
        if (matchingIdMethod == null
            && method.getName().equals(methodName)
            && method.getParameterCount() == 0) {
          matchingIdMethod = method;
        }
      }
    }
    if (matchingIdMethod != null) {
      return normalizeOwner(invoke(matchingIdMethod, value));
    }
    throw unusableOwner();
  }

  private Optional<UUID> ownerFromMap(Map<?, ?> values) throws ProtectionProviderException {
    Optional<String> explicitOwnerKey = Optional.empty();
    for (String key : OWNER_KEYS) {
      if (explicitOwnerKey.isEmpty() && values.containsKey(key)) {
        explicitOwnerKey = Optional.of(key);
      }
    }
    if (explicitOwnerKey.isPresent()) {
      return normalizeOwner(values.get(explicitOwnerKey.get()));
    }
    Optional<Map.Entry<?, ?>> permissionOwner = Optional.empty();
    for (Map.Entry<?, ?> entry : values.entrySet()) {
      if (permissionOwner.isEmpty() && isOwnerPermission(entry.getValue())) {
        permissionOwner = Optional.of(entry);
      }
    }
    if (permissionOwner.isPresent()) {
      return normalizeOwner(permissionOwner.get().getKey());
    }
    throw unusableOwner();
  }

  private static boolean isOwnerPermission(Object value) {
    if (value instanceof Enum<?> enumValue) {
      return "OWNER".equalsIgnoreCase(enumValue.name());
    }
    if (value instanceof String stringValue) {
      return "owner".equalsIgnoreCase(stringValue.trim())
          || "xx".equalsIgnoreCase(stringValue.trim());
    }
    return false;
  }

  private Optional<UUID> resolveKnownPlayerName(String name) throws ProtectionProviderException {
    OfflinePlayer player = cachedOfflinePlayer(name);
    if (player == null || player.getUniqueId() == null) {
      throw unusableOwner();
    }
    if (!player.isOnline() && !player.hasPlayedBefore()) {
      throw unusableOwner();
    }
    return Optional.of(player.getUniqueId());
  }

  private OfflinePlayer cachedOfflinePlayer(String name) throws ProtectionProviderException {
    try {
      Method method = Bukkit.class.getMethod("getOfflinePlayerIfCached", String.class);
      Object result = method.invoke(null, name);
      return result instanceof OfflinePlayer player ? player : null;
    } catch (NoSuchMethodException ignored) {
      return Bukkit.getOfflinePlayer(name);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new ProtectionProviderException(
          providerId + " could not resolve owner name", exception);
    }
  }

  private Optional<UUID> ownerFromUuid(UUID uuid) throws ProtectionProviderException {
    if (uuid == null) {
      throw unusableOwner();
    }
    return Optional.of(uuid);
  }

  private ProtectionProviderException unusableOwner() {
    return new ProtectionProviderException(
        providerId + " returned a protected record without a usable owner");
  }

  private record InvocationResult(boolean found, Object value) {}
}
