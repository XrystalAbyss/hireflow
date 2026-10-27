-- ==============================================================================
-- Flyway Migration V3: Add manual closure flag to recruitment drives
-- Milestone: Stage 3F.1 — Recruitment Drive Closure Correction
-- ==============================================================================

ALTER TABLE recruitment_drives
    ADD COLUMN IF NOT EXISTS is_closed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_recruitment_drives_closed
    ON recruitment_drives(is_closed);
