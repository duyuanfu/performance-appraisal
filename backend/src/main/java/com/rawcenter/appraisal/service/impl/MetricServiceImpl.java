package com.rawcenter.appraisal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rawcenter.appraisal.common.BusinessException;
import com.rawcenter.appraisal.config.DefaultMetricsProperties;
import com.rawcenter.appraisal.dto.MetricDTO;
import com.rawcenter.appraisal.entity.AppraisalMetric;
import com.rawcenter.appraisal.mapper.AppraisalMetricMapper;
import com.rawcenter.appraisal.service.MetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private final AppraisalMetricMapper metricMapper;
    private final DefaultMetricsProperties defaultMetricsProperties;

    @Override
    public List<AppraisalMetric> listMetricsByBatchId(Long batchId) {
        return metricMapper.selectList(new LambdaQueryWrapper<AppraisalMetric>()
                .eq(AppraisalMetric::getBatchId, batchId)
                .orderByAsc(AppraisalMetric::getSortOrder)
                .orderByAsc(AppraisalMetric::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatchMetrics(Long batchId, List<MetricDTO> metricDTOs) {
        if (CollectionUtils.isEmpty(metricDTOs)) {
            throw new BusinessException(400, "指标列表不能为空，请至少配置一项考核指标");
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        for (MetricDTO dto : metricDTOs) {
            if (dto.getWeight() == null || dto.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "指标【" + dto.getMetricName() + "】权重必须大于0");
            }
            totalWeight = totalWeight.add(dto.getWeight());
        }

        // Validate that total weight equals 100 (precision tolerance < 0.0001)
        BigDecimal diff = totalWeight.subtract(new BigDecimal("100.0000")).abs();
        if (diff.compareTo(new BigDecimal("0.0001")) > 0) {
            throw new BusinessException(400, "所有指标权重之和必须等于100分，当前合计为：" + totalWeight.stripTrailingZeros().toPlainString() + "分");
        }

        // Clean existing metrics for this batch
        metricMapper.delete(new LambdaQueryWrapper<AppraisalMetric>()
                .eq(AppraisalMetric::getBatchId, batchId));

        int order = 1;
        for (MetricDTO dto : metricDTOs) {
            AppraisalMetric entity = AppraisalMetric.builder()
                    .batchId(batchId)
                    .metricName(dto.getMetricName())
                    .metricDesc(dto.getMetricDesc() != null ? dto.getMetricDesc() : "")
                    .maxScore(dto.getMaxScore() != null ? dto.getMaxScore() : new BigDecimal("100.0000"))
                    .weight(dto.getWeight())
                    .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : order++)
                    .build();
            metricMapper.insert(entity);
        }
    }

    @Override
    public List<MetricDTO> getDefaultTemplates(String batchType) {
        List<DefaultMetricsProperties.MetricConfigItem> configItems =
                "MONTHLY".equalsIgnoreCase(batchType)
                        ? defaultMetricsProperties.getMonthly()
                        : defaultMetricsProperties.getAnnualProject();

        if (!CollectionUtils.isEmpty(configItems)) {
            return configItems.stream().map(item -> MetricDTO.builder()
                    .metricName(item.getMetricName())
                    .metricDesc(item.getMetricDesc())
                    .maxScore(item.getMaxScore())
                    .weight(item.getWeight())
                    .sortOrder(item.getSortOrder())
                    .build()).collect(Collectors.toList());
        }

        // 默认保底兜底模板（在未配置参数文件时生效）
        List<MetricDTO> templates = new ArrayList<>();
        if ("MONTHLY".equalsIgnoreCase(batchType)) {
            templates.add(MetricDTO.builder().metricName("工作能力").metricDesc("专业业务技能与履职综合能力").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("25.0000")).sortOrder(1).build());
            templates.add(MetricDTO.builder().metricName("工作质量").metricDesc("工作成果质量、合规与标准执行").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("25.0000")).sortOrder(2).build());
            templates.add(MetricDTO.builder().metricName("工作态度").metricDesc("履职态度、敬业精神与团队协作意识").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("20.0000")).sortOrder(3).build());
            templates.add(MetricDTO.builder().metricName("工作效率").metricDesc("任务推进时效性与响应解决速度").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("15.0000")).sortOrder(4).build());
            templates.add(MetricDTO.builder().metricName("工作业务量").metricDesc("承担重点工作负荷及实际业务产出量").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("15.0000")).sortOrder(5).build());
        } else {
            // ANNUAL_PROJECT
            templates.add(MetricDTO.builder().metricName("完成度").metricDesc("项目目标达成与实际里程碑交付情况").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("20.0000")).sortOrder(1).build());
            templates.add(MetricDTO.builder().metricName("质效").metricDesc("项目产出质量标准与业务赋能效益").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("20.0000")).sortOrder(2).build());
            templates.add(MetricDTO.builder().metricName("协作").metricDesc("跨部门横向协同配合与资源整合度").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("20.0000")).sortOrder(3).build());
            templates.add(MetricDTO.builder().metricName("效率").metricDesc("进度推进节奏与关键节点把控效率").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("20.0000")).sortOrder(4).build());
            templates.add(MetricDTO.builder().metricName("合规").metricDesc("合规管理、规范运作与风险把控").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("20.0000")).sortOrder(5).build());
        }
        return templates;
    }
}
