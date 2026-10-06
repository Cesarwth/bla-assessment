-- The user who first synced a record; only the owner (or an admin) may edit it.
-- Nullable on purpose: rows created before this migration (or seeded) have no owner.
ALTER TABLE pokemon ADD COLUMN owner_id BIGINT;

ALTER TABLE pokemon
    ADD CONSTRAINT fk_pokemon_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_pokemon_owner ON pokemon (owner_id);
