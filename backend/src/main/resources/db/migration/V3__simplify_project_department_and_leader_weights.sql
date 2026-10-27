-- Project entry no longer stores an owner or a supervisor. The department name is
-- a free-text display value used only by the annual summary ranking.
UPDATE project_item
SET department = owner_name
WHERE (department IS NULL OR department = '')
  AND owner_name IS NOT NULL
  AND owner_name <> '';

UPDATE project_item SET department = '' WHERE department IS NULL;

ALTER TABLE project_item
    MODIFY COLUMN department VARCHAR(128) NOT NULL DEFAULT ''
        COMMENT '部门名称（仅用于汇总排名展示，与系统用户部门无关）';

ALTER TABLE project_item DROP FOREIGN KEY fk_project_item_owner;
ALTER TABLE project_item DROP INDEX idx_project_owner;
ALTER TABLE project_item DROP COLUMN owner_user_id;
ALTER TABLE project_item DROP COLUMN owner_name;

ALTER TABLE project_item DROP FOREIGN KEY fk_project_item_supervisor;
ALTER TABLE project_item DROP INDEX idx_project_supervisor;
ALTER TABLE project_item DROP COLUMN supervisor_id;

-- Center leaders are now split into only two groups: primary leader 30% and
-- other center leaders 70%. Existing batches are migrated to the same rule.
DELETE FROM project_role_weight WHERE role_category = 'SUPERVISOR_LEADER';
UPDATE project_role_weight
SET role_name = '中心主要领导', weight = 30.0000
WHERE role_category = 'PRIMARY_LEADER';
UPDATE project_role_weight
SET role_name = '其他中心领导', weight = 70.0000
WHERE role_category = 'OTHER_LEADER';
UPDATE project_score_record
SET evaluator_role_type = 'OTHER_LEADER'
WHERE evaluator_role_type = 'SUPERVISOR_LEADER';

ALTER TABLE project_role_weight DROP CHECK chk_project_role_category;
ALTER TABLE project_role_weight
    ADD CONSTRAINT chk_project_role_category
        CHECK (role_category IN ('CENTER_LEADER_TOTAL', 'PRIMARY_LEADER', 'OTHER_LEADER', 'DEPT_HEAD', 'EXPERT'));
