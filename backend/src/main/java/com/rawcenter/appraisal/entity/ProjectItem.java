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
    private String materialPdfName;
    private String materialPdfPath;
    private String attachmentPdfName;
    private String attachmentPdfPath;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
