package com.rawcenter.appraisal.controller;

import com.rawcenter.appraisal.common.Result;
import com.rawcenter.appraisal.dto.MetricDTO;
import com.rawcenter.appraisal.entity.AppraisalMetric;
import com.rawcenter.appraisal.service.MetricService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService;

    @GetMapping("/batches/{batchId}/metrics")
    public Result<List<AppraisalMetric>> listMetrics(@PathVariable Long batchId) {
        return Result.success(metricService.listMetricsByBatchId(batchId));
    }

    @PostMapping("/batches/{batchId}/metrics")
    public Result<Void> saveBatchMetrics(
            @PathVariable Long batchId,
            @Valid @RequestBody List<MetricDTO> metricDTOs) {
        metricService.saveBatchMetrics(batchId, metricDTOs);
        return Result.success();
    }

    @GetMapping("/metrics/templates")
    public Result<List<MetricDTO>> getDefaultTemplates(@RequestParam(defaultValue = "MONTHLY") String batchType) {
        return Result.success(metricService.getDefaultTemplates(batchType));
    }
}
