-- ==============================================================================
-- HireFlow Database Architecture - User Accounts Schema Migration
-- Flyway Version: V2
-- Description: Persistent authentication records, role constraints, and student
--              identity binding for Stage 3C Web Portal security.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. User Accounts Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_accounts (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    student_id VARCHAR(32),
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITHOUT TIME ZONE,

    -- Normalized, unique username constraint
    CONSTRAINT uq_user_accounts_username UNIQUE (username),

    -- Foreign key to students table
    CONSTRAINT fk_user_accounts_student FOREIGN KEY (student_id)
        REFERENCES students(id) ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Role enumeration constraint
    CONSTRAINT chk_user_accounts_role CHECK (
        role IN ('STUDENT', 'PLACEMENT_OFFICER', 'ADMINISTRATOR')
    ),

    -- Strict student-identity binding:
    -- STUDENT role must have a non-null student_id; non-STUDENT roles must NOT have a student_id.
    CONSTRAINT chk_user_accounts_student_binding CHECK (
        (role = 'STUDENT' AND student_id IS NOT NULL) OR
        (role != 'STUDENT' AND student_id IS NULL)
    ),

    -- Normalized lowercase username constraint (min length 3, max length 50)
    CONSTRAINT chk_user_accounts_username_normalized CHECK (
        username = LOWER(TRIM(username)) AND LENGTH(username) >= 3
    ),

    -- Non-empty password hash constraint
    CONSTRAINT chk_user_accounts_password_hash CHECK (
        LENGTH(password_hash) >= 20
    ),

    -- Failed login attempts cannot be negative
    CONSTRAINT chk_user_accounts_failed_attempts CHECK (
        failed_login_attempts >= 0
    )
);

-- Indices for rapid lookup by username (covered by UNIQUE), role, and student binding
CREATE INDEX IF NOT EXISTS idx_user_accounts_role ON user_accounts(role);
CREATE INDEX IF NOT EXISTS idx_user_accounts_student_id ON user_accounts(student_id);
