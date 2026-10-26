package com.rawcenter.appraisal.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectItemVO {
    private Long id;
    private Long batchId;
    private String projectCode;
    private String projectName;
    private Long ownerUserId;
    private String ownerName;
    private String category;
    private String department;
    private Long supervisorId;
    private String supervisorName;
    private String materialPdfName;
    private Boolean hasMaterial;
    private String attachmentPdfName;
    private Boolean hasAttachment;
    private Integer sortOrder;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
