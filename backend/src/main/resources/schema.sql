-- MySQL 8.0 数据库初始化脚本（持久化模式：不删除已有表与历史数据）

-- 1. 用户与认证表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户主键ID',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '登录用户名',
    password_hash VARCHAR(128) NOT NULL COMMENT '登录密码哈希 (SHA-256)',
    real_name VARCHAR(64) NOT NULL COMMENT '真实姓名',
    department VARCHAR(64) NOT NULL COMMENT '所属部门',
    position VARCHAR(64) DEFAULT '' COMMENT '职务/岗位',
    email VARCHAR(128) DEFAULT '' COMMENT '邮箱',
    phone VARCHAR(32) DEFAULT '' COMMENT '联系电话',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色主键ID',
    role_code VARCHAR(32) NOT NULL UNIQUE COMMENT '角色标识: ADMIN/LEADER/EXPERT/EMPLOYEE',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) DEFAULT '' COMMENT '角色描述'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 2. 考核批次表
CREATE TABLE IF NOT EXISTS appraisal_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '批次主键ID',
    batch_name VARCHAR(128) NOT NULL COMMENT '批次名称',
    batch_type VARCHAR(32) NOT NULL COMMENT '批次类型: MONTHLY (月度), ANNUAL_PROJECT (年度项目)',
    appraisal_period VARCHAR(32) NOT NULL COMMENT '考核周期: 2026-10 或 2026',
    start_time TIMESTAMP NULL COMMENT '评分开始时间',
    end_time TIMESTAMP NULL COMMENT '评分截止时间',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT (草稿), IN_PROGRESS (进行中), COMPLETED (已归档)',
    created_by BIGINT NULL COMMENT '创建人用户ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考核评审批次表';

-- 3. 考核指标表
CREATE TABLE IF NOT EXISTS appraisal_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '指标主键ID',
    batch_id BIGINT NOT NULL COMMENT '所属批次ID',
    metric_name VARCHAR(64) NOT NULL COMMENT '指标名称',
    metric_desc VARCHAR(255) DEFAULT '' COMMENT '指标描述/评分标准',
    max_score DECIMAL(10,4) NOT NULL DEFAULT 100.0000 COMMENT '指标满分分值',
    weight DECIMAL(10,4) NOT NULL DEFAULT 20.0000 COMMENT '指标权重百分比 (累计必须为100)',
    sort_order INT NOT NULL DEFAULT 1 COMMENT '排序号',
    KEY idx_batch_id (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考核指标项表';

-- 4. 月度绩效考核配置与打分表
CREATE TABLE IF NOT EXISTS monthly_evaluator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    batch_id BIGINT NOT NULL COMMENT '批次ID',
    leader_user_id BIGINT NOT NULL COMMENT '中心领导用户ID',
    weight DECIMAL(10,4) NOT NULL DEFAULT 50.0000 COMMENT '该领导打分权重百分比',
    UNIQUE KEY uk_batch_leader (batch_id, leader_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月度考核评分领导配置表';

CREATE TABLE IF NOT EXISTS monthly_examinee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    batch_id BIGINT NOT NULL COMMENT '批次ID',
    examinee_user_id BIGINT NOT NULL COMMENT '被考核人员用户ID',
    department VARCHAR(64) DEFAULT '' COMMENT '所属部门',
    position VARCHAR(64) DEFAULT '' COMMENT '职务',
    UNIQUE KEY uk_batch_examinee (batch_id, examinee_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月度被考核对象配置表';

CREATE TABLE IF NOT EXISTS monthly_score_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评分记录ID',
    batch_id BIGINT NOT NULL COMMENT '批次ID',
    leader_user_id BIGINT NOT NULL COMMENT '打分领导ID',
    examinee_user_id BIGINT NOT NULL COMMENT '被考核人员ID',
    total_score DECIMAL(10,4) DEFAULT 0.0000 COMMENT '该领导给出的加权总分 (保留4位小数)',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT (暂存草稿), SUBMITTED (正式提交)',
    submitted_at TIMESTAMP NULL COMMENT '提交时间',
    UNIQUE KEY uk_batch_leader_examinee (batch_id, leader_user_id, examinee_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月度领导打分记录表';

CREATE TABLE IF NOT EXISTS monthly_score_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细主键ID',
    record_id BIGINT NOT NULL COMMENT '评分主记录ID',
    metric_id BIGINT NOT NULL COMMENT '指标ID',
    score DECIMAL(10,4) NOT NULL DEFAULT 0.0000 COMMENT '单项指标得分 (保留4位小数)',
    UNIQUE KEY uk_record_metric (record_id, metric_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月度单项指标评分明细表';

-- 5. 年度目标管理项目评审配置与打分表
CREATE TABLE IF NOT EXISTS project_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '项目主键ID',
    batch_id BIGINT NOT NULL COMMENT '所属评审批次ID',
    project_code VARCHAR(64) NOT NULL COMMENT '项目序号/编号',
    project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
    owner_name VARCHAR(64) NOT NULL COMMENT '项目负责人姓名 (文本展示)',
    material_pdf_name VARCHAR(255) DEFAULT '' COMMENT '申报材料原始文件名',
    material_pdf_path VARCHAR(255) DEFAULT '' COMMENT '申报材料物理文件存储路径',
    attachment_pdf_name VARCHAR(255) DEFAULT '' COMMENT '附件材料原始文件名',
    attachment_pdf_path VARCHAR(255) DEFAULT '' COMMENT '附件材料物理文件存储路径',
    sort_order INT NOT NULL DEFAULT 1 COMMENT '排序序号',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
    KEY idx_proj_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年度参评项目表';

CREATE TABLE IF NOT EXISTS project_expert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    batch_id BIGINT NOT NULL COMMENT '评审批次ID',
    expert_user_id BIGINT NOT NULL COMMENT '评审专家用户ID',
    weight DECIMAL(10,4) NOT NULL DEFAULT 100.0000 COMMENT '专家打分权重百分比',
    UNIQUE KEY uk_batch_expert (batch_id, expert_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年度项目评审专家配置表';

CREATE TABLE IF NOT EXISTS project_score_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评审评分记录ID',
    batch_id BIGINT NOT NULL COMMENT '批次ID',
    expert_user_id BIGINT NOT NULL COMMENT '评审专家ID',
    project_id BIGINT NOT NULL COMMENT '参评项目ID',
    total_score DECIMAL(10,4) DEFAULT 0.0000 COMMENT '专家总评分 (保留4位小数)',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT (暂存草稿), SUBMITTED (正式提交)',
    submitted_at TIMESTAMP NULL COMMENT '提交时间',
    UNIQUE KEY uk_batch_expert_project (batch_id, expert_user_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年度项目专家打分记录表';

CREATE TABLE IF NOT EXISTS project_score_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细主键ID',
    record_id BIGINT NOT NULL COMMENT '打分主记录ID',
    metric_id BIGINT NOT NULL COMMENT '指标ID',
    score DECIMAL(10,4) NOT NULL DEFAULT 0.0000 COMMENT '单项指标得分 (保留4位小数)',
    UNIQUE KEY uk_proj_record_metric (record_id, metric_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年度项目单项指标评分明细表';
