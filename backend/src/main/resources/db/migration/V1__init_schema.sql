-- MySQL 8.0 initial schema (Flyway V1).
-- Fresh install only: this is the authoritative schema for new databases.
-- resources/schema.sql is a manual-inspection copy of this schema.

-- 1. Authentication and organization
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    department VARCHAR(64) NOT NULL,
    position VARCHAR(64) DEFAULT '',
    title VARCHAR(64) DEFAULT '',
    position_category VARCHAR(64) NOT NULL DEFAULT 'EMPLOYEE',
    supervisor_id BIGINT NULL,
    supervisor_name VARCHAR(64) DEFAULT '' COMMENT 'Snapshot; synchronized on save',
    dept_head_id BIGINT NULL,
    dept_head_name VARCHAR(64) DEFAULT '' COMMENT 'Snapshot; synchronized on save',
    email VARCHAR(128) DEFAULT '',
    phone VARCHAR(32) DEFAULT '',
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_sys_user_department (department),
    KEY idx_sys_user_position_category (position_category),
    KEY idx_sys_user_supervisor (supervisor_id),
    KEY idx_sys_user_dept_head (dept_head_id),
    KEY idx_sys_user_status (status),
    CONSTRAINT chk_sys_user_position_category CHECK (position_category IN ('PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'EMPLOYEE', '')),
    CONSTRAINT fk_sys_user_supervisor FOREIGN KEY (supervisor_id) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_sys_user_dept_head FOREIGN KEY (dept_head_id) REFERENCES sys_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System user';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255) DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System role';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_role_role (role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User-role relation';

-- 2. Appraisal batch and metrics
CREATE TABLE IF NOT EXISTS appraisal_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_name VARCHAR(128) NOT NULL,
    batch_type VARCHAR(32) NOT NULL,
    monthly_category VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'Monthly only: DEPT_HEAD / DEPT_DEPUTY',
    project_category VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'Project only: BUSINESS / MANAGEMENT / ALL',
    appraisal_period VARCHAR(32) NOT NULL,
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_batch_type_status (batch_type, status),
    KEY idx_batch_period (appraisal_period),
    KEY idx_batch_monthly_category (monthly_category),
    KEY idx_batch_project_category (project_category),
    KEY idx_batch_created_by (created_by),
    CONSTRAINT chk_batch_type CHECK (batch_type IN ('MONTHLY', 'TARGET_PROJECT', 'SPECIAL_INNOVATION', 'ANNUAL_PROJECT')),
    CONSTRAINT chk_batch_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_batch_monthly_category CHECK (monthly_category IN ('', 'DEPT_HEAD', 'DEPT_DEPUTY')),
    CONSTRAINT chk_batch_project_category CHECK (project_category IN ('', 'BUSINESS', 'MANAGEMENT', 'ALL')),
    CONSTRAINT fk_batch_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_batch_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Appraisal batch';

CREATE TABLE IF NOT EXISTS appraisal_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    metric_desc VARCHAR(255) DEFAULT '',
    max_score DECIMAL(10,4) NOT NULL DEFAULT 100.0000,
    weight DECIMAL(10,4) NOT NULL DEFAULT 20.0000,
    sort_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_metric_name (batch_id, metric_name),
    KEY idx_metric_batch_sort (batch_id, sort_order),
    CONSTRAINT chk_metric_score CHECK (max_score >= 0 AND weight >= 0 AND weight <= 100),
    CONSTRAINT fk_metric_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Appraisal metric';

-- 3. Monthly appraisal
CREATE TABLE IF NOT EXISTS monthly_role_weight (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    role_category VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    weight DECIMAL(10,4) NOT NULL DEFAULT 25.0000,
    sort_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_role_category (batch_id, role_category),
    KEY idx_monthly_role_category (role_category),
    CONSTRAINT chk_monthly_role_category CHECK (role_category IN ('PRIMARY_LEADER', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'DEPT_HEAD', 'PEER_HEAD', 'PEER_DEPUTY')),
    CONSTRAINT chk_monthly_role_weight CHECK (weight >= 0 AND weight <= 100),
    CONSTRAINT fk_monthly_role_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Monthly role weight';

CREATE TABLE IF NOT EXISTS monthly_evaluator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    leader_user_id BIGINT NOT NULL COMMENT 'Evaluator user id; kept for backward compatibility',
    weight DECIMAL(10,4) NOT NULL DEFAULT 50.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_leader (batch_id, leader_user_id),
    KEY idx_monthly_evaluator_user (leader_user_id),
    CONSTRAINT chk_monthly_evaluator_weight CHECK (weight >= 0 AND weight <= 100),
    CONSTRAINT fk_monthly_evaluator_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    CONSTRAINT fk_monthly_evaluator_user FOREIGN KEY (leader_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Monthly evaluator';

CREATE TABLE IF NOT EXISTS monthly_examinee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    examinee_user_id BIGINT NOT NULL,
    department VARCHAR(64) DEFAULT '',
    position VARCHAR(64) DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_examinee (batch_id, examinee_user_id),
    KEY idx_monthly_examinee_user (examinee_user_id),
    CONSTRAINT fk_monthly_examinee_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    CONSTRAINT fk_monthly_examinee_user FOREIGN KEY (examinee_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Monthly examinee';

CREATE TABLE IF NOT EXISTS monthly_score_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    leader_user_id BIGINT NOT NULL COMMENT 'Evaluator user id; kept for backward compatibility',
    examinee_user_id BIGINT NOT NULL,
    evaluator_role_type VARCHAR(32) NOT NULL DEFAULT '',
    total_score DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMP NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_batch_leader_examinee (batch_id, leader_user_id, examinee_user_id),
    KEY idx_monthly_record_examinee (batch_id, examinee_user_id),
    KEY idx_monthly_record_evaluator (batch_id, leader_user_id),
    KEY idx_monthly_record_status (batch_id, status),
    CONSTRAINT chk_monthly_record_role CHECK (evaluator_role_type IN ('', 'PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'PEER_HEAD', 'PEER_DEPUTY', 'DEPT_HEAD_PEER', 'DEPT_DEPUTY_PEER')),
    CONSTRAINT chk_monthly_record_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT chk_monthly_record_score CHECK (total_score >= 0),
    CONSTRAINT fk_monthly_record_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    CONSTRAINT fk_monthly_record_evaluator FOREIGN KEY (leader_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_monthly_record_examinee FOREIGN KEY (examinee_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_monthly_record_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_monthly_record_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Monthly score record';

CREATE TABLE IF NOT EXISTS monthly_score_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    metric_id BIGINT NOT NULL,
    score DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_record_metric (record_id, metric_id),
    KEY idx_monthly_detail_metric (metric_id),
    CONSTRAINT chk_monthly_detail_score CHECK (score >= 0),
    CONSTRAINT fk_monthly_detail_record FOREIGN KEY (record_id) REFERENCES monthly_score_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_monthly_detail_metric FOREIGN KEY (metric_id) REFERENCES appraisal_metric (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Monthly score detail';

-- 4. Project appraisal
CREATE TABLE IF NOT EXISTS project_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    owner_user_id BIGINT NULL,
    owner_name VARCHAR(64) NOT NULL COMMENT 'Snapshot of owner name',
    category VARCHAR(32) NOT NULL DEFAULT '',
    department VARCHAR(64) DEFAULT '',
    supervisor_id BIGINT NULL,
    material_pdf_name VARCHAR(255) DEFAULT '',
    material_pdf_path VARCHAR(255) DEFAULT '',
    attachment_pdf_name VARCHAR(255) DEFAULT '',
    attachment_pdf_path VARCHAR(255) DEFAULT '',
    sort_order INT NOT NULL DEFAULT 1,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_batch_project_code (batch_id, project_code),
    KEY idx_project_batch_category (batch_id, category),
    KEY idx_project_owner (owner_user_id),
    KEY idx_project_supervisor (supervisor_id),
    CONSTRAINT chk_project_item_category CHECK (category IN ('', 'BUSINESS', 'MANAGEMENT')),
    CONSTRAINT fk_project_item_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_item_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_project_item_supervisor FOREIGN KEY (supervisor_id) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_project_item_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_project_item_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Project item';

CREATE TABLE IF NOT EXISTS project_role_weight (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    role_category VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    weight DECIMAL(10,4) NOT NULL DEFAULT 10.0000,
    sort_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_proj_batch_role_category (batch_id, role_category),
    KEY idx_project_role_category (role_category),
    CONSTRAINT chk_project_role_category CHECK (role_category IN ('CENTER_LEADER_TOTAL', 'PRIMARY_LEADER', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'DEPT_HEAD', 'EXPERT')),
    CONSTRAINT chk_project_role_weight CHECK (weight >= 0 AND weight <= 100),
    CONSTRAINT fk_project_role_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Project role weight';

CREATE TABLE IF NOT EXISTS project_expert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL COMMENT 'Evaluator user id; kept for backward compatibility',
    weight DECIMAL(10,4) NOT NULL DEFAULT 100.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_expert (batch_id, expert_user_id),
    KEY idx_project_expert_user (expert_user_id),
    CONSTRAINT chk_project_expert_weight CHECK (weight >= 0 AND weight <= 100),
    CONSTRAINT fk_project_expert_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_expert_user FOREIGN KEY (expert_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Project evaluator';

CREATE TABLE IF NOT EXISTS project_score_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL COMMENT 'Evaluator user id; kept for backward compatibility',
    project_id BIGINT NOT NULL,
    evaluator_role_type VARCHAR(32) NOT NULL DEFAULT '',
    total_score DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMP NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_batch_expert_project (batch_id, expert_user_id, project_id),
    KEY idx_project_record_project (batch_id, project_id),
    KEY idx_project_record_evaluator (batch_id, expert_user_id),
    KEY idx_project_record_status (batch_id, status),
    CONSTRAINT chk_project_record_role CHECK (evaluator_role_type IN ('', 'PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'PEER_HEAD', 'PEER_DEPUTY', 'DEPT_HEAD_PEER', 'DEPT_DEPUTY_PEER')),
    CONSTRAINT chk_project_record_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT chk_project_record_score CHECK (total_score >= 0),
    CONSTRAINT fk_project_record_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_record_evaluator FOREIGN KEY (expert_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_record_project FOREIGN KEY (project_id) REFERENCES project_item (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_record_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_project_record_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Project score record';

CREATE TABLE IF NOT EXISTS project_score_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    metric_id BIGINT NOT NULL,
    score DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_proj_record_metric (record_id, metric_id),
    KEY idx_project_detail_metric (metric_id),
    CONSTRAINT chk_project_detail_score CHECK (score >= 0),
    CONSTRAINT fk_project_detail_record FOREIGN KEY (record_id) REFERENCES project_score_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_detail_metric FOREIGN KEY (metric_id) REFERENCES appraisal_metric (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Project score detail';
