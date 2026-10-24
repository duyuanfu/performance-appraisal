package com.rawcenter.appraisal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.default-metrics")
public class DefaultMetricsProperties {

    private List<MetricConfigItem> monthly = new ArrayList<>();
    private List<MetricConfigItem> targetProject = new ArrayList<>();
    private List<MetricConfigItem> specialInnovation = new ArrayList<>();
    private List<MetricConfigItem> annualProject = new ArrayList<>();

    @Data
    public static class MetricConfigItem {
        private String metricName;
        private String metricDesc;
        private BigDecimal maxScore;
        private BigDecimal weight;
        private Integer sortOrder;
    }
}
