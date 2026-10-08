# Spec: Monthly Performance Appraisal (monthly-appraisal)

## Purpose
支持原料中心月度部门负责人及其他被考核人员绩效考核全流程线上化管理。涵盖管理员考核任务发布、考核对象与评价领导配置、评价人权重设置、指标评分标准配置、中心领导逐项打分（支持暂存、提交与截止前自主撤回）、按领导权重加权汇总总分与排名，以及被考核人员个人总分及全员排名查询和管理员 Excel 导出归档。

## Requirements

### Requirement: Monthly appraisal batch creation and configuration
The system SHALL allow administrators to create a monthly appraisal batch, configure evaluation period, set scoring deadlines, select examinees, assign evaluating leaders, and configure evaluator weights (with 4-decimal precision).

#### Scenario: Administrator creates monthly batch
- **WHEN** administrator inputs batch name, appraisal period, selects leaders with individual weights, and selects examinees
- **THEN** system creates the monthly appraisal batch in DRAFT status

#### Scenario: Administrator publishes monthly batch
- **WHEN** administrator clicks publish on a fully configured monthly batch
- **THEN** system changes batch status to IN_PROGRESS and makes appraisal tasks available to evaluating leaders

### Requirement: Leader scoring with draft, submit, and self-withdrawal
The system SHALL allow evaluating leaders to rate each metric (supporting up to 4 decimal places), save drafts, submit finalized scores, and self-withdraw submissions before the deadline.

#### Scenario: Leader saves scoring draft
- **WHEN** a leader inputs metric scores for examinees and clicks "Save Draft"
- **THEN** system persists draft scores without finalizing, allowing future updates before the deadline

#### Scenario: Leader submits finalized scores
- **WHEN** a leader completes all required metric scores and clicks "Submit"
- **THEN** system marks the leader's submission as SUBMITTED and calculates the leader's total score for each examinee

#### Scenario: Leader withdraws submission before deadline
- **WHEN** a leader clicks "Withdraw" on a submitted appraisal before the batch end time
- **THEN** system changes the submission status back to DRAFT and enables score editing

#### Scenario: Leader attempts withdrawal after deadline
- **WHEN** a leader attempts to withdraw a submission after the batch end time
- **THEN** system denies the withdrawal request and maintains read-only status

### Requirement: Weighted score aggregation and ranking
The system SHALL calculate each examinee's composite score using leader weights and metric weights, treating unsubmitted evaluations past the deadline as 0 points, and generate ranking lists.

#### Scenario: Aggregate scores after leader submissions
- **WHEN** the batch completes or administrator triggers summary
- **THEN** system computes individual composite scores using formula `Sum(LeaderScore * LeaderWeight)` (accounting for overdue evaluations as 0) and ranks examinees in descending order

### Requirement: Results visibility and export
The system SHALL allow examinees to view their own final composite score and full department rankings (without displaying individual metric breakdowns or leader identities) and allow administrators to export results to Excel.

#### Scenario: Examinee views personal result and ranking
- **WHEN** an examinee logs into the system after administrator releases results
- **THEN** system displays the examinee's final composite score and department rank list without showing metric breakdown or evaluator names

#### Scenario: Administrator exports batch results
- **WHEN** administrator clicks "Export" on a monthly batch
- **THEN** system generates and downloads an Excel file containing examinee scores and rankings
