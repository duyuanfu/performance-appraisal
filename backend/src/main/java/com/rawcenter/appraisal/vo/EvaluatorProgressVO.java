package com.rawcenter.appraisal.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluatorProgressVO {
    private Long userId;
    private String realName;
    private String department;
    private String position;
    private String phone;
    private BigDecimal weight;
    private String status; // NOT_STARTED, DRAFT, SUBMITTED
    private Integer scoredCount; // 已打分数量
    private Integer totalTargetCount; // 需打分总数量

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;
}
