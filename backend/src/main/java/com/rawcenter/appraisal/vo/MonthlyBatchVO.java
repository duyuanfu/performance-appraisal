package com.rawcenter.appraisal.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import com.rawcenter.appraisal.dto.MonthlyBatchSaveDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyBatchVO {
    private Long id;
    private String batchName;
    private String batchType;
    private String category; // DEPT_HEAD, DEPT_DEPUTY
    private String appraisalPeriod;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private String status; // DRAFT, IN_PROGRESS, COMPLETED
    private Integer leaderCount;
    private Integer examineeCount;
    private Integer submittedLeaderCount;
    private String mySubmissionStatus; // For current leader: NOT_STARTED, DRAFT, SUBMITTED
    private Boolean isExpired;
    private List<MonthlyBatchSaveDTO.RoleWeightDTO> roleWeights;

    // 分类评分进度字段
    private Integer centerLeaderCount;
    private Integer submittedCenterLeaderCount;
    private Integer peerCount;
    private Integer submittedPeerCount;
    private Integer deptHeadLeaderCount;
    private Integer submittedDeptHeadLeaderCount;
    private Integer totalEvaluatorCount;
    private Integer submittedEvaluatorCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
