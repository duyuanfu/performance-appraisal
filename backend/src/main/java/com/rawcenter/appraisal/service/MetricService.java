package com.rawcenter.appraisal.service;

import com.rawcenter.appraisal.dto.MetricDTO;
import com.rawcenter.appraisal.entity.AppraisalMetric;

import java.util.List;

public interface MetricService {
    List<AppraisalMetric> listMetricsByBatchId(Long batchId);
    void saveBatchMetrics(Long batchId, List<MetricDTO> metricDTOs);
    List<MetricDTO> getDefaultTemplates(String batchType);
}
