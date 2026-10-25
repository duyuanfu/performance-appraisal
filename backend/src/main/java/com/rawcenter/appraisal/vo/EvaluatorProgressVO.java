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
    private String evaluatorRoleType; // PRIMARY_LEADER, CENTER_LEADER, DEPT_HEAD, DEPT_HEAD_PEER, DEPT_DEPUTY_PEER
    private String evaluatorRoleName; // 中心主要领导, 中心领导, 本部门正职, 部门正职互评, 部门副职互评
    private String targetDesc; // 打分对象简述
    private String status; // NOT_STARTED, DRAFT, SUBMITTED
    private Integer scoredCount; // 已打分数量
    private Integer totalTargetCount; // 需打分总数量

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;
}
