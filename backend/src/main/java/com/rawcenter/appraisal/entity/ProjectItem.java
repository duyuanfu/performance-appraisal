package com.rawcenter.appraisal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
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
    private String category; // BUSINESS, MANAGEMENT
    private String department; // 部门名称：仅用于汇总排名展示，与系统用户部门无关
    private String materialPdfName;
    private String materialPdfPath;
    private String attachmentPdfName;
    private String attachmentPdfPath;
    private Integer sortOrder;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
