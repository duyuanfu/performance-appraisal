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
@TableName("monthly_role_weight")
public class MonthlyRoleWeight {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private String roleCategory;
    private String roleName;
    private BigDecimal weight;
    private Integer sortOrder;
}
