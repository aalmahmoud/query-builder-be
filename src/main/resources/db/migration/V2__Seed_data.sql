-- ============================================================================
-- V2: Seed data for permissions, roles, and default users
-- ============================================================================

-- Permissions: CRUD operations for each resource
INSERT INTO permissions (name, resource, action, description, is_active, created_date) VALUES
    ('user:create', 'user', 'create', 'Create new users', true, NOW()),
    ('user:read',   'user', 'read',   'View user details', true, NOW()),
    ('user:update', 'user', 'update', 'Update user information', true, NOW()),
    ('user:delete', 'user', 'delete', 'Delete users', true, NOW()),
    ('role:create', 'role', 'create', 'Create new roles', true, NOW()),
    ('role:read',   'role', 'read',   'View role details', true, NOW()),
    ('role:update', 'role', 'update', 'Update role information', true, NOW()),
    ('role:delete', 'role', 'delete', 'Delete roles', true, NOW()),
    ('permission:create', 'permission', 'create', 'Create new permissions', true, NOW()),
    ('permission:read',   'permission', 'read',   'View permission details', true, NOW()),
    ('permission:update', 'permission', 'update', 'Update permission information', true, NOW()),
    ('permission:delete', 'permission', 'delete', 'Delete permissions', true, NOW());

-- Roles
INSERT INTO roles (name, description, is_active, created_date) VALUES
    ('ADMIN',   'Full system access with all permissions', true, NOW()),
    ('MANAGER', 'Manage users and roles, read permissions', true, NOW()),
    ('USER',    'Basic user access with read-only capabilities', true, NOW());

-- ADMIN role: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ADMIN';

-- MANAGER role: all user and role permissions + permission:read
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MANAGER'
  AND (p.resource IN ('user', 'role') OR p.name = 'permission:read');

-- USER role: read-only on users, roles, permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'USER'
  AND p.action = 'read';

-- Default users (passwords hashed with BCrypt, cost factor 10)
-- admin@system.com    / admin123
-- manager@system.com  / manager123
-- user@system.com     / user123
INSERT INTO users (first_name, last_name, email, mobile_number, national_id, password, role_id, is_active, created_date) VALUES
    ('System', 'Admin',   'admin@system.com',   '+966500000001', '1000000001',
     '$2a$10$/THKMQ0Qa3m.v3erUWTtoukCUpOyZPAUmcDLUHmGzlsi5yX8UUI5q',
     (SELECT id FROM roles WHERE name = 'ADMIN'), true, NOW()),
    ('System', 'Manager', 'manager@system.com', '+966500000002', '1000000002',
     '$2a$10$ciIKE3gBMzEwrKszwtmknOWRg5oggfawXQUb4SG9bUL04FL7TBXAe',
     (SELECT id FROM roles WHERE name = 'MANAGER'), true, NOW()),
    ('System', 'User',    'user@system.com',    '+966500000003', '1000000003',
     '$2a$10$LXBAP2WhdbMZczcw7rtC5eY662KdOjCA0pi5pkJDiuZBTbFD689/W',
     (SELECT id FROM roles WHERE name = 'USER'), true, NOW());
