package com.rawcenter.appraisal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * 数据库启动初始化与增量列迁移器
 * 在服务启动时自动检查各表结构，自动平滑补充 V2 升级所需新增字段，确保无需手动执行 ALTER TABLE。
 */
@Slf4j
@Component
@Order(1)
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(String... args) {
        log.info("[DatabaseInitializer] 开始检查并自动平滑升级数据库表结构...");
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // 1. 检查并补充 sys_user 表字段
            ensureColumn(metaData, stmt, "sys_user", "title", "VARCHAR(64) DEFAULT '' COMMENT '职务名称'");
            ensureColumn(metaData, stmt, "sys_user", "position_category", "VARCHAR(64) DEFAULT 'EMPLOYEE' COMMENT '岗位类别'");
            ensureColumn(metaData, stmt, "sys_user", "supervisor_id", "BIGINT NULL COMMENT '分管领导用户ID'");
            ensureColumn(metaData, stmt, "sys_user", "supervisor_name", "VARCHAR(64) DEFAULT '' COMMENT '分管领导姓名'");
            ensureColumn(metaData, stmt, "sys_user", "dept_head_id", "BIGINT NULL COMMENT '直属部门正职领导ID'");
            ensureColumn(metaData, stmt, "sys_user", "dept_head_name", "VARCHAR(64) DEFAULT '' COMMENT '直属部门正职姓名'");

            // 2. 检查并补充 appraisal_batch 表字段
            ensureColumn(metaData, stmt, "appraisal_batch", "category", "VARCHAR(32) DEFAULT '' COMMENT '评审类别: BUSINESS, MANAGEMENT, ALL'");

            // 3. 检查并补充 project_item 表字段
            ensureColumn(metaData, stmt, "project_item", "category", "VARCHAR(32) DEFAULT '' COMMENT '申报类别: BUSINESS, MANAGEMENT'");
            ensureColumn(metaData, stmt, "project_item", "department", "VARCHAR(64) DEFAULT '' COMMENT '申报部门'");
            ensureColumn(metaData, stmt, "project_item", "award_tier", "VARCHAR(32) DEFAULT '' COMMENT '评定奖项等次'");
            ensureColumn(metaData, stmt, "project_item", "score_coefficient", "DECIMAL(10,4) DEFAULT NULL COMMENT '得分系数'");
            ensureColumn(metaData, stmt, "project_item", "bonus_amount", "DECIMAL(12,4) DEFAULT NULL COMMENT '核定奖励金额'");
            ensureColumn(metaData, stmt, "project_item", "rank_num", "INT DEFAULT NULL COMMENT '排序序号'");

            // 4. 检查并补充 monthly_score_record 表字段
            ensureColumn(metaData, stmt, "monthly_score_record", "evaluator_role_type", "VARCHAR(32) DEFAULT '' COMMENT '评价人角色'");

            // 5. 检查并补充 project_score_record 表字段
            ensureColumn(metaData, stmt, "project_score_record", "evaluator_role_type", "VARCHAR(32) DEFAULT '' COMMENT '打分角色分类'");

            // 6. 自动检查并创建 monthly_role_weight 与 project_role_weight 表
            stmt.execute("CREATE TABLE IF NOT EXISTS monthly_role_weight (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID'," +
                    "batch_id BIGINT NOT NULL COMMENT '批次ID'," +
                    "role_category VARCHAR(32) NOT NULL COMMENT '角色分类'," +
                    "role_name VARCHAR(64) NOT NULL COMMENT '角色名称'," +
                    "weight DECIMAL(10,4) NOT NULL DEFAULT 25.0000 COMMENT '权重百分比'," +
                    "sort_order INT NOT NULL DEFAULT 1 COMMENT '排序序号'," +
                    "UNIQUE KEY uk_batch_role_category (batch_id, role_category)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月度考核角色类别权重配置表'");

            stmt.execute("CREATE TABLE IF NOT EXISTS project_role_weight (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID'," +
                    "batch_id BIGINT NOT NULL COMMENT '批次ID'," +
                    "role_category VARCHAR(32) NOT NULL COMMENT '角色分类'," +
                    "role_name VARCHAR(64) NOT NULL COMMENT '角色名称'," +
                    "weight DECIMAL(10,4) NOT NULL DEFAULT 10.0000 COMMENT '权重百分比'," +
                    "sort_order INT NOT NULL DEFAULT 1 COMMENT '排序序号'," +
                    "UNIQUE KEY uk_proj_batch_role_category (batch_id, role_category)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目评审角色类别权重配置表'");

            // 7. 清理可能残留的 V1 测试临时旧账号
            stmt.execute("DELETE FROM sys_user WHERE username IN ('leader_zhang', 'expert_wang', 'expert_zhao', 'expert_sun', 'dept_qian', 'dept_zhou', 'dept_wu', 'dept_zheng')");

            // 8. 严格对齐《评审管理系统相关.xlsx》测试账号配置，精简保留 11 位测试人员 + 2 位管理员
            stmt.execute("DELETE FROM sys_user WHERE username NOT IN (" +
                    "'admin', 'sys_admin', " +
                    "'13908866003', '13888952359', '13888279030', " + // 3位中心领导: 陈剑, 施文萃, 邱晔
                    "'13987184673', '13888837073', '15912553435', " + // 3位部门正职: 王昆淼, 张玉丰, 李正兵
                    "'15208773024', '18687778502', '13529376500', " + // 3位部门副职: 田阳阳, 陈超, 张燕
                    "'13085371368', '17787141508'" +                   // 2位评审专家: 徐济仓, 刘海业
                    ")");

            // 9. 清除王昆淼及正职、副职人员多余的普通职工 (EMPLOYEE) 角色映射，确保身份单一清晰
            stmt.execute("DELETE FROM sys_user_role WHERE role_id = (SELECT id FROM sys_role WHERE role_code = 'EMPLOYEE') " +
                    "AND user_id IN (SELECT id FROM sys_user WHERE position_category IN ('DEPT_HEAD', 'DEPT_DEPUTY', 'PRIMARY_LEADER', 'CENTER_LEADER', 'EXPERT'))");

            // 10. 清理残留的用户角色映射孤立数据
            stmt.execute("DELETE FROM sys_user_role WHERE user_id NOT IN (SELECT id FROM sys_user)");

            log.info("[DatabaseInitializer] 数据库表结构平滑升级检查完成！");
        } catch (Exception e) {
            log.warn("[DatabaseInitializer] 数据库表结构自动升级发生非致命异常，跳过: {}", e.getMessage());
        }
    }

    private void ensureColumn(DatabaseMetaData metaData, Statement stmt, String tableName, String columnName, String columnDef) {
        try {
            Set<String> existingColumns = new HashSet<>();
            try (ResultSet rs = metaData.getColumns(null, null, tableName.toLowerCase(), null)) {
                while (rs.next()) {
                    existingColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
            if (existingColumns.isEmpty()) {
                try (ResultSet rs = metaData.getColumns(null, null, tableName.toUpperCase(), null)) {
                    while (rs.next()) {
                        existingColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
                    }
                }
            }

            if (!existingColumns.contains(columnName.toLowerCase())) {
                String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef;
                log.info("[DatabaseInitializer] 正在为表 {} 添加新增列: {}", tableName, columnName);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            log.debug("[DatabaseInitializer] 表 {} 添加列 {} 检查提示: {}", tableName, columnName, e.getMessage());
        }
    }
}
