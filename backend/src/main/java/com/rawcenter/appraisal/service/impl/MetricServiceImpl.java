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
        List<DefaultMetricsProperties.MetricConfigItem> configItems = null;
        if ("MONTHLY".equalsIgnoreCase(batchType)) {
            configItems = defaultMetricsProperties.getMonthly();
        } else if ("TARGET_PROJECT".equalsIgnoreCase(batchType)) {
            configItems = !CollectionUtils.isEmpty(defaultMetricsProperties.getTargetProject())
                    ? defaultMetricsProperties.getTargetProject()
                    : defaultMetricsProperties.getAnnualProject();
        } else if ("SPECIAL_INNOVATION".equalsIgnoreCase(batchType)) {
            configItems = defaultMetricsProperties.getSpecialInnovation();
        } else {
            // ANNUAL_PROJECT or others
            configItems = defaultMetricsProperties.getAnnualProject();
        }

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
            templates.add(MetricDTO.builder().metricName("职责履行").metricDesc("岗位职责履行与工作要求执行情况").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("30.0000")).sortOrder(1).build());
            templates.add(MetricDTO.builder().metricName("主要业绩").metricDesc("月度重点业绩产出与实际工作成效").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("50.0000")).sortOrder(2).build());
            templates.add(MetricDTO.builder().metricName("团队建设").metricDesc("团队协作配合、作风建设与组织执行力").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("20.0000")).sortOrder(3).build());
        } else if ("SPECIAL_INNOVATION".equalsIgnoreCase(batchType)) {
            templates.add(MetricDTO.builder().metricName("特色亮点").metricDesc("项目创新性、特色做法与突破亮点").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("30.0000")).sortOrder(1).build());
            templates.add(MetricDTO.builder().metricName("产生质效").metricDesc("实际业务成效、管理提升或经济技术效益").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("50.0000")).sortOrder(2).build());
            templates.add(MetricDTO.builder().metricName("难度系数").metricDesc("技术攻坚难度、协调复杂度与创新风险度").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("20.0000")).sortOrder(3).build());
        } else {
            // TARGET_PROJECT & ANNUAL_PROJECT
            templates.add(MetricDTO.builder().metricName("完成度").metricDesc("项目目标达成与实际任务完成程度").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("30.0000")).sortOrder(1).build());
            templates.add(MetricDTO.builder().metricName("完成质量").metricDesc("项目产出质量、标准达成及业务赋能实效").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("50.0000")).sortOrder(2).build());
            templates.add(MetricDTO.builder().metricName("完成时效性").metricDesc("推进节奏、时间节点把控及完成时效").maxScore(new BigDecimal("100.0000")).weight(new BigDecimal("20.0000")).sortOrder(3).build());
        }
        return templates;
    }
}
