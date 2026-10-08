package com.rawcenter.appraisal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ExpertScoreSubmitDTO {
    @NotNull(message = "评审批次ID不能为空")
    private Long batchId;

    private List<ProjectScoreItemDTO> items;

    private Boolean isSubmit; // true: submit; false: draft

    @Data
    public static class ProjectScoreItemDTO {
        private Long projectId;
        private Map<Long, BigDecimal> metricScores; // metricId -> score
    }
}
