-- Create roles table first (users table depends on it)
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_date TIMESTAMP NOT NULL,
    last_modified_date TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100)
);

-- Create permissions table
CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    resource VARCHAR(200),
    action VARCHAR(100),
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_date TIMESTAMP NOT NULL,
    last_modified_date TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100)
);

-- Create role_permissions join table (depends on roles and permissions)
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- Create users table last (depends on roles)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(200) NOT NULL UNIQUE,
    mobile_number VARCHAR(20),
    national_id VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255),
    role_id BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_date TIMESTAMP NOT NULL,
    last_modified_date TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100),
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_national_id ON users(national_id);
CREATE INDEX IF NOT EXISTS idx_users_role_id ON users(role_id);
CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);
CREATE INDEX IF NOT EXISTS idx_permissions_name ON permissions(name);
CREATE INDEX IF NOT EXISTS idx_permissions_resource ON permissions(resource);
