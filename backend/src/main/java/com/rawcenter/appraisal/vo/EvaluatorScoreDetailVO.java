package com.rawcenter.appraisal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 汇总排名“查看明细”使用的单个评分人明细。
 * 同时适用于月度绩效考核与项目评审。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluatorScoreDetailVO {
    private Long evaluatorUserId;
    private String realName;
    private String roleType; // PRIMARY_LEADER, SUPERVISOR_LEADER, OTHER_LEADER, DEPT_HEAD, DEPT_HEAD_PEER, DEPT_DEPUTY_PEER, EXPERT
    private String roleName; // 中心主要领导、分管领导、其他中心领导、部门正职、部门正职互评、部门副职互评、评审专家
    private String status; // SUBMITTED, DRAFT, NOT_STARTED
    private Map<Long, BigDecimal> metricScores; // metricId -> score
    private BigDecimal totalScore;
    private LocalDateTime submittedAt;
}
