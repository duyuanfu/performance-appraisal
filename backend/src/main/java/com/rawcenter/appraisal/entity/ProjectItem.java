package com.rawcenter.appraisal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_item")
public class ProjectItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private String projectCode;
    private String projectName;
    private String ownerName; // Text field, not a role
    private String category; // BUSINESS (业务类), MANAGEMENT (管理类)
    private String department; // 申报部门
    private Long supervisorId; // 分管领导用户ID
    private String awardTier; // SPECIAL, FIRST, SECOND, THIRD, NONE
    private java.math.BigDecimal scoreCoefficient; // 得分系数
    private java.math.BigDecimal bonusAmount; // 核定奖励金额 (万元)
    private Integer rankNum; // 排序
    private String materialPdfName;
    private String materialPdfPath;
    private String attachmentPdfName;
    private String attachmentPdfPath;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
