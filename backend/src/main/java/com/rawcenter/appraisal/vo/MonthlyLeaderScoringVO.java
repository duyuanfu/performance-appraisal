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
        private Map<Long, BigDecimal> metricScores; // metricId -> score
        private BigDecimal totalScore;
    }
}
