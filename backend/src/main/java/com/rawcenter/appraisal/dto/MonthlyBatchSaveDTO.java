package com.rawcenter.appraisal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MonthlyBatchSaveDTO {
    private Long id;

    @NotBlank(message = "考核批次名称不能为空")
    private String batchName;

    private String category; // DEPT_HEAD (正职), DEPT_DEPUTY (副职)

    @NotBlank(message = "考核月份周期不能为空")
    private String appraisalPeriod; // e.g. 2026-10

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private List<LeaderWeightDTO> leaders;

    private List<RoleWeightDTO> roleWeights;

    private List<Long> examineeUserIds;

    @NotEmpty(message = "请配置考核指标")
    private List<MetricDTO> metrics;

    @Data
    public static class LeaderWeightDTO {
        private Long leaderUserId;
        private BigDecimal weight;
    }

    @Data
    public static class RoleWeightDTO {
        private String roleCategory; // PRIMARY_LEADER, SUPERVISOR_LEADER, OTHER_LEADER, DEPT_HEAD, PEER_HEAD, PEER_DEPUTY
        private String roleName;
        private BigDecimal weight;
        private Integer sortOrder;
    }
}
