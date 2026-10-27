-- H2 in-memory schema for unit/integration tests.
-- Keep this file in sync with db/migration/V2__p0_p1_p2_hardening.sql.

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
    supervisor_name VARCHAR(64) DEFAULT '',
    dept_head_id BIGINT NULL,
    dept_head_name VARCHAR(64) DEFAULT '',
    email VARCHAR(128) DEFAULT '',
    phone VARCHAR(32) DEFAULT '',
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_sys_user_position_category CHECK (position_category IN ('PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'EMPLOYEE', ''))
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255) DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id)
);

-- 2. Appraisal batch and metrics
CREATE TABLE IF NOT EXISTS appraisal_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_name VARCHAR(128) NOT NULL,
    batch_type VARCHAR(32) NOT NULL,
    monthly_category VARCHAR(32) NOT NULL DEFAULT '',
    project_category VARCHAR(32) NOT NULL DEFAULT '',
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
    CONSTRAINT chk_batch_type CHECK (batch_type IN ('MONTHLY', 'TARGET_PROJECT', 'SPECIAL_INNOVATION', 'ANNUAL_PROJECT')),
    CONSTRAINT chk_batch_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_batch_monthly_category CHECK (monthly_category IN ('', 'DEPT_HEAD', 'DEPT_DEPUTY')),
    CONSTRAINT chk_batch_project_category CHECK (project_category IN ('', 'BUSINESS', 'MANAGEMENT', 'ALL'))
);

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
    CONSTRAINT chk_metric_score CHECK (max_score >= 0 AND weight >= 0 AND weight <= 100)
);

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
    CONSTRAINT chk_monthly_role_category CHECK (role_category IN ('PRIMARY_LEADER', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'DEPT_HEAD', 'PEER_HEAD', 'PEER_DEPUTY')),
    CONSTRAINT chk_monthly_role_weight CHECK (weight >= 0 AND weight <= 100)
);

CREATE TABLE IF NOT EXISTS monthly_evaluator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    leader_user_id BIGINT NOT NULL,
    weight DECIMAL(10,4) NOT NULL DEFAULT 50.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_leader (batch_id, leader_user_id),
    CONSTRAINT chk_monthly_evaluator_weight CHECK (weight >= 0 AND weight <= 100)
);

CREATE TABLE IF NOT EXISTS monthly_examinee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    examinee_user_id BIGINT NOT NULL,
    department VARCHAR(64) DEFAULT '',
    position VARCHAR(64) DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_examinee (batch_id, examinee_user_id)
);

CREATE TABLE IF NOT EXISTS monthly_score_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    leader_user_id BIGINT NOT NULL,
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
    CONSTRAINT chk_monthly_record_role CHECK (evaluator_role_type IN ('', 'PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'PEER_HEAD', 'PEER_DEPUTY', 'DEPT_HEAD_PEER', 'DEPT_DEPUTY_PEER')),
    CONSTRAINT chk_monthly_record_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT chk_monthly_record_score CHECK (total_score >= 0)
);

CREATE TABLE IF NOT EXISTS monthly_score_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    metric_id BIGINT NOT NULL,
    score DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_record_metric (record_id, metric_id),
    CONSTRAINT chk_monthly_detail_score CHECK (score >= 0)
);

-- 4. Project appraisal
CREATE TABLE IF NOT EXISTS project_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT '',
    department VARCHAR(128) NOT NULL DEFAULT '',
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
    CONSTRAINT chk_project_item_category CHECK (category IN ('', 'BUSINESS', 'MANAGEMENT'))
);

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
    CONSTRAINT chk_project_role_category CHECK (role_category IN ('CENTER_LEADER_TOTAL', 'PRIMARY_LEADER', 'OTHER_LEADER', 'DEPT_HEAD', 'EXPERT')),
    CONSTRAINT chk_project_role_weight CHECK (weight >= 0 AND weight <= 100)
);

CREATE TABLE IF NOT EXISTS project_expert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL,
    weight DECIMAL(10,4) NOT NULL DEFAULT 100.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_expert (batch_id, expert_user_id),
    CONSTRAINT chk_project_expert_weight CHECK (weight >= 0 AND weight <= 100)
);

CREATE TABLE IF NOT EXISTS project_score_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL,
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
    CONSTRAINT chk_project_record_role CHECK (evaluator_role_type IN ('', 'PRIMARY_LEADER', 'CENTER_LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY', 'EXPERT', 'SUPERVISOR_LEADER', 'OTHER_LEADER', 'PEER_HEAD', 'PEER_DEPUTY', 'DEPT_HEAD_PEER', 'DEPT_DEPUTY_PEER')),
    CONSTRAINT chk_project_record_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT chk_project_record_score CHECK (total_score >= 0)
);

CREATE TABLE IF NOT EXISTS project_score_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    metric_id BIGINT NOT NULL,
    score DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_proj_record_metric (record_id, metric_id),
    CONSTRAINT chk_project_detail_score CHECK (score >= 0)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_sys_user_department ON sys_user (department);
