package com.rawcenter.appraisal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_role_weight")
public class ProjectRoleWeight {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private String roleCategory; // CENTER_LEADER_TOTAL, PRIMARY_LEADER, OTHER_LEADER, DEPT_HEAD, EXPERT
    private String roleName;
    private BigDecimal weight;
    private Integer sortOrder;
}
