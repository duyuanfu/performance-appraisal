## ADDED Requirements

### Requirement: Annual project appraisal batch and candidate projects management
The system SHALL allow administrators to create annual project appraisal batches, manage candidate projects, record project owner name as a text field, upload project PDF materials and attachments, and assign review experts with individual weights.

#### Scenario: Administrator creates project batch and adds candidate project
- **WHEN** administrator creates a batch, enters project name, inputs project owner name, and uploads project PDF material and attachment
- **THEN** system saves the candidate project and stores the PDF files in secure local storage

#### Scenario: Administrator assigns review experts with weights
- **WHEN** administrator selects review experts for the batch and configures weights for each expert
- **THEN** system assigns evaluation tasks to all selected experts with their configured weights and updates batch status to IN_PROGRESS upon publishing

### Requirement: Split-screen PDF preview and expert evaluation
The system SHALL provide review experts with an integrated evaluation interface featuring embedded online PDF document preview alongside metric rating inputs (supporting 4 decimal places), draft saving, submission, and self-withdrawal before deadline.

#### Scenario: Expert views project PDF and scores metrics
- **WHEN** an expert selects a candidate project in the evaluation interface
- **THEN** system renders the project PDF in the document viewer pane and displays metric inputs (完成度, 质效, 协作, 效率, 合规) in the scoring pane

#### Scenario: Expert withdraws submission before deadline
- **WHEN** an expert clicks "Withdraw" on a submitted appraisal before the batch end time
- **THEN** system changes the submission status back to DRAFT and enables score editing

#### Scenario: Expert attempts withdrawal after deadline
- **WHEN** an expert attempts to withdraw a submission after the batch end time
- **THEN** system denies the withdrawal request and maintains read-only status

### Requirement: Weighted score aggregation and ranking
The system SHALL automatically calculate each project's overall composite score across all assigned evaluating experts using expert weights, treating unsubmitted evaluations past deadline as 0 points, and produce project rankings.

#### Scenario: Automatic weighted average calculation
- **WHEN** the batch completes or administrator triggers summary
- **THEN** system computes each project's weighted average score `Sum(ExpertScore * ExpertWeight) / Sum(ExpertWeight)` with 4 decimal places precision and sorts projects by score descending

### Requirement: Admin-exclusive results inquiry and Excel export
The system SHALL restrict viewing and exporting of project appraisal composite scores and rankings exclusively to administrators.

#### Scenario: Administrator exports annual project appraisal report
- **WHEN** administrator clicks "Export" on an annual project batch
- **THEN** system exports an Excel file matching the project appraisal grid format with project names, owner names, expert scores, weighted totals, and rankings

#### Scenario: Non-admin user attempts to view project results
- **WHEN** a review expert or employee attempts to access project appraisal summary or ranking endpoints
- **THEN** system denies access with an authorization error
