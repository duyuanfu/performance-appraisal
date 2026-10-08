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
public class MonthlyBatchVO {
    private Long id;
    private String batchName;
    private String batchType;
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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
