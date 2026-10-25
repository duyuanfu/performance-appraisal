package com.rawcenter.appraisal.vo;

import com.rawcenter.appraisal.dto.ProjectBatchSaveDTO;
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
public class AnnualProjectSummaryVO {
    private Long batchId;
    private String batchName;
    private String batchType;
    private String appraisalPeriod;
    private String status;
    private BigDecimal averageScore;
    private List<AppraisalMetric> metrics;
    private List<ExpertHeaderVO> experts;
    private List<ProjectBatchSaveDTO.RoleWeightDTO> roleWeights;
    private List<ProjectSummaryRowVO> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpertHeaderVO {
        private Long expertUserId;
        private String realName;
        private BigDecimal weight;
        private Boolean submitted;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectSummaryRowVO {
        private Long projectId;
        private String projectCode;
        private String projectName;
        private String ownerName;
        private String category; // BUSINESS, MANAGEMENT
        private String department; // 申报部门
        private BigDecimal primaryLeaderScore; // 中心主要领导评分
        private BigDecimal supervisorLeaderScore; // 分管领导评分
        private BigDecimal otherLeaderScore; // 其他中心领导均分
        private BigDecimal headScore; // 正职评分均分
        private BigDecimal expertScore; // 代表评分（专家评分）
        private BigDecimal scoreCoefficient; // 得分系数
        private BigDecimal bonusAmount; // 核定奖励金额 (万元)
        private String awardTier; // SPECIAL, FIRST, SECOND, THIRD, NONE
        private String awardTierName; // 特等奖, 一等奖, 二等奖, 三等奖
        private Map<Long, BigDecimal> metricAvgScores; // metricId -> average score
        private Map<Long, BigDecimal> expertScores; // expertUserId -> total score
        private BigDecimal compositeScore;
        private Integer rank;
    }
}
