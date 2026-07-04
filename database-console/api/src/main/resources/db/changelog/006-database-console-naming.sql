--liquibase formatted sql
--changeset cosmic-database-console:006

UPDATE console_roles
SET description = 'Full Database Console ownership and user management'
WHERE name = 'OWNER';

UPDATE console_permissions
SET code = 'database.manage',
    description = 'Manage Database Console users'
WHERE code = 'staff.manage';
