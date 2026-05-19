-- Phase 4 fix 4.14: previously the FK had no ON DELETE clause, so deleting a role still
-- referenced by users surfaced as a 500. Switch to ON DELETE SET NULL — a deleted role
-- detaches its users (they keep working, no role until reassigned). The application's
-- DataIntegrityViolationException handler (V3 era) still produces a 409 for any other
-- constraint violation if business rules require richer behaviour.

ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_user_role;

ALTER TABLE users
    ADD CONSTRAINT fk_user_role
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE SET NULL;
