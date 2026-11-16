ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
CREATE INDEX users_active_last_login_idx ON users (active, last_login_at DESC);
