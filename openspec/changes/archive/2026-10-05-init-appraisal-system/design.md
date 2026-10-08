## Context

原料中心（规模约 50 人）需要建设一套面向 WEB 端的绩效评审管理系统。系统包含两大核心业务模块：
1. **月度绩效评审**：针对部门负责人及其他被考核人员的定期考核，由中心领导按配置权重打分，多位领导加权累加汇总。
2. **年度目标管理绩效评审**：按参评项目进行评审，专家在线查看项目 PDF 申报材料及附件并按指标评分，系统按专家权重加权平均汇总得分与排名，结果仅管理员可见。

经与业务方全面确认，系统规则明确如下：
- **无评语**：完全取消评语输入，流程极致精简。
- **打分精度**：评分支持保留 4 位小数。
- **角色体系**：基于 RBAC（管理员 ADMIN、中心领导 LEADER、评审专家 EXPERT、员工 EMPLOYEE），支持一人多角色切换；不设立独立的“项目负责人”角色（仅作为项目基本信息文本字段展示）。
- **查看权限**：被考核人可查看本人最终总分及部门内全员排名（不可见指标细项得分）；年度项目评审结果与排名仅管理员可见。
- **提交与撤回**：截止时间前允许自主撤回修改；逾期未正式提交一律按 0 分计入。
- **材料上传与导出**：项目材料和附件 PDF 逐项上传至受控本地目录并支持在线预览；报表统一导出为标准 Excel (.xlsx)。

## Goals / Non-Goals

**Goals:**
- 提供轻量、稳健、快速的前后端分离架构（Spring Boot 3 + React 18 + MySQL 8.0 / H2）。
- 支持多角色权限控制与顶部工作台身份切换。
- 支持月度考核的批次管理、指标模板加载、领导权重配置、多领导打分（暂存/提交/撤回）和加权汇总排名。
- 支持年度目标项目评审的参评项目管理、PDF 材料上传、专家权重配置、专家在线分屏预览材料并即时打分（暂存/提交/撤回）、加权平均计算及管理员专属汇总排名导出。
- 提供指标配置与 100 分满分校验，提供考核结果全量查询与 Excel 导出。

**Non-Goals:**
- 无需评语功能，不设任何评语字段与输入框。
- 不为项目负责人设立独立业务角色（仅作为项目信息文本录入）。
- 不对普通员工或专家公开年度项目综合排名（仅限管理员可见）。
- 不引入外部云端对象存储（OSS），项目 PDF 材料保存在服务器本地受控目录并通过权限流式接口预览。

## Decisions

### 1. 技术栈选型
- **后端**：Java 17 / Spring Boot 3 + MyBatis-Plus + JWT + EasyExcel + Spring Validation。
- **前端**：React 18 + Vite + Ant Design 5 + React Router 6 + Axios。
- **数据库**：MySQL 8.0（生产模式）/ H2 兼容模式（开发即用体验）。

### 2. 数据模型设计

```sql
-- 用户与权限
sys_user (id, username, password_hash, real_name, department, position, email, phone, status, created_at, updated_at)
sys_role (id, role_code, role_name, description) -- ADMIN, LEADER, EXPERT, EMPLOYEE
sys_user_role (id, user_id, role_id)

-- 考核批次与任务
appraisal_batch (
    id, batch_name, batch_type, appraisal_period, 
    start_time, end_time, status, created_by, created_at, updated_at
) -- batch_type: MONTHLY, ANNUAL_PROJECT; status: DRAFT, IN_PROGRESS, COMPLETED

-- 批次考核指标项
appraisal_metric (
    id, batch_id, metric_name, metric_desc, max_score, weight, sort_order
)

-- 月度考核配置与打分
monthly_evaluator (
    id, batch_id, leader_user_id, weight -- 领导个人权重，例如 0.5000
)
monthly_examinee (
    id, batch_id, examinee_user_id, department, position
)
monthly_score_record (
    id, batch_id, leader_user_id, examinee_user_id, 
    total_score, status, submitted_at -- status: DRAFT, SUBMITTED (支持截止前撤回为DRAFT)
)
monthly_score_detail (
    id, record_id, metric_id, score -- DECIMAL(10, 4)
)

-- 年度项目评审配置与打分
project_item (
    id, batch_id, project_code, project_name, 
    owner_name, material_pdf_name, material_pdf_path, attachment_pdf_name, attachment_pdf_path, sort_order, created_at
)
project_expert (
    id, batch_id, expert_user_id, weight -- 专家个人权重，例如 0.2000
)
project_score_record (
    id, batch_id, expert_user_id, project_id, 
    total_score, status, submitted_at -- status: DRAFT, SUBMITTED (支持截止前撤回为DRAFT)
)
project_score_detail (
    id, record_id, metric_id, score -- DECIMAL(10, 4)
)
```

### 3. 核心计算规则决策
- **月度绩效**：
  - 各领导独立评分（各项得分支持 4 位小数）。
  - 最终得分按各领导权重加权累加：$\text{个人总分} = \sum(\text{领导得分} \times \text{该领导权重})$。
  - 若截止后领导仍未提交，该领导打分按 0 分计入汇总。
- **年度目标管理绩效**：
  - 各专家按权重独立评分，支持 4 位小数。
  - 最终项目得分按专家权重加权平均计算：
    $$\text{项目得分} = \frac{\sum(\text{专家得分} \times \text{该专家权重})}{\sum \text{专家权重}}$$
  - 逾期未提交的专家打分按 0 分计入。
  - 汇总结果、专家打分明细及排名仅管理员有权限查看和导出。

### 4. 界面交互与 PDF 分屏查阅
- **专家评审工作台**：
  - 左右分屏布局：左侧/上侧直接嵌入 PDF 阅读器预览项目申报材料与附件；右侧直接填写各指标分值，实时展示总分（精确到 4 位小数）。
  - 提交后在截止时间前显示“撤回修改”按钮；超过截止时间后锁定只读。
- **月度被考核人界面**：
  - 展示个人最终综合总分与部门全员排名，隐藏指标明细与领导个人信息。

## Risks / Trade-offs

- **[PDF 文件流式传输与跨浏览器适配]** → *Mitigation*：服务端统一定义受控存储目录 `./data/uploads/projects/`，提供带 Token 鉴权的流式接口 `/api/projects/{id}/pdf/{type}`，前端使用原生 iframe/object 结合现代浏览器 PDF 内核直接流畅渲染。
- **[截止时间与撤回状态并发一致性]** → *Mitigation*：服务端在每一次提交、暂存和撤回操作时严格校验 `batch.end_time > now()`，后端统一拦截逾期写操作，保证状态严密一致。
