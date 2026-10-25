-- H2 In-Memory Schema for Unit/Integration Tests

-- 1. 用户与认证表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    department VARCHAR(64) NOT NULL,
    position VARCHAR(64) DEFAULT '',
    title VARCHAR(64) DEFAULT '',
    position_category VARCHAR(64) DEFAULT 'EMPLOYEE',
    supervisor_id BIGINT NULL,
    supervisor_name VARCHAR(64) DEFAULT '',
    dept_head_id BIGINT NULL,
    dept_head_name VARCHAR(64) DEFAULT '',
    email VARCHAR(128) DEFAULT '',
    phone VARCHAR(32) DEFAULT '',
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255) DEFAULT ''
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
);

-- 2. 考核批次表
CREATE TABLE IF NOT EXISTS appraisal_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_name VARCHAR(128) NOT NULL,
    batch_type VARCHAR(32) NOT NULL,
    category VARCHAR(32) DEFAULT '',
    appraisal_period VARCHAR(32) NOT NULL,
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 3. 考核指标表
CREATE TABLE IF NOT EXISTS appraisal_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    metric_desc VARCHAR(255) DEFAULT '',
    max_score DECIMAL(10,4) NOT NULL DEFAULT 100.0000,
    weight DECIMAL(10,4) NOT NULL DEFAULT 20.0000,
    sort_order INT NOT NULL DEFAULT 1
);

-- 4. 月度绩效考核配置与打分表
CREATE TABLE IF NOT EXISTS monthly_role_weight (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    role_category VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    weight DECIMAL(10,4) NOT NULL DEFAULT 25.0000,
    sort_order INT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_batch_role_category (batch_id, role_category)
);

CREATE TABLE IF NOT EXISTS monthly_evaluator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    leader_user_id BIGINT NOT NULL,
    weight DECIMAL(10,4) NOT NULL DEFAULT 50.0000,
    UNIQUE KEY uk_batch_leader (batch_id, leader_user_id)
);

CREATE TABLE IF NOT EXISTS monthly_examinee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    examinee_user_id BIGINT NOT NULL,
    department VARCHAR(64) DEFAULT '',
    position VARCHAR(64) DEFAULT '',
    UNIQUE KEY uk_batch_examinee (batch_id, examinee_user_id)
);

CREATE TABLE IF NOT EXISTS monthly_score_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    leader_user_id BIGINT NOT NULL,
    examinee_user_id BIGINT NOT NULL,
    evaluator_role_type VARCHAR(32) DEFAULT '',
    total_score DECIMAL(10,4) DEFAULT 0.0000,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMP NULL,
    UNIQUE KEY uk_batch_leader_examinee (batch_id, leader_user_id, examinee_user_id)
);

CREATE TABLE IF NOT EXISTS monthly_score_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    metric_id BIGINT NOT NULL,
    score DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    UNIQUE KEY uk_record_metric (record_id, metric_id)
);

-- 5. 目标绩效任务与专项创新任务评审配置与打分表
CREATE TABLE IF NOT EXISTS project_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    owner_name VARCHAR(64) NOT NULL,
    category VARCHAR(32) DEFAULT '',
    department VARCHAR(64) DEFAULT '',
    award_tier VARCHAR(32) DEFAULT '',
    score_coefficient DECIMAL(10,4) DEFAULT NULL,
    bonus_amount DECIMAL(12,4) DEFAULT NULL,
    rank_num INT DEFAULT NULL,
    material_pdf_name VARCHAR(255) DEFAULT '',
    material_pdf_path VARCHAR(255) DEFAULT '',
    attachment_pdf_name VARCHAR(255) DEFAULT '',
    attachment_pdf_path VARCHAR(255) DEFAULT '',
    sort_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_role_weight (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    role_category VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    weight DECIMAL(10,4) NOT NULL DEFAULT 10.0000,
    sort_order INT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_proj_batch_role_category (batch_id, role_category)
);

CREATE TABLE IF NOT EXISTS project_expert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL,
    weight DECIMAL(10,4) NOT NULL DEFAULT 100.0000,
    UNIQUE KEY uk_batch_expert (batch_id, expert_user_id)
);

CREATE TABLE IF NOT EXISTS project_score_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    evaluator_role_type VARCHAR(32) DEFAULT '',
    total_score DECIMAL(10,4) DEFAULT 0.0000,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMP NULL,
    UNIQUE KEY uk_batch_expert_project (batch_id, expert_user_id, project_id)
);

CREATE TABLE IF NOT EXISTS project_score_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    metric_id BIGINT NOT NULL,
    score DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    UNIQUE KEY uk_proj_record_metric (record_id, metric_id)
);
