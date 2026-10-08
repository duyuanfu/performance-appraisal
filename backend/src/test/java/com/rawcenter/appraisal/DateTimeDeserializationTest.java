package com.rawcenter.appraisal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rawcenter.appraisal.config.JacksonConfig;
import com.rawcenter.appraisal.dto.MonthlyBatchSaveDTO;
import com.rawcenter.appraisal.dto.ProjectBatchSaveDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

public class DateTimeDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        JacksonConfig config = new JacksonConfig();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        config.jacksonCustomizer().customize(builder);
        this.objectMapper = builder.build();
    }

    @Test
    public void testMonthlyBatchJsonDeserialization() throws Exception {
        // Test standard "yyyy-MM-dd HH:mm:ss" string from frontend
        String json = "{\n" +
                "  \"batchName\": \"2026年10月部门负责人月度绩效考核\",\n" +
                "  \"appraisalPeriod\": \"2026-10\",\n" +
                "  \"startTime\": \"2026-10-05 20:06:46\",\n" +
                "  \"endTime\": \"2026-10-12 20:06:46\",\n" +
                "  \"leaders\": [{\"leaderUserId\": 2, \"weight\": 100.0}],\n" +
                "  \"examineeUserIds\": [7],\n" +
                "  \"metrics\": [{\"metricName\": \"工作能力\", \"maxScore\": 100, \"weight\": 100.0}]\n" +
                "}";

        MonthlyBatchSaveDTO dto = objectMapper.readValue(json, MonthlyBatchSaveDTO.class);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(LocalDateTime.of(2026, 10, 5, 20, 6, 46), dto.getStartTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 10, 12, 20, 6, 46), dto.getEndTime());
    }

    @Test
    public void testProjectBatchJsonDeserialization() throws Exception {
        String json = "{\n" +
                "  \"batchName\": \"2026年度目标管理绩效项目评审\",\n" +
                "  \"appraisalPeriod\": \"2026\",\n" +
                "  \"startTime\": \"2026-10-05 20:06:46\",\n" +
                "  \"endTime\": \"2026-10-19 20:06:46\",\n" +
                "  \"experts\": [{\"expertUserId\": 4, \"weight\": 1.0}],\n" +
                "  \"metrics\": [{\"metricName\": \"完成度\", \"maxScore\": 100, \"weight\": 100.0}]\n" +
                "}";

        ProjectBatchSaveDTO dto = objectMapper.readValue(json, ProjectBatchSaveDTO.class);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(LocalDateTime.of(2026, 10, 5, 20, 6, 46), dto.getStartTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 10, 19, 20, 6, 46), dto.getEndTime());
    }

    @Test
    public void testFlexibleIsoFormatDeserialization() throws Exception {
        // Also ensure ISO-8601 with 'T' still works smoothly
        String json = "{\n" +
                "  \"batchName\": \"ISO格式测试\",\n" +
                "  \"appraisalPeriod\": \"2026-10\",\n" +
                "  \"startTime\": \"2026-10-05T20:06:46\",\n" +
                "  \"endTime\": \"2026-10-12T20:06:46\",\n" +
                "  \"leaders\": [{\"leaderUserId\": 2, \"weight\": 100.0}],\n" +
                "  \"examineeUserIds\": [7],\n" +
                "  \"metrics\": [{\"metricName\": \"工作能力\", \"maxScore\": 100, \"weight\": 100.0}]\n" +
                "}";

        MonthlyBatchSaveDTO dto = objectMapper.readValue(json, MonthlyBatchSaveDTO.class);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(LocalDateTime.of(2026, 10, 5, 20, 6, 46), dto.getStartTime());
    }
}
