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
public class MonthlyLeaderScoringVO {
    private Long batchId;
    private String batchName;
    private String appraisalPeriod;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Boolean isExpired;
    private String batchStatus; // DRAFT, IN_PROGRESS, COMPLETED
    private String mySubmissionStatus; // DRAFT, SUBMITTED
    private List<AppraisalMetric> metrics;
    private List<ExamineeScoringRowVO> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExamineeScoringRowVO {
        private Long examineeUserId;
        private String realName;
        private String department;
        private String position;
        private String positionCategory; // DEPT_HEAD, DEPT_DEPUTY
        private String evaluatorRelationship; // 中心主要领导, 分管领导, 其他中心领导, 本部门正职, 部门正职互评, 部门副职互评
        private Map<Long, BigDecimal> metricScores; // metricId -> score
        private BigDecimal totalScore;
    }
}
