CREATE TABLE IF NOT EXISTS pending_claims (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    item_blob BLOB NOT NULL,
    source TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    state TEXT NOT NULL DEFAULT 'PENDING',
    delivery_token TEXT,
    claimed_at INTEGER
);

CREATE INDEX IF NOT EXISTS pending_claims_player_state_id
    ON pending_claims (player_uuid, state, id);
