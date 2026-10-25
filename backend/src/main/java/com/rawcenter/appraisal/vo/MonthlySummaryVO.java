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
    private String category; // DEPT_HEAD, DEPT_DEPUTY
    private String appraisalPeriod;
    private String status;
    private List<AppraisalMetric> metrics; // 指标项列表
    private List<LeaderHeaderVO> leaders;
    private List<SummaryRowVO> rows;
    private List<TierSummaryRowVO> headRows; // 部门正职考评汇总列表
    private List<TierSummaryRowVO> deputyRows; // 部门副职考评汇总列表

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierSummaryRowVO {
        private Long examineeUserId;
        private String realName;
        private String department;
        private String position;
        private String positionCategory; // DEPT_HEAD, DEPT_DEPUTY
        private BigDecimal primaryLeaderScore; // 主要领导得分
        private BigDecimal supervisorLeaderScore; // 分管领导得分
        private BigDecimal otherLeaderScore; // 其他中心领导均分
        private BigDecimal deptHeadScore; // 直属部门正职领导得分 (副职专属)
        private BigDecimal peerScore; // 同级互评均分
        private BigDecimal compositeScore; // 综合得分
        private Integer rank; // 排名
    }

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
