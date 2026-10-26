package com.rawcenter.appraisal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("appraisal_batch")
public class AppraisalBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchName;
    private String batchType; // MONTHLY, TARGET_PROJECT, SPECIAL_INNOVATION, ANNUAL_PROJECT
    private String monthlyCategory; // Monthly only: DEPT_HEAD, DEPT_DEPUTY
    private String projectCategory; // Project only: BUSINESS, MANAGEMENT, ALL
    private String appraisalPeriod; // e.g. 2026-10 or 2026

    // API compatibility: callers still read/write "category"; services map it to the
    // correct typed column according to batchType. It is never persisted directly.
    @TableField(exist = false)
    private String category;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private String status; // DRAFT, IN_PROGRESS, COMPLETED
    private Long createdBy;
    private Long updatedBy;

    // 非持久化统计字段（项目评审分类考评进度）
    @TableField(exist = false)
    private Integer totalEvaluatorCount;
    @TableField(exist = false)
    private Integer submittedEvaluatorCount;
    @TableField(exist = false)
    private Integer centerLeaderCount;
    @TableField(exist = false)
    private Integer submittedCenterLeaderCount;
    @TableField(exist = false)
    private Integer deptHeadCount;
    @TableField(exist = false)
    private Integer submittedDeptHeadCount;
    @TableField(exist = false)
    private Integer expertCount;
    @TableField(exist = false)
    private Integer submittedExpertCount;
    @TableField(exist = false)
    private List<ProjectRoleWeight> roleWeights;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
