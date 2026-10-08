package com.rawcenter.appraisal.vo;

import com.rawcenter.appraisal.entity.AppraisalMetric;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlySummaryVO {
    private Long batchId;
    private String batchName;
    private String appraisalPeriod;
    private String status;
    private List<AppraisalMetric> metrics; // 指标项列表
    private List<LeaderHeaderVO> leaders;
    private List<SummaryRowVO> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaderHeaderVO {
        private Long leaderUserId;
        private String realName;
        private BigDecimal weight;
        private Boolean submitted;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryRowVO {
        private Long examineeUserId;
        private String realName;
        private String department;
        private String position;
        private Map<Long, BigDecimal> metricAvgScores; // metricId -> 该指标在各领导加权下的综合得分
        private Map<Long, BigDecimal> leaderScores; // leaderUserId -> total score
        private Map<String, BigDecimal> leaderMetricScores; // "leaderUserId_metricId" -> 单项指标得分
        private BigDecimal compositeScore;
        private Integer rank;
    }
}
