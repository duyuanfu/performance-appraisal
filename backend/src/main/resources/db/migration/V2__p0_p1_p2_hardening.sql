-- P0/P1/P2 database hardening.
-- This migration is intentionally explicit instead of relying on startup DDL.
-- It is safe for the baseline schema created by V1.

-- ---------------------------------------------------------------------------
-- 1. Normalize/clean orphan data before adding foreign keys.
-- ---------------------------------------------------------------------------
DELETE FROM sys_user_role
WHERE user_id NOT IN (SELECT id FROM sys_user)
   OR role_id NOT IN (SELECT id FROM sys_role);

UPDATE sys_user SET supervisor_id = NULL
WHERE supervisor_id IS NOT NULL AND supervisor_id NOT IN (SELECT id FROM sys_user);
UPDATE sys_user SET dept_head_id = NULL
WHERE dept_head_id IS NOT NULL AND dept_head_id NOT IN (SELECT id FROM sys_user);
UPDATE sys_user SET position_category = 'EMPLOYEE'
WHERE position_category IS NULL OR position_category = '';

UPDATE appraisal_batch SET created_by = NULL
WHERE created_by IS NOT NULL AND created_by NOT IN (SELECT id FROM sys_user);
UPDATE appraisal_batch SET status = 'DRAFT'
WHERE status IS NULL OR status NOT IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED');
UPDATE appraisal_batch SET batch_type = 'MONTHLY'
WHERE batch_type IS NULL OR batch_type NOT IN ('MONTHLY', 'TARGET_PROJECT', 'SPECIAL_INNOVATION', 'ANNUAL_PROJECT');

DELETE FROM appraisal_metric WHERE batch_id NOT IN (SELECT id FROM appraisal_batch);
DELETE FROM monthly_role_weight WHERE batch_id NOT IN (SELECT id FROM appraisal_batch);
DELETE FROM monthly_evaluator
WHERE batch_id NOT IN (SELECT id FROM appraisal_batch)
   OR leader_user_id NOT IN (SELECT id FROM sys_user);
DELETE FROM monthly_examinee
WHERE batch_id NOT IN (SELECT id FROM appraisal_batch)
   OR examinee_user_id NOT IN (SELECT id FROM sys_user);
DELETE FROM monthly_score_detail
WHERE record_id NOT IN (SELECT id FROM monthly_score_record)
   OR metric_id NOT IN (SELECT id FROM appraisal_metric)
   OR record_id IN (
       SELECT id FROM monthly_score_record
       WHERE batch_id NOT IN (SELECT id FROM appraisal_batch)
          OR leader_user_id NOT IN (SELECT id FROM sys_user)
          OR examinee_user_id NOT IN (SELECT id FROM sys_user)
   );
DELETE FROM monthly_score_record
WHERE batch_id NOT IN (SELECT id FROM appraisal_batch)
   OR leader_user_id NOT IN (SELECT id FROM sys_user)
   OR examinee_user_id NOT IN (SELECT id FROM sys_user);

DELETE FROM project_role_weight WHERE batch_id NOT IN (SELECT id FROM appraisal_batch);
DELETE FROM project_expert
WHERE batch_id NOT IN (SELECT id FROM appraisal_batch)
   OR expert_user_id NOT IN (SELECT id FROM sys_user);
DELETE FROM project_score_detail
WHERE record_id NOT IN (SELECT id FROM project_score_record)
   OR metric_id NOT IN (SELECT id FROM appraisal_metric)
   OR record_id IN (
       SELECT id FROM project_score_record
       WHERE batch_id NOT IN (SELECT id FROM appraisal_batch)
          OR expert_user_id NOT IN (SELECT id FROM sys_user)
          OR project_id NOT IN (SELECT id FROM project_item)
          OR project_id IN (
              SELECT id FROM project_item
              WHERE batch_id NOT IN (SELECT id FROM appraisal_batch)
          )
   );
DELETE FROM project_score_record
WHERE batch_id NOT IN (SELECT id FROM appraisal_batch)
   OR expert_user_id NOT IN (SELECT id FROM sys_user)
   OR project_id NOT IN (SELECT id FROM project_item)
   OR project_id IN (
       SELECT id FROM project_item
       WHERE batch_id NOT IN (SELECT id FROM appraisal_batch)
   );
