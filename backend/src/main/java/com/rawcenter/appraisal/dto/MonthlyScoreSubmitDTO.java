package com.rawcenter.appraisal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class MonthlyScoreSubmitDTO {
    @NotNull(message = "考核批次ID不能为空")
    private Long batchId;

    private List<MonthlyScoreItemDTO> items;

    private Boolean isSubmit; // true: submit; false: draft

    @Data
    public static class MonthlyScoreItemDTO {
        private Long examineeUserId;
        private Map<Long, BigDecimal> metricScores; // metricId -> score
    }
}
