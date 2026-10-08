package com.rawcenter.appraisal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricDTO {
    private Long id;
    private Long batchId;

    @NotBlank(message = "指标名称不能为空")
    private String metricName;

    private String metricDesc;

    @NotNull(message = "指标满分分值不能为空")
    @DecimalMin(value = "0.0001", message = "满分分值必须大于0")
    private BigDecimal maxScore;

    @NotNull(message = "指标权重不能为空")
    @DecimalMin(value = "0.0001", message = "指标权重必须大于0")
    private BigDecimal weight;

    private Integer sortOrder;
}