DELETE FROM project_item WHERE batch_id NOT IN (SELECT id FROM appraisal_batch);
UPDATE project_item SET supervisor_id = NULL
WHERE supervisor_id IS NOT NULL AND supervisor_id NOT IN (SELECT id FROM sys_user);

-- Normalize enum values that were historically written with a different name.
UPDATE monthly_score_record SET evaluator_role_type = 'SUPERVISOR_LEADER'
WHERE evaluator_role_type = 'SUPERVISING_LEADER';
UPDATE project_score_record SET evaluator_role_type = 'SUPERVISOR_LEADER'
WHERE evaluator_role_type = 'SUPERVISING_LEADER';
UPDATE sys_user SET position_category = 'EMPLOYEE'
WHERE position_category NOT IN ('PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'EMPLOYEE');
UPDATE monthly_score_record SET evaluator_role_type = ''
WHERE evaluator_role_type IS NULL
   OR evaluator_role_type NOT IN ('', 'PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'PEER_HEAD', 'PEER_DEPUTY', 'DEPT_HEAD_PEER', 'DEPT_DEPUTY_PEER');
UPDATE project_score_record SET evaluator_role_type = ''
WHERE evaluator_role_type IS NULL
   OR evaluator_role_type NOT IN ('', 'PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'PEER_HEAD', 'PEER_DEPUTY', 'DEPT_HEAD_PEER', 'DEPT_DEPUTY_PEER');
UPDATE monthly_score_record SET status = 'DRAFT' WHERE status NOT IN ('DRAFT', 'SUBMITTED');
UPDATE project_score_record SET status = 'DRAFT' WHERE status NOT IN ('DRAFT', 'SUBMITTED');
UPDATE monthly_score_record SET total_score = 0 WHERE total_score < 0;
UPDATE project_score_record SET total_score = 0 WHERE total_score < 0;
UPDATE monthly_score_detail SET score = 0 WHERE score < 0;
UPDATE project_score_detail SET score = 0 WHERE score < 0;
UPDATE project_item SET category = '' WHERE category IS NULL OR category NOT IN ('', 'BUSINESS', 'MANAGEMENT');
UPDATE appraisal_metric SET max_score = 0 WHERE max_score < 0;
UPDATE appraisal_metric SET weight = 0 WHERE weight < 0;
UPDATE appraisal_metric SET weight = 100 WHERE weight > 100;
UPDATE monthly_role_weight SET role_category = 'OTHER_LEADER'
WHERE role_category NOT IN ('PRIMARY_LEADER', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'DEPT_HEAD', 'PEER_HEAD', 'PEER_DEPUTY');
UPDATE project_role_weight SET role_category = 'OTHER_LEADER'
WHERE role_category NOT IN ('CENTER_LEADER_TOTAL', 'PRIMARY_LEADER', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'DEPT_HEAD', 'EXPERT');
UPDATE monthly_role_weight SET weight = 0 WHERE weight < 0;
UPDATE monthly_role_weight SET weight = 100 WHERE weight > 100;
UPDATE project_role_weight SET weight = 0 WHERE weight < 0;
UPDATE project_role_weight SET weight = 100 WHERE weight > 100;
UPDATE monthly_evaluator SET weight = 0 WHERE weight < 0;
UPDATE monthly_evaluator SET weight = 100 WHERE weight > 100;
UPDATE project_expert SET weight = 0 WHERE weight < 0;
UPDATE project_expert SET weight = 100 WHERE weight > 100;

UPDATE sys_user SET position_category = 'EMPLOYEE' WHERE position_category IS NULL;
UPDATE monthly_score_record SET evaluator_role_type = '' WHERE evaluator_role_type IS NULL;
UPDATE project_score_record SET evaluator_role_type = '' WHERE evaluator_role_type IS NULL;
UPDATE monthly_score_record SET total_score = 0 WHERE total_score IS NULL;
UPDATE project_score_record SET total_score = 0 WHERE total_score IS NULL;
UPDATE project_item SET category = '' WHERE category IS NULL;

ALTER TABLE sys_user
    MODIFY COLUMN position_category VARCHAR(64) NOT NULL DEFAULT 'EMPLOYEE';
ALTER TABLE monthly_score_record
    MODIFY COLUMN evaluator_role_type VARCHAR(32) NOT NULL DEFAULT '',
    MODIFY COLUMN total_score DECIMAL(10,4) NOT NULL DEFAULT 0.0000;
ALTER TABLE project_score_record
    MODIFY COLUMN evaluator_role_type VARCHAR(32) NOT NULL DEFAULT '',
    MODIFY COLUMN total_score DECIMAL(10,4) NOT NULL DEFAULT 0.0000;
ALTER TABLE project_item
    MODIFY COLUMN category VARCHAR(32) NOT NULL DEFAULT '';

-- ---------------------------------------------------------------------------
-- 2. Split the overloaded appraisal_batch.category field.
-- ---------------------------------------------------------------------------
ALTER TABLE appraisal_batch
    ADD COLUMN monthly_category VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'Monthly only: DEPT_HEAD / DEPT_DEPUTY' AFTER batch_type;
ALTER TABLE appraisal_batch
    ADD COLUMN project_category VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'Project only: BUSINESS / MANAGEMENT / ALL' AFTER monthly_category;
ALTER TABLE appraisal_batch
    ADD COLUMN updated_by BIGINT NULL COMMENT 'Last operator user id' AFTER created_by;
ALTER TABLE appraisal_batch
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version' AFTER updated_at;
ALTER TABLE appraisal_batch
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Soft delete flag' AFTER version;

UPDATE appraisal_batch
SET monthly_category = CASE
        WHEN batch_type = 'MONTHLY' AND category IN ('DEPT_HEAD', 'DEPT_DEPUTY') THEN category
        ELSE ''
    END,
    project_category = CASE
        WHEN batch_type IN ('TARGET_PROJECT', 'SPECIAL_INNOVATION', 'ANNUAL_PROJECT')
             AND category IN ('BUSINESS', 'MANAGEMENT', 'ALL') THEN category
        ELSE ''
    END;

ALTER TABLE appraisal_batch DROP COLUMN category;

-- ---------------------------------------------------------------------------
-- 3. Add audit columns and optimistic-lock/soft-delete columns.
-- ---------------------------------------------------------------------------
ALTER TABLE sys_role
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE sys_user_role
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE appraisal_metric
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE monthly_role_weight
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE monthly_evaluator
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE monthly_examinee
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE monthly_score_record
    ADD COLUMN created_by BIGINT NULL AFTER submitted_at;
ALTER TABLE monthly_score_record
    ADD COLUMN updated_by BIGINT NULL AFTER created_by;
ALTER TABLE monthly_score_record
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER updated_by;
ALTER TABLE monthly_score_record
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
ALTER TABLE monthly_score_record
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_at;

ALTER TABLE monthly_score_detail
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE project_item
    ADD COLUMN owner_user_id BIGINT NULL AFTER project_name;
ALTER TABLE project_item
    ADD COLUMN created_by BIGINT NULL AFTER sort_order;
ALTER TABLE project_item
    ADD COLUMN updated_by BIGINT NULL AFTER created_by;
ALTER TABLE project_item
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
ALTER TABLE project_item
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_at;
ALTER TABLE project_item
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 AFTER version;

ALTER TABLE project_item
    DROP COLUMN score_coefficient,
    DROP COLUMN bonus_amount,
    DROP COLUMN rank_num;

ALTER TABLE project_role_weight
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE project_expert
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE project_score_record
    ADD COLUMN created_by BIGINT NULL AFTER submitted_at;
ALTER TABLE project_score_record
    ADD COLUMN updated_by BIGINT NULL AFTER created_by;
ALTER TABLE project_score_record
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER updated_by;
ALTER TABLE project_score_record
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
ALTER TABLE project_score_record
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_at;

ALTER TABLE project_score_detail
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------------
-- 5. Remove duplicate project codes / metric names before adding unique keys.
-- ---------------------------------------------------------------------------
UPDATE project_item p
JOIN (
    SELECT batch_id, project_code, MIN(id) AS keep_id
    FROM project_item
    GROUP BY batch_id, project_code
    HAVING COUNT(*) > 1
) d ON p.batch_id = d.batch_id AND p.project_code = d.project_code AND p.id <> d.keep_id
SET p.project_code = CONCAT(LEFT(p.project_code, 40), '-', p.id);

UPDATE appraisal_metric m
JOIN (
    SELECT batch_id, metric_name, MIN(id) AS keep_id
    FROM appraisal_metric
    GROUP BY batch_id, metric_name
    HAVING COUNT(*) > 1
) d ON m.batch_id = d.batch_id AND m.metric_name = d.metric_name AND m.id <> d.keep_id
SET m.metric_name = CONCAT(LEFT(m.metric_name, 40), '-', m.id);

-- ---------------------------------------------------------------------------
-- 6. Indexes.
-- ---------------------------------------------------------------------------
ALTER TABLE sys_user
    ADD INDEX idx_sys_user_department (department),
    ADD INDEX idx_sys_user_position_category (position_category),
    ADD INDEX idx_sys_user_supervisor (supervisor_id),
    ADD INDEX idx_sys_user_dept_head (dept_head_id),
    ADD INDEX idx_sys_user_status (status);

ALTER TABLE sys_user_role
    ADD INDEX idx_user_role_role (role_id);

ALTER TABLE appraisal_batch
    ADD INDEX idx_batch_type_status (batch_type, status),
    ADD INDEX idx_batch_period (appraisal_period),
    ADD INDEX idx_batch_monthly_category (monthly_category),
    ADD INDEX idx_batch_project_category (project_category),
    ADD INDEX idx_batch_created_by (created_by);

ALTER TABLE appraisal_metric
    ADD UNIQUE KEY uk_batch_metric_name (batch_id, metric_name),
    ADD INDEX idx_metric_batch_sort (batch_id, sort_order);

ALTER TABLE monthly_role_weight
    ADD INDEX idx_monthly_role_category (role_category);

ALTER TABLE monthly_evaluator
    ADD INDEX idx_monthly_evaluator_user (leader_user_id);

ALTER TABLE monthly_examinee
    ADD INDEX idx_monthly_examinee_user (examinee_user_id);

ALTER TABLE monthly_score_record
    ADD INDEX idx_monthly_record_examinee (batch_id, examinee_user_id),
    ADD INDEX idx_monthly_record_evaluator (batch_id, leader_user_id),
    ADD INDEX idx_monthly_record_status (batch_id, status);

ALTER TABLE monthly_score_detail
    ADD INDEX idx_monthly_detail_metric (metric_id);

ALTER TABLE project_item
    ADD UNIQUE KEY uk_batch_project_code (batch_id, project_code),
    ADD INDEX idx_project_batch_category (batch_id, category),
    ADD INDEX idx_project_owner (owner_user_id),
    ADD INDEX idx_project_supervisor (supervisor_id);

ALTER TABLE project_role_weight
    ADD INDEX idx_project_role_category (role_category);

ALTER TABLE project_expert
    ADD INDEX idx_project_expert_user (expert_user_id);

ALTER TABLE project_score_record
    ADD INDEX idx_project_record_project (batch_id, project_id),
    ADD INDEX idx_project_record_evaluator (batch_id, expert_user_id),
    ADD INDEX idx_project_record_status (batch_id, status);

ALTER TABLE project_score_detail
    ADD INDEX idx_project_detail_metric (metric_id);

-- ---------------------------------------------------------------------------
-- 7. Check constraints (MySQL 8.0.16+).
-- ---------------------------------------------------------------------------
ALTER TABLE sys_user
    ADD CONSTRAINT chk_sys_user_position_category CHECK (position_category IN ('PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'EMPLOYEE', ''));
ALTER TABLE appraisal_batch
    ADD CONSTRAINT chk_batch_type CHECK (batch_type IN ('MONTHLY', 'TARGET_PROJECT', 'SPECIAL_INNOVATION', 'ANNUAL_PROJECT')),
    ADD CONSTRAINT chk_batch_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED')),
    ADD CONSTRAINT chk_batch_monthly_category CHECK (monthly_category IN ('', 'DEPT_HEAD', 'DEPT_DEPUTY')),
    ADD CONSTRAINT chk_batch_project_category CHECK (project_category IN ('', 'BUSINESS', 'MANAGEMENT', 'ALL'));
ALTER TABLE appraisal_metric
    ADD CONSTRAINT chk_metric_score CHECK (max_score >= 0 AND weight >= 0 AND weight <= 100);
ALTER TABLE monthly_role_weight
    ADD CONSTRAINT chk_monthly_role_category CHECK (role_category IN ('PRIMARY_LEADER', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'DEPT_HEAD', 'PEER_HEAD', 'PEER_DEPUTY')),
    ADD CONSTRAINT chk_monthly_role_weight CHECK (weight >= 0 AND weight <= 100);
ALTER TABLE monthly_evaluator
    ADD CONSTRAINT chk_monthly_evaluator_weight CHECK (weight >= 0 AND weight <= 100);
ALTER TABLE monthly_score_record
    ADD CONSTRAINT chk_monthly_record_role CHECK (evaluator_role_type IN ('', 'PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'PEER_HEAD', 'PEER_DEPUTY', 'DEPT_HEAD_PEER', 'DEPT_DEPUTY_PEER')),
    ADD CONSTRAINT chk_monthly_record_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    ADD CONSTRAINT chk_monthly_record_score CHECK (total_score >= 0);
ALTER TABLE monthly_score_detail
    ADD CONSTRAINT chk_monthly_detail_score CHECK (score >= 0);
ALTER TABLE project_item
    ADD CONSTRAINT chk_project_item_category CHECK (category IN ('', 'BUSINESS', 'MANAGEMENT'));
ALTER TABLE project_role_weight
    ADD CONSTRAINT chk_project_role_category CHECK (role_category IN ('CENTER_LEADER_TOTAL', 'PRIMARY_LEADER', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'DEPT_HEAD', 'EXPERT')),
    ADD CONSTRAINT chk_project_role_weight CHECK (weight >= 0 AND weight <= 100);
ALTER TABLE project_expert
    ADD CONSTRAINT chk_project_expert_weight CHECK (weight >= 0 AND weight <= 100);
ALTER TABLE project_score_record
    ADD CONSTRAINT chk_project_record_role CHECK (evaluator_role_type IN ('', 'PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'PEER_HEAD', 'PEER_DEPUTY', 'DEPT_HEAD_PEER', 'DEPT_DEPUTY_PEER')),
    ADD CONSTRAINT chk_project_record_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    ADD CONSTRAINT chk_project_record_score CHECK (total_score >= 0);
ALTER TABLE project_score_detail
    ADD CONSTRAINT chk_project_detail_score CHECK (score >= 0);

-- ---------------------------------------------------------------------------
-- 8. Foreign keys.
-- ---------------------------------------------------------------------------
ALTER TABLE sys_user
    ADD CONSTRAINT fk_sys_user_supervisor FOREIGN KEY (supervisor_id) REFERENCES sys_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_sys_user_dept_head FOREIGN KEY (dept_head_id) REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE sys_user_role
    ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT;

ALTER TABLE appraisal_batch
    ADD CONSTRAINT fk_batch_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_batch_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE appraisal_metric
    ADD CONSTRAINT fk_metric_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;

ALTER TABLE monthly_role_weight
    ADD CONSTRAINT fk_monthly_role_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;

ALTER TABLE monthly_evaluator
    ADD CONSTRAINT fk_monthly_evaluator_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_monthly_evaluator_user FOREIGN KEY (leader_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT;

ALTER TABLE monthly_examinee
    ADD CONSTRAINT fk_monthly_examinee_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_monthly_examinee_user FOREIGN KEY (examinee_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT;

ALTER TABLE monthly_score_record
    ADD CONSTRAINT fk_monthly_record_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_monthly_record_evaluator FOREIGN KEY (leader_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_monthly_record_examinee FOREIGN KEY (examinee_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_monthly_record_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_monthly_record_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE monthly_score_detail
    ADD CONSTRAINT fk_monthly_detail_record FOREIGN KEY (record_id) REFERENCES monthly_score_record (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_monthly_detail_metric FOREIGN KEY (metric_id) REFERENCES appraisal_metric (id) ON DELETE RESTRICT;

ALTER TABLE project_item
    ADD CONSTRAINT fk_project_item_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_project_item_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_project_item_supervisor FOREIGN KEY (supervisor_id) REFERENCES sys_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_project_item_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_project_item_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE project_role_weight
    ADD CONSTRAINT fk_project_role_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;

ALTER TABLE project_expert
    ADD CONSTRAINT fk_project_expert_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_project_expert_user FOREIGN KEY (expert_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT;

ALTER TABLE project_score_record
    ADD CONSTRAINT fk_project_record_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_project_record_evaluator FOREIGN KEY (expert_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_project_record_project FOREIGN KEY (project_id) REFERENCES project_item (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_project_record_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_project_record_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE project_score_detail
    ADD CONSTRAINT fk_project_detail_record FOREIGN KEY (record_id) REFERENCES project_score_record (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_project_detail_metric FOREIGN KEY (metric_id) REFERENCES appraisal_metric (id) ON DELETE RESTRICT;
