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
@TableName("project_expert")
public class ProjectExpert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long expertUserId;
    private BigDecimal weight; // Expert weight (e.g., 20.0000)
}
