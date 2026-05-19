-- Phase 4 fix 4.13: every list endpoint defaults to ORDER BY created_date DESC. Without
-- a supporting index, paginated listings degrade to a sort over the whole table once row
-- counts grow. DESC index matches the access pattern.

CREATE INDEX IF NOT EXISTS idx_users_created_date       ON users(created_date DESC);
CREATE INDEX IF NOT EXISTS idx_roles_created_date       ON roles(created_date DESC);
CREATE INDEX IF NOT EXISTS idx_permissions_created_date ON permissions(created_date DESC);
