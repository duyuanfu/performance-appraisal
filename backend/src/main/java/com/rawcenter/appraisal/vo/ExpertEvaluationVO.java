package com.rawcenter.appraisal.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.rawcenter.appraisal.entity.AppraisalMetric;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertEvaluationVO {
    private Long batchId;
    private String batchName;
    private String appraisalPeriod;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Boolean isExpired;
    private String batchStatus; // DRAFT, IN_PROGRESS, COMPLETED
    private String mySubmissionStatus; // DRAFT, SUBMITTED
    private List<AppraisalMetric> metrics;
    private List<ProjectEvaluationRowVO> projects;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectEvaluationRowVO {
        private Long projectId;
        private String projectCode;
        private String projectName;
        private String ownerName;
        private Long supervisorId;
        private String supervisorName;
        private String myRoleName; // 我在本项目中的考评主体角色（如：分管领导、其他中心领导）
        private Boolean isSupervisor; // 当前用户是否为该项目的分管领导
        private String materialPdfName;
        private Boolean hasMaterial;
        private String attachmentPdfName;
        private Boolean hasAttachment;
        private Map<Long, BigDecimal> metricScores; // metricId -> score
        private BigDecimal totalScore;
    }
}
