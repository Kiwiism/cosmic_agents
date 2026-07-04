--liquibase formatted sql
--changeset cosmic-database-console:001

CREATE TABLE console_users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(96) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_console_users_username (username)
);

CREATE TABLE console_roles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(48) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uq_console_roles_name (name)
);

CREATE TABLE console_permissions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(96) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uq_console_permissions_code (code)
);

CREATE TABLE console_user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_console_user_roles_user FOREIGN KEY (user_id) REFERENCES console_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_console_user_roles_role FOREIGN KEY (role_id) REFERENCES console_roles(id) ON DELETE CASCADE
);

CREATE TABLE console_role_permissions (
    role_id BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_console_role_permissions_role FOREIGN KEY (role_id) REFERENCES console_roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_console_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES console_permissions(id) ON DELETE CASCADE
);

CREATE TABLE console_audit_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NULL,
    action VARCHAR(96) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_key VARCHAR(128) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    outcome VARCHAR(32) NOT NULL,
    remote_address VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_console_audit_entity (entity_type, entity_key),
    KEY ix_console_audit_created (created_at),
    CONSTRAINT fk_console_audit_actor FOREIGN KEY (actor_user_id) REFERENCES console_users(id) ON DELETE SET NULL
);

CREATE TABLE console_change_sets (
    id CHAR(36) NOT NULL,
    title VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_console_change_sets_user FOREIGN KEY (created_by) REFERENCES console_users(id)
);

CREATE TABLE console_queued_operations (
    id CHAR(36) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    safety_class VARCHAR(32) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    KEY ix_console_queue_status (status, created_at),
    CONSTRAINT fk_console_queue_user FOREIGN KEY (created_by) REFERENCES console_users(id) ON DELETE SET NULL
);

CREATE TABLE console_idempotency_keys (
    operation_key VARCHAR(96) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json JSON NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (operation_key)
);

INSERT INTO console_roles (name, description) VALUES
('OWNER', 'Full Console ownership and staff management'),
('ADMINISTRATOR', 'Server settings and diagnostics'),
('GAME_MASTER', 'Live player and event management'),
('CONTENT_EDITOR', 'Drops, shops, and catalog content'),
('MODERATOR', 'Reports, sanctions, and sessions'),
('SUPPORT', 'Player support and safe character assistance'),
('VIEWER', 'Read-only access');

INSERT INTO console_permissions (code, description) VALUES
('dashboard.read', 'View dashboard'),
('catalog.read', 'Browse WZ catalog'),
('drops.read', 'Browse drop data'),
('drops.write', 'Modify drop data'),
('shops.read', 'Browse shops'),
('shops.write', 'Modify shops'),
('accounts.read', 'Browse accounts'),
('accounts.write', 'Modify accounts'),
('characters.read', 'Browse characters'),
('characters.write', 'Modify offline characters'),
('inventory.read', 'Browse inventories'),
('inventory.write', 'Modify offline inventories'),
('server.read', 'View live server state'),
('server.operate', 'Perform live server operations'),
('audit.read', 'View audit history'),
('staff.manage', 'Manage Console staff');

INSERT INTO console_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM console_roles r CROSS JOIN console_permissions p WHERE r.name = 'OWNER';

INSERT INTO console_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM console_roles r JOIN console_permissions p
  ON p.code IN ('dashboard.read', 'catalog.read', 'drops.read', 'shops.read', 'accounts.read',
                'characters.read', 'inventory.read', 'server.read', 'audit.read', 'server.operate')
WHERE r.name = 'ADMINISTRATOR';

INSERT INTO console_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM console_roles r JOIN console_permissions p
  ON p.code IN ('dashboard.read', 'catalog.read', 'drops.read', 'shops.read', 'accounts.read',
                'characters.read', 'characters.write', 'inventory.read', 'server.read', 'server.operate')
WHERE r.name = 'GAME_MASTER';

INSERT INTO console_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM console_roles r JOIN console_permissions p
  ON p.code IN ('dashboard.read', 'catalog.read', 'drops.read', 'drops.write', 'shops.read',
                'shops.write', 'server.read', 'audit.read')
WHERE r.name = 'CONTENT_EDITOR';

INSERT INTO console_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM console_roles r JOIN console_permissions p
  ON p.code IN ('dashboard.read', 'catalog.read', 'accounts.read', 'accounts.write',
                'characters.read', 'inventory.read', 'server.read', 'audit.read')
WHERE r.name = 'MODERATOR';

INSERT INTO console_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM console_roles r JOIN console_permissions p
  ON p.code IN ('dashboard.read', 'catalog.read', 'accounts.read', 'characters.read',
                'characters.write', 'inventory.read', 'inventory.write', 'server.read', 'audit.read')
WHERE r.name = 'SUPPORT';

INSERT INTO console_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM console_roles r CROSS JOIN console_permissions p
WHERE r.name = 'VIEWER' AND p.code LIKE '%.read';
