package com.rawcenter.appraisal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProjectBatchSaveDTO {
    private Long id;

    @NotBlank(message = "评审批次名称不能为空")
    private String batchName;

    private String batchType; // TARGET_PROJECT, SPECIAL_INNOVATION, ANNUAL_PROJECT
    private String category; // BUSINESS, MANAGEMENT, ALL

    @NotBlank(message = "评审年度周期不能为空")
    private String appraisalPeriod; // e.g. 2026

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private List<MonthlyBatchSaveDTO.RoleWeightDTO> roleWeights;

    private List<ExpertWeightDTO> experts;

    @NotEmpty(message = "请配置评审指标")
    private List<MetricDTO> metrics;

    @Data
    public static class ExpertWeightDTO {
        private Long expertUserId;
        private BigDecimal weight;
    }
}
