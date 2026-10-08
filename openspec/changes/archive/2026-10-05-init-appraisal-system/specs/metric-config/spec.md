## ADDED Requirements

### Requirement: Preset default evaluation metric templates
The system SHALL provide default metrics and weights for monthly employee performance appraisal and annual project appraisal.

#### Scenario: Load default monthly appraisal metrics
- **WHEN** an administrator creates a new monthly appraisal batch and chooses to use default metrics
- **THEN** system populates default metrics: 工作能力 (25分), 工作质量 (25分), 工作态度 (20分), 工作效率 (15分), 工作业务量 (15分), summing to 100 points

#### Scenario: Load default annual project appraisal metrics
- **WHEN** an administrator creates a new annual project appraisal batch and chooses to use default metrics
- **THEN** system populates default metrics: 完成度 (20分), 质效 (20分), 协作 (20分), 效率 (20分), 合规 (20分), summing to 100 points

### Requirement: Customizable metrics and weight validation
The system SHALL allow administrators to add, edit, reorder, and remove metrics for any appraisal batch, and validate that total weights sum to 100 points before saving.

#### Scenario: Save valid custom metric configuration
- **WHEN** administrator defines metric names, descriptions, max scores, and weights that sum to exactly 100
- **THEN** system saves the metric configuration for the appraisal batch

#### Scenario: Reject metric configuration with mismatched total weight
- **WHEN** administrator submits a metric configuration whose total weight does not equal 100
- **THEN** system rejects the submission with a clear validation error indicating the current sum