CREATE INDEX IF NOT EXISTS idx_sys_user_position_category ON sys_user (position_category);
CREATE INDEX IF NOT EXISTS idx_sys_user_supervisor ON sys_user (supervisor_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_dept_head ON sys_user (dept_head_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_status ON sys_user (status);
CREATE INDEX IF NOT EXISTS idx_user_role_role ON sys_user_role (role_id);
CREATE INDEX IF NOT EXISTS idx_batch_type_status ON appraisal_batch (batch_type, status);
CREATE INDEX IF NOT EXISTS idx_batch_period ON appraisal_batch (appraisal_period);
CREATE INDEX IF NOT EXISTS idx_batch_monthly_category ON appraisal_batch (monthly_category);
CREATE INDEX IF NOT EXISTS idx_batch_project_category ON appraisal_batch (project_category);
CREATE INDEX IF NOT EXISTS idx_batch_created_by ON appraisal_batch (created_by);
CREATE INDEX IF NOT EXISTS idx_metric_batch_sort ON appraisal_metric (batch_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_monthly_role_category ON monthly_role_weight (role_category);
CREATE INDEX IF NOT EXISTS idx_monthly_evaluator_user ON monthly_evaluator (leader_user_id);
CREATE INDEX IF NOT EXISTS idx_monthly_examinee_user ON monthly_examinee (examinee_user_id);
CREATE INDEX IF NOT EXISTS idx_monthly_record_examinee ON monthly_score_record (batch_id, examinee_user_id);
CREATE INDEX IF NOT EXISTS idx_monthly_record_evaluator ON monthly_score_record (batch_id, leader_user_id);
CREATE INDEX IF NOT EXISTS idx_monthly_record_status ON monthly_score_record (batch_id, status);
CREATE INDEX IF NOT EXISTS idx_monthly_detail_metric ON monthly_score_detail (metric_id);
CREATE INDEX IF NOT EXISTS idx_project_batch_category ON project_item (batch_id, category);
CREATE INDEX IF NOT EXISTS idx_project_role_category ON project_role_weight (role_category);
CREATE INDEX IF NOT EXISTS idx_project_expert_user ON project_expert (expert_user_id);
CREATE INDEX IF NOT EXISTS idx_project_record_project ON project_score_record (batch_id, project_id);
CREATE INDEX IF NOT EXISTS idx_project_record_evaluator ON project_score_record (batch_id, expert_user_id);
CREATE INDEX IF NOT EXISTS idx_project_record_status ON project_score_record (batch_id, status);
CREATE INDEX IF NOT EXISTS idx_project_detail_metric ON project_score_detail (metric_id);

-- Foreign keys
ALTER TABLE sys_user ADD CONSTRAINT fk_sys_user_supervisor FOREIGN KEY (supervisor_id) REFERENCES sys_user (id) ON DELETE SET NULL;
ALTER TABLE sys_user ADD CONSTRAINT fk_sys_user_dept_head FOREIGN KEY (dept_head_id) REFERENCES sys_user (id) ON DELETE SET NULL;
ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE;
ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT;
ALTER TABLE appraisal_batch ADD CONSTRAINT fk_batch_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL;
ALTER TABLE appraisal_batch ADD CONSTRAINT fk_batch_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL;
ALTER TABLE appraisal_metric ADD CONSTRAINT fk_metric_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;
ALTER TABLE monthly_role_weight ADD CONSTRAINT fk_monthly_role_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;
ALTER TABLE monthly_evaluator ADD CONSTRAINT fk_monthly_evaluator_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;
ALTER TABLE monthly_evaluator ADD CONSTRAINT fk_monthly_evaluator_user FOREIGN KEY (leader_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT;
ALTER TABLE monthly_examinee ADD CONSTRAINT fk_monthly_examinee_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;
ALTER TABLE monthly_examinee ADD CONSTRAINT fk_monthly_examinee_user FOREIGN KEY (examinee_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT;
ALTER TABLE monthly_score_record ADD CONSTRAINT fk_monthly_record_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;
ALTER TABLE monthly_score_record ADD CONSTRAINT fk_monthly_record_evaluator FOREIGN KEY (leader_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT;
ALTER TABLE monthly_score_record ADD CONSTRAINT fk_monthly_record_examinee FOREIGN KEY (examinee_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT;
ALTER TABLE monthly_score_record ADD CONSTRAINT fk_monthly_record_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL;
ALTER TABLE monthly_score_record ADD CONSTRAINT fk_monthly_record_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL;
ALTER TABLE monthly_score_detail ADD CONSTRAINT fk_monthly_detail_record FOREIGN KEY (record_id) REFERENCES monthly_score_record (id) ON DELETE CASCADE;
ALTER TABLE monthly_score_detail ADD CONSTRAINT fk_monthly_detail_metric FOREIGN KEY (metric_id) REFERENCES appraisal_metric (id) ON DELETE RESTRICT;
ALTER TABLE project_item ADD CONSTRAINT fk_project_item_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;
ALTER TABLE project_item ADD CONSTRAINT fk_project_item_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL;
ALTER TABLE project_item ADD CONSTRAINT fk_project_item_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL;
ALTER TABLE project_role_weight ADD CONSTRAINT fk_project_role_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;
ALTER TABLE project_expert ADD CONSTRAINT fk_project_expert_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;
ALTER TABLE project_expert ADD CONSTRAINT fk_project_expert_user FOREIGN KEY (expert_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT;
ALTER TABLE project_score_record ADD CONSTRAINT fk_project_record_batch FOREIGN KEY (batch_id) REFERENCES appraisal_batch (id) ON DELETE CASCADE;
ALTER TABLE project_score_record ADD CONSTRAINT fk_project_record_evaluator FOREIGN KEY (expert_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT;
ALTER TABLE project_score_record ADD CONSTRAINT fk_project_record_project FOREIGN KEY (project_id) REFERENCES project_item (id) ON DELETE CASCADE;
ALTER TABLE project_score_record ADD CONSTRAINT fk_project_record_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL;
ALTER TABLE project_score_record ADD CONSTRAINT fk_project_record_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE SET NULL;
ALTER TABLE project_score_detail ADD CONSTRAINT fk_project_detail_record FOREIGN KEY (record_id) REFERENCES project_score_record (id) ON DELETE CASCADE;
ALTER TABLE project_score_detail ADD CONSTRAINT fk_project_detail_metric FOREIGN KEY (metric_id) REFERENCES appraisal_metric (id) ON DELETE RESTRICT;
