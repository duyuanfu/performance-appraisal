-- 1. Insert Initial Roles (INSERT IGNORE 避免重启覆盖或主键冲突)
INSERT IGNORE INTO sys_role (id, role_code, role_name, description) VALUES
(1, 'ADMIN', '管理员', '负责系统配置、任务发布、结果汇总与导出'),
(2, 'LEADER', '中心领导', '负责月度绩效考核打分'),
(3, 'EXPERT', '评审专家', '负责年度目标管理项目评审打分'),
(4, 'EMPLOYEE', '被考核人员', '负责查看本人月度绩效考核结果与排名');

-- 2. Insert Users (Default Password: "password123", SHA-256: ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f)
INSERT IGNORE INTO sys_user (id, username, password_hash, real_name, department, position, email, phone, status) VALUES
(1, 'admin', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '系统管理员', '原料中心综合处', '主管', 'admin@rawcenter.com', '13800000001', 1),
(2, 'leader_zhang', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '张主任', '原料中心领导班子', '中心主任', 'zhang@rawcenter.com', '13800000002', 1),
(3, 'leader_li', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '李副主任', '原料中心领导班子', '中心副主任', 'li@rawcenter.com', '13800000003', 1),
(4, 'expert_wang', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '王专家', '技术评审委员会', '资深专家', 'wang@rawcenter.com', '13800000004', 1),
(5, 'expert_zhao', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '赵专家', '战略规划委员会', '资深专家', 'zhao@rawcenter.com', '13800000005', 1),
(6, 'expert_sun', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '孙专家', '质量安全委员会', '资深专家', 'sun@rawcenter.com', '13800000006', 1),
(7, 'dept_qian', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '钱部长', '采购一处', '部门负责人', 'qian@rawcenter.com', '13800000007', 1),
(8, 'dept_zhou', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '周部长', '采购二处', '部门负责人', 'zhou@rawcenter.com', '13800000008', 1),
(9, 'dept_wu', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '吴部长', '仓储物流处', '部门负责人', 'wu@rawcenter.com', '13800000009', 1),
(10, 'dept_zheng', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '郑部长', '品质检验处', '部门负责人', 'zheng@rawcenter.com', '13800000010', 1);

-- 3. Assign User Roles (Demonstrating multi-role support for dept_qian as both EMPLOYEE and EXPERT)
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
(1, 1), -- admin -> ADMIN
(2, 2), -- leader_zhang -> LEADER
(3, 2), -- leader_li -> LEADER
(4, 3), -- expert_wang -> EXPERT
(5, 3), -- expert_zhao -> EXPERT
(6, 3), -- expert_sun -> EXPERT
(7, 4), -- dept_qian -> EMPLOYEE
(7, 3), -- dept_qian -> EXPERT (一人多角)
(8, 4), -- dept_zhou -> EMPLOYEE
(9, 4), -- dept_wu -> EMPLOYEE
(10, 4); -- dept_zheng -> EMPLOYEE
