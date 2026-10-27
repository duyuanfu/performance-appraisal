package com.rawcenter.appraisal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rawcenter.appraisal.common.BusinessException;
import com.rawcenter.appraisal.common.UserContext;
import com.rawcenter.appraisal.dto.*;
import com.rawcenter.appraisal.entity.AppraisalBatch;
import com.rawcenter.appraisal.entity.AppraisalMetric;
import com.rawcenter.appraisal.mapper.AppraisalBatchMapper;
import com.rawcenter.appraisal.service.MetricService;
import com.rawcenter.appraisal.service.MonthlyAppraisalService;
import com.rawcenter.appraisal.service.ProjectAppraisalService;
import com.rawcenter.appraisal.service.UserService;
import com.rawcenter.appraisal.vo.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootTest
public class AppraisalFlowTest {

    @Autowired
    private UserService userService;

    @Autowired
    private MetricService metricService;

    @Autowired
    private MonthlyAppraisalService monthlyService;

    @Autowired
    private ProjectAppraisalService projectService;

    @Autowired
    private AppraisalBatchMapper batchMapper;

    @BeforeEach
    public void cleanupTestBatches() {
        batchMapper.delete(new LambdaQueryWrapper<AppraisalBatch>()
                .in(AppraisalBatch::getAppraisalPeriod, Arrays.asList("2026-10", "2026")));
    }

    @Test
    public void testUserAuthAndMultiRole() {
        // 1. Test Admin login
        LoginRequest adminReq = new LoginRequest();
        adminReq.setUsername("admin");
        adminReq.setPassword("password123");
        LoginResponse adminResp = userService.login(adminReq);
        Assertions.assertNotNull(adminResp.getToken());
        Assertions.assertEquals("ADMIN", adminResp.getActiveRole());

        // 2. Test User login for 王昆淼 (13987184673, DEPT_HEAD only, no EMPLOYEE role)
        LoginRequest wangReq = new LoginRequest();
        wangReq.setUsername("13987184673");
        wangReq.setPassword("password123");
        LoginResponse wangResp = userService.login(wangReq);
        Assertions.assertNotNull(wangResp.getToken());
        boolean hasDeptHead = wangResp.getRoles().stream().anyMatch(r -> "DEPT_HEAD".equals(r.getRoleCode()));
        boolean hasEmployee = wangResp.getRoles().stream().anyMatch(r -> "EMPLOYEE".equals(r.getRoleCode()));
        Assertions.assertTrue(hasDeptHead, "王昆淼 13987184673 应该拥有 DEPT_HEAD 角色");
        Assertions.assertFalse(hasEmployee, "王昆淼 13987184673 不应有 EMPLOYEE (普通职工) 身份");
    }

    @Test
    public void testMonthlyAppraisalFullFlow() {
        // Set UserContext to Admin (userId = 1)
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(1L)
                .username("admin")
                .realName("业务管理员")
                .activeRole("ADMIN")
                .build());

        // 1. Create Monthly Batch (部门正职月度考评)
        MonthlyBatchSaveDTO batchDto = new MonthlyBatchSaveDTO();
        batchDto.setBatchName("2026年10月内设部门正职绩效考核测试");
        batchDto.setCategory("DEPT_HEAD");
        batchDto.setAppraisalPeriod("2026-10");
        batchDto.setStartTime(LocalDateTime.now().minusHours(1));
        batchDto.setEndTime(LocalDateTime.now().plusDays(7));

        // Leaders: 陈剑 (id=3, weight=50%), 施文萃 (id=4, weight=50%)
        MonthlyBatchSaveDTO.LeaderWeightDTO l1 = new MonthlyBatchSaveDTO.LeaderWeightDTO();
        l1.setLeaderUserId(3L);
        l1.setWeight(new BigDecimal("50.0000"));
        MonthlyBatchSaveDTO.LeaderWeightDTO l2 = new MonthlyBatchSaveDTO.LeaderWeightDTO();
        l2.setLeaderUserId(4L);
        l2.setWeight(new BigDecimal("50.0000"));
        batchDto.setLeaders(Arrays.asList(l1, l2));

        // Examinees: 王昆淼 (id=9), 张玉丰 (id=10)
        batchDto.setExamineeUserIds(Arrays.asList(9L, 10L));

        // Default Metrics
        List<MetricDTO> metrics = metricService.getDefaultTemplates("MONTHLY");
        batchDto.setMetrics(metrics);

        monthlyService.saveMonthlyBatch(batchDto);

        List<MonthlyBatchVO> batches = monthlyService.listMonthlyBatches(null, "2026年10月内设部门正职绩效考核测试");
        Assertions.assertFalse(batches.isEmpty());
        Long batchId = batches.get(0).getId();

        // Test Batch Name Uniqueness
        MonthlyBatchSaveDTO dupDto = new MonthlyBatchSaveDTO();
        dupDto.setBatchName("2026年10月内设部门正职绩效考核测试"); // 重复名称
        dupDto.setCategory("DEPT_HEAD");
        dupDto.setAppraisalPeriod("2026-10");
        dupDto.setLeaders(Arrays.asList(l1, l2));
        dupDto.setExamineeUserIds(Arrays.asList(9L, 10L));
        dupDto.setMetrics(metrics);
        Assertions.assertThrows(BusinessException.class, () -> monthlyService.saveMonthlyBatch(dupDto));

        // 2. Publish Batch
        monthlyService.publishMonthlyBatch(batchId);

        // Test Leader Progress & Urge
        List<EvaluatorProgressVO> leaderProgress = monthlyService.getLeaderProgress(batchId);
        Assertions.assertEquals(6, leaderProgress.size(), "考评人应包含3位中心领导与全部3位部门正职互评人员");
        monthlyService.urgeLeader(batchId, 3L);

        // 3. Leader 1 (陈剑, id=3) Scores
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(3L)
                .username("13908866003")
                .realName("陈剑")
                .activeRole("LEADER")
                .build());

        MonthlyLeaderScoringVO leader1View = monthlyService.getLeaderScoringData(batchId);
        Assertions.assertEquals(2, leader1View.getRows().size());

        List<AppraisalMetric> metricList = leader1View.getMetrics();
        MonthlyScoreSubmitDTO submit1 = new MonthlyScoreSubmitDTO();
        submit1.setBatchId(batchId);
        submit1.setIsSubmit(true);

        List<MonthlyScoreSubmitDTO.MonthlyScoreItemDTO> items1 = new ArrayList<>();
        // Examinee 9 score 90 each metric
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO item1 = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        item1.setExamineeUserId(9L);
        Map<Long, BigDecimal> scores1 = new HashMap<>();
        for (AppraisalMetric m : metricList) {
            scores1.put(m.getId(), new BigDecimal("90.0000"));
        }
        item1.setMetricScores(scores1);
        items1.add(item1);

        // Examinee 10 score 80 each metric
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO item2 = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        item2.setExamineeUserId(10L);
        Map<Long, BigDecimal> scores2 = new HashMap<>();
        for (AppraisalMetric m : metricList) {
            scores2.put(m.getId(), new BigDecimal("80.0000"));
        }
        item2.setMetricScores(scores2);
        items1.add(item2);

        submit1.setItems(items1);
        monthlyService.saveLeaderScores(submit1);

        // Test self-withdraw before deadline
        monthlyService.withdrawLeaderScores(batchId);
        MonthlyLeaderScoringVO viewAfterWithdraw = monthlyService.getLeaderScoringData(batchId);
        Assertions.assertEquals("DRAFT", viewAfterWithdraw.getMySubmissionStatus());

        // Resubmit
        monthlyService.saveLeaderScores(submit1);

        // 4. Leader 2 (施文萃, id=4) Scores
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(4L)
                .username("13888952359")
                .realName("施文萃")
                .activeRole("LEADER")
                .build());

        MonthlyScoreSubmitDTO submit2 = new MonthlyScoreSubmitDTO();
        submit2.setBatchId(batchId);
        submit2.setIsSubmit(true);

        List<MonthlyScoreSubmitDTO.MonthlyScoreItemDTO> items2 = new ArrayList<>();
        // Examinee 9 score 100
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO item3 = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        item3.setExamineeUserId(9L);
        Map<Long, BigDecimal> scores3 = new HashMap<>();
        for (AppraisalMetric m : metricList) {
            scores3.put(m.getId(), new BigDecimal("100.0000"));
        }
        item3.setMetricScores(scores3);
        items2.add(item3);

        // Examinee 10 score 80
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO item4 = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        item4.setExamineeUserId(10L);
        Map<Long, BigDecimal> scores4 = new HashMap<>();
        for (AppraisalMetric m : metricList) {
            scores4.put(m.getId(), new BigDecimal("80.0000"));
        }
        item4.setMetricScores(scores4);
        items2.add(item4);

        submit2.setItems(items2);
        monthlyService.saveLeaderScores(submit2);

        // 5. Admin checks Summary
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(1L)
                .username("admin")
                .realName("业务管理员")
                .activeRole("ADMIN")
                .build());

        MonthlySummaryVO summary = monthlyService.getMonthlyBatchSummary(batchId);
        Assertions.assertEquals(2, summary.getRows().size());

        // Examinee 9 composite: 90 * 0.5 + 100 * 0.5 = 95.0000
        MonthlySummaryVO.SummaryRowVO row9 = summary.getRows().stream()
                .filter(r -> r.getExamineeUserId().equals(9L)).findFirst().orElseThrow();
        Assertions.assertEquals(new BigDecimal("95.0000"), row9.getCompositeScore());
        Assertions.assertEquals(1, row9.getRank());
        MonthlySummaryVO.TierSummaryRowVO headRow9 = summary.getHeadRows().stream()
                .filter(r -> r.getExamineeUserId().equals(9L)).findFirst().orElseThrow();
        Assertions.assertFalse(headRow9.getEvaluatorDetails().isEmpty(), "汇总明细应包含评分人打分明细");

        // Examinee 10 composite: 80 * 0.5 + 80 * 0.5 = 80.0000
        MonthlySummaryVO.SummaryRowVO row10 = summary.getRows().stream()
                .filter(r -> r.getExamineeUserId().equals(10L)).findFirst().orElseThrow();
        Assertions.assertEquals(new BigDecimal("80.0000"), row10.getCompositeScore());
        Assertions.assertEquals(2, row10.getRank());

        // 6. Test Excel Export
        byte[] excelBytes = monthlyService.exportMonthlyBatchExcel(batchId);
        Assertions.assertNotNull(excelBytes);
        Assertions.assertTrue(excelBytes.length > 0);

        // 7. Admin completes/archives the batch
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(1L)
                .username("admin")
                .realName("业务管理员")
                .activeRole("ADMIN")
                .build());
        monthlyService.completeMonthlyBatch(batchId);

        // 8. 普通职工不再能查看被考核记录或月度考核结果
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(9L)
                .username("13987184673")
                .realName("王昆淼")
                .activeRole("EMPLOYEE")
                .build());
        Assertions.assertTrue(monthlyService.listMonthlyBatches(null, null).isEmpty());
    }

    @Test
    public void testDepartmentHeadOnlyExamineeCannotSeeMonthlyBatch() {
        // 管理员创建一个仅有王昆淼(9)一人参评的部门正职批次
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(1L)
                .username("admin")
                .realName("业务管理员")
                .activeRole("ADMIN")
                .build());

        MonthlyBatchSaveDTO singleDto = new MonthlyBatchSaveDTO();
        singleDto.setBatchName("2026年10月正职单人参评可见性测试");
        singleDto.setCategory("DEPT_HEAD");
        singleDto.setAppraisalPeriod("2026-10");
        singleDto.setStartTime(LocalDateTime.now().minusHours(1));
        singleDto.setEndTime(LocalDateTime.now().plusDays(7));

        MonthlyBatchSaveDTO.LeaderWeightDTO l1 = new MonthlyBatchSaveDTO.LeaderWeightDTO();
        l1.setLeaderUserId(3L);
        l1.setWeight(new BigDecimal("50.0000"));
        MonthlyBatchSaveDTO.LeaderWeightDTO l2 = new MonthlyBatchSaveDTO.LeaderWeightDTO();
        l2.setLeaderUserId(4L);
        l2.setWeight(new BigDecimal("50.0000"));
        singleDto.setLeaders(Arrays.asList(l1, l2));
        singleDto.setExamineeUserIds(Collections.singletonList(9L));
        singleDto.setMetrics(metricService.getDefaultTemplates("MONTHLY"));

        monthlyService.saveMonthlyBatch(singleDto);
        Long singleBatchId = monthlyService.listMonthlyBatches(null, "2026年10月正职单人参评可见性测试")
                .get(0).getId();
        monthlyService.publishMonthlyBatch(singleBatchId);

        // 王昆淼仅是该批次被考核人，没有其他正职可互评，不应在互评批次列表中看到它
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(9L)
                .username("13987184673")
                .realName("王昆淼")
                .activeRole("DEPT_HEAD")
                .build());
        List<MonthlyBatchVO> visibleToExaminee = monthlyService.listMonthlyBatches(null, "2026年10月正职单人参评可见性测试");
        Assertions.assertTrue(visibleToExaminee.stream().noneMatch(b -> singleBatchId.equals(b.getId())),
                "仅作为被考核人的部门正职不应看到该月度互评批次");

        // 管理员再创建一个有两位正职参评的批次，王昆淼需要与另一位正职互评，应可见
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(1L)
                .username("admin")
                .realName("业务管理员")
                .activeRole("ADMIN")
                .build());
        MonthlyBatchSaveDTO peerDto = new MonthlyBatchSaveDTO();
        peerDto.setBatchName("2026年10月正职互评可见性测试");
        peerDto.setCategory("DEPT_HEAD");
        peerDto.setAppraisalPeriod("2026-10");
        peerDto.setStartTime(LocalDateTime.now().minusHours(1));
        peerDto.setEndTime(LocalDateTime.now().plusDays(7));
        peerDto.setLeaders(Arrays.asList(l1, l2));
        peerDto.setExamineeUserIds(Arrays.asList(9L, 10L));
        peerDto.setMetrics(metricService.getDefaultTemplates("MONTHLY"));

        monthlyService.saveMonthlyBatch(peerDto);
        Long peerBatchId = monthlyService.listMonthlyBatches(null, "2026年10月正职互评可见性测试")
                .get(0).getId();
        monthlyService.publishMonthlyBatch(peerBatchId);

        UserContext.set(UserContext.CurrentUser.builder()
                .userId(9L)
                .username("13987184673")
                .realName("王昆淼")
                .activeRole("DEPT_HEAD")
                .build());
        List<MonthlyBatchVO> visibleToPeer = monthlyService.listMonthlyBatches(null, "2026年10月正职互评可见性测试");
        Assertions.assertTrue(visibleToPeer.stream().anyMatch(b -> peerBatchId.equals(b.getId())),
                "作为部门正职且存在其他互评对象时，应能看到月度互评批次");
    }

    @Test
    public void testAnnualProjectAppraisalFullFlow() {
        // Set UserContext to Admin (userId = 1)
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(1L)
                .username("admin")
                .realName("系统管理员")
                .activeRole("ADMIN")
                .build());

        // 1. Create Annual Project Batch
        ProjectBatchSaveDTO batchDto = new ProjectBatchSaveDTO();
        batchDto.setBatchName("2026年度目标管理绩效项目评审测试");
        batchDto.setBatchType("TARGET_PROJECT");
        batchDto.setAppraisalPeriod("2026");
        batchDto.setStartTime(LocalDateTime.now().minusHours(1));
        batchDto.setEndTime(LocalDateTime.now().plusDays(10));

        // Experts: 徐济仓 (id=32, weight=50%), 刘海业 (id=33, weight=50%)
        ProjectBatchSaveDTO.ExpertWeightDTO e1 = new ProjectBatchSaveDTO.ExpertWeightDTO();
        e1.setExpertUserId(32L);
        e1.setWeight(new BigDecimal("50.0000"));
        ProjectBatchSaveDTO.ExpertWeightDTO e2 = new ProjectBatchSaveDTO.ExpertWeightDTO();
        e2.setExpertUserId(33L);
        e2.setWeight(new BigDecimal("50.0000"));
        batchDto.setExperts(Arrays.asList(e1, e2));

        // Metrics
        List<MetricDTO> metrics = metricService.getDefaultTemplates("ANNUAL_PROJECT");
        batchDto.setMetrics(metrics);

        projectService.saveProjectBatch(batchDto);

        List<AppraisalBatch> batches = projectService.listProjectBatches(null, "2026年度目标管理绩效项目评审测试");
        Assertions.assertFalse(batches.isEmpty());
        Long batchId = batches.get(0).getId();

        // 默认中心领导内部细分权重：主要领导 30% + 其他中心领导 70%，不再设置分管领导
        AppraisalBatch batchDetail = projectService.getProjectBatchDetail(batchId);
        Map<String, BigDecimal> roleWeightMap = batchDetail.getRoleWeights().stream()
                .collect(Collectors.toMap(rw -> rw.getRoleCategory(), rw -> rw.getWeight()));
        Assertions.assertEquals(new BigDecimal("30.0000"), roleWeightMap.get("PRIMARY_LEADER"));
        Assertions.assertEquals(new BigDecimal("70.0000"), roleWeightMap.get("OTHER_LEADER"));
        Assertions.assertFalse(roleWeightMap.containsKey("SUPERVISOR_LEADER"));

        // 验证批次名称唯一性：相同名称必须拦截抛出异常
        ProjectBatchSaveDTO dupNameDto = new ProjectBatchSaveDTO();
        dupNameDto.setBatchName("2026年度目标管理绩效项目评审测试"); // 重复名称
        dupNameDto.setAppraisalPeriod("2026");
        dupNameDto.setExperts(Arrays.asList(e1, e2));
        dupNameDto.setMetrics(metrics);
        Assertions.assertThrows(BusinessException.class, () -> projectService.saveProjectBatch(dupNameDto));

        // 验证取消一年一个限制：名称不同、相同年度能够成功创建
        ProjectBatchSaveDTO diffNameDto = new ProjectBatchSaveDTO();
        diffNameDto.setBatchName("2026年度专项项目评审测试"); // 不同名称，相同年度
        diffNameDto.setAppraisalPeriod("2026");
        diffNameDto.setExperts(Arrays.asList(e1, e2));
        diffNameDto.setMetrics(metrics);
        Assertions.assertDoesNotThrow(() -> projectService.saveProjectBatch(diffNameDto));

        // 2. Add Candidate Projects
        ProjectItemSaveDTO p1 = new ProjectItemSaveDTO();
        p1.setBatchId(batchId);
        p1.setProjectCode("1");
        p1.setProjectName("原料集采平台数字化升级项目");
        p1.setDepartment("原料部");
        p1.setSortOrder(1);
        projectService.saveProjectItem(p1);

        ProjectItemSaveDTO p2 = new ProjectItemSaveDTO();
        p2.setBatchId(batchId);
        p2.setProjectCode("2");
        p2.setProjectName("仓储物流智能调度系统开发项目");
        p2.setDepartment("物流部");
        p2.setSortOrder(2);
        projectService.saveProjectItem(p2);

        List<ProjectItemVO> projectList = projectService.listProjectItems(batchId);
        Assertions.assertEquals(2, projectList.size());
        Assertions.assertEquals("原料部", projectList.get(0).getDepartment());
        Long p1Id = projectList.get(0).getId();
        Long p2Id = projectList.get(1).getId();

        // 3. Publish Batch
        projectService.publishProjectBatch(batchId);

        // Test Expert Progress & Urge
        List<EvaluatorProgressVO> expertProgress = projectService.getExpertProgress(batchId);
        Assertions.assertEquals(8, expertProgress.size(), "项目评审全量考评人应包含3位中心领导+3位部门正职+2位评审专家");
        projectService.urgeExpert(batchId, 32L);

        // 4. Expert 1 (徐济仓, id=32) Scores
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(32L)
                .username("13085371368")
                .realName("徐济仓")
                .activeRole("EXPERT")
                .build());

        ExpertEvaluationVO expert1View = projectService.getExpertEvaluationData(batchId);
        Assertions.assertEquals(2, expert1View.getProjects().size());

        ExpertScoreSubmitDTO submit1 = new ExpertScoreSubmitDTO();
        submit1.setBatchId(batchId);
        submit1.setIsSubmit(true);

        List<ExpertScoreSubmitDTO.ProjectScoreItemDTO> items1 = new ArrayList<>();
        // P1 score 90
        ExpertScoreSubmitDTO.ProjectScoreItemDTO itemP1_1 = new ExpertScoreSubmitDTO.ProjectScoreItemDTO();
        itemP1_1.setProjectId(p1Id);
        Map<Long, BigDecimal> scoresP1_1 = new HashMap<>();
        for (AppraisalMetric m : expert1View.getMetrics()) {
            scoresP1_1.put(m.getId(), new BigDecimal("90.0000"));
        }
        itemP1_1.setMetricScores(scoresP1_1);
        items1.add(itemP1_1);

        // P2 score 80
        ExpertScoreSubmitDTO.ProjectScoreItemDTO itemP2_1 = new ExpertScoreSubmitDTO.ProjectScoreItemDTO();
        itemP2_1.setProjectId(p2Id);
        Map<Long, BigDecimal> scoresP2_1 = new HashMap<>();
        for (AppraisalMetric m : expert1View.getMetrics()) {
            scoresP2_1.put(m.getId(), new BigDecimal("80.0000"));
        }
        itemP2_1.setMetricScores(scoresP2_1);
        items1.add(itemP2_1);

        submit1.setItems(items1);
        projectService.saveExpertScores(submit1);

        // 5. Expert 2 (刘海业, id=33) Scores
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(33L)
                .username("17787141508")
                .realName("刘海业")
                .activeRole("EXPERT")
                .build());

        ExpertScoreSubmitDTO submit2 = new ExpertScoreSubmitDTO();
        submit2.setBatchId(batchId);
        submit2.setIsSubmit(true);

        List<ExpertScoreSubmitDTO.ProjectScoreItemDTO> items2 = new ArrayList<>();
        // P1 score 100
        ExpertScoreSubmitDTO.ProjectScoreItemDTO itemP1_2 = new ExpertScoreSubmitDTO.ProjectScoreItemDTO();
        itemP1_2.setProjectId(p1Id);
        Map<Long, BigDecimal> scoresP1_2 = new HashMap<>();
        for (AppraisalMetric m : expert1View.getMetrics()) {
            scoresP1_2.put(m.getId(), new BigDecimal("100.0000"));
        }
        itemP1_2.setMetricScores(scoresP1_2);
        items2.add(itemP1_2);

        // P2 score 80
        ExpertScoreSubmitDTO.ProjectScoreItemDTO itemP2_2 = new ExpertScoreSubmitDTO.ProjectScoreItemDTO();
        itemP2_2.setProjectId(p2Id);
        Map<Long, BigDecimal> scoresP2_2 = new HashMap<>();
        for (AppraisalMetric m : expert1View.getMetrics()) {
            scoresP2_2.put(m.getId(), new BigDecimal("80.0000"));
        }
        itemP2_2.setMetricScores(scoresP2_2);
        items2.add(itemP2_2);

        submit2.setItems(items2);
        projectService.saveExpertScores(submit2);

        // 6. Admin Summary
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(1L)
                .username("admin")
                .realName("系统管理员")
                .activeRole("ADMIN")
                .build());

        AnnualProjectSummaryVO summary = projectService.getProjectSummary(batchId);
        Assertions.assertEquals(2, summary.getRows().size());

        // P1 weighted average = (90*1.0 + 100*1.0) / 2.0 = 95.0000 (Rank 1)
        AnnualProjectSummaryVO.ProjectSummaryRowVO rowP1 = summary.getRows().stream()
                .filter(r -> r.getProjectId().equals(p1Id)).findFirst().orElseThrow();
        Assertions.assertEquals(new BigDecimal("95.0000"), rowP1.getCompositeScore());
        Assertions.assertEquals(1, rowP1.getRank());
        Assertions.assertFalse(rowP1.getEvaluatorDetails().isEmpty(), "项目汇总明细应包含评分人打分明细");

        // P2 weighted average = (80*1.0 + 80*1.0) / 2.0 = 80.0000 (Rank 2)
        AnnualProjectSummaryVO.ProjectSummaryRowVO rowP2 = summary.getRows().stream()
                .filter(r -> r.getProjectId().equals(p2Id)).findFirst().orElseThrow();
        Assertions.assertEquals(new BigDecimal("80.0000"), rowP2.getCompositeScore());
        Assertions.assertEquals(2, rowP2.getRank());

        // 7. Test Excel Export
        byte[] excelBytes = projectService.exportProjectSummaryExcel(batchId);
        Assertions.assertNotNull(excelBytes);
        Assertions.assertTrue(excelBytes.length > 0);
    }

    @Test
    public void testV2MonthlyTieredHeadAndDeputyAppraisal() {
        // 1. Create monthly batch
        MonthlyBatchSaveDTO dto = new MonthlyBatchSaveDTO();
        dto.setBatchName("2026年11月V2分层考评测试批次");
        dto.setCategory("DEPT_DEPUTY");
        dto.setAppraisalPeriod("2026-11");
        MonthlyBatchSaveDTO.LeaderWeightDTO l1 = new MonthlyBatchSaveDTO.LeaderWeightDTO();
        l1.setLeaderUserId(3L); // 陈剑 (PRIMARY_LEADER)
        l1.setWeight(new BigDecimal("50.0000"));
        MonthlyBatchSaveDTO.LeaderWeightDTO l2 = new MonthlyBatchSaveDTO.LeaderWeightDTO();
        l2.setLeaderUserId(4L); // 施文萃 (CENTER_LEADER, 分管办公室)
        l2.setWeight(new BigDecimal("50.0000"));
        dto.setLeaders(Arrays.asList(l1, l2));

        // Examinees: 王昆淼 (9L, DEPT_HEAD), 田阳阳 (20L, DEPT_DEPUTY, 直属王昆淼, 分管施文萃)
        dto.setExamineeUserIds(Arrays.asList(9L, 20L));
        List<MetricDTO> metrics = metricService.getDefaultTemplates("MONTHLY");
        dto.setMetrics(metrics);

        UserContext.set(UserContext.CurrentUser.builder().userId(1L).username("admin").activeRole("ADMIN").build());
        monthlyService.saveMonthlyBatch(dto);

        List<MonthlyBatchVO> batches = monthlyService.listMonthlyBatches(null, "2026年11月V2分层考评测试批次");
        Assertions.assertFalse(batches.isEmpty());
        Long batchId = batches.get(0).getId();

        monthlyService.publishMonthlyBatch(batchId);
        List<AppraisalMetric> savedMonthlyMetrics = metricService.listMetricsByBatchId(batchId);

        // 2. Score as 陈剑 (PRIMARY_LEADER): 给王昆淼、田阳阳各 100 分
        UserContext.set(UserContext.CurrentUser.builder().userId(3L).username("13908866003").activeRole("LEADER").build());
        MonthlyScoreSubmitDTO s1 = new MonthlyScoreSubmitDTO();
        s1.setBatchId(batchId);
        s1.setIsSubmit(true);
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO item1 = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        item1.setExamineeUserId(9L);
        Map<Long, BigDecimal> mScores1 = new HashMap<>();
        for (AppraisalMetric m : savedMonthlyMetrics) {
            mScores1.put(m.getId(), new BigDecimal("100.0000"));
        }
        item1.setMetricScores(mScores1);
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO item1b = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        item1b.setExamineeUserId(20L);
        item1b.setMetricScores(mScores1);
        s1.setItems(Arrays.asList(item1, item1b));
        monthlyService.saveLeaderScores(s1);

        // 3. Score as 施文萃 (CENTER_LEADER, 王昆淼的分管领导, 田阳阳的分管领导)
        UserContext.set(UserContext.CurrentUser.builder().userId(4L).username("13888952359").activeRole("LEADER").build());
        MonthlyScoreSubmitDTO s2 = new MonthlyScoreSubmitDTO();
        s2.setBatchId(batchId);
        s2.setIsSubmit(true);
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO item2 = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        item2.setExamineeUserId(9L);
        item2.setMetricScores(mScores1); // 100分

        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO item3 = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        item3.setExamineeUserId(20L);
        item3.setMetricScores(mScores1); // 100分
        s2.setItems(Arrays.asList(item2, item3));
        monthlyService.saveLeaderScores(s2);

        // 4. Check summary
        UserContext.set(UserContext.CurrentUser.builder().userId(1L).username("admin").activeRole("ADMIN").build());
        MonthlySummaryVO summary = monthlyService.getMonthlyBatchSummary(batchId);
        Assertions.assertNotNull(summary);
        Assertions.assertFalse(summary.getHeadRows().isEmpty());
        Assertions.assertFalse(summary.getDeputyRows().isEmpty());

        // 王昆淼 (正职): 主要领导100分(25%) + 分管领导100分(35%) + 其他领导0 + 互评0 = 60.0000
        MonthlySummaryVO.TierSummaryRowVO headRow = summary.getHeadRows().stream()
                .filter(r -> r.getExamineeUserId().equals(9L)).findFirst().orElseThrow();
        Assertions.assertEquals(new BigDecimal("100.0000"), headRow.getPrimaryLeaderScore());
        Assertions.assertEquals(new BigDecimal("100.0000"), headRow.getSupervisorLeaderScore());
        Assertions.assertEquals(new BigDecimal("60.0000"), headRow.getCompositeScore());

        // 田阳阳 (副职): 分管领导100分(35%) + 其他中心领导(陈剑100分，邱晔未提交，均分100)*20% + 本部门正职0 + 副职互评0 = 55.0000
        MonthlySummaryVO.TierSummaryRowVO deputyRow = summary.getDeputyRows().stream()
                .filter(r -> r.getExamineeUserId().equals(20L)).findFirst().orElseThrow();
        Assertions.assertEquals(new BigDecimal("100.0000"), deputyRow.getSupervisorLeaderScore());
        Assertions.assertEquals(new BigDecimal("100.0000"), deputyRow.getOtherLeaderScore());
        Assertions.assertEquals(new BigDecimal("55.0000"), deputyRow.getCompositeScore());
        Assertions.assertTrue(deputyRow.getEvaluatorDetails().stream()
                .anyMatch(d -> d.getEvaluatorUserId().equals(3L) && "其他中心领导".equals(d.getRoleName())),
                "副职考核中中心主要领导的评分应并入其他中心领导");

        // 5. Test Multi-sheet export
        byte[] excelBytes = monthlyService.exportMonthlyBatchExcel(batchId);
        Assertions.assertNotNull(excelBytes);
        Assertions.assertTrue(excelBytes.length > 0);
    }

    @Test
    public void testV2SpecialInnovationRatingAndSummary() {
        // 1. Create special innovation batch
        ProjectBatchSaveDTO batchDto = new ProjectBatchSaveDTO();
        batchDto.setBatchName("2026年度专项与创新任务评审测试");
        batchDto.setBatchType("SPECIAL_INNOVATION");
        batchDto.setCategory("ALL");
        batchDto.setAppraisalPeriod("2026");
        ProjectBatchSaveDTO.ExpertWeightDTO ew1 = new ProjectBatchSaveDTO.ExpertWeightDTO();
        ew1.setExpertUserId(32L); // 徐济仓
        ew1.setWeight(new BigDecimal("100.0000"));
        batchDto.setExperts(Collections.singletonList(ew1));
        List<MetricDTO> metrics = metricService.getDefaultTemplates("SPECIAL_INNOVATION");
        Assertions.assertEquals(3, metrics.size());
        Assertions.assertEquals("特色亮点", metrics.get(0).getMetricName());
        Assertions.assertEquals(0, new BigDecimal("30.0000").compareTo(metrics.get(0).getWeight()));
        Assertions.assertEquals("产生质效", metrics.get(1).getMetricName());
        Assertions.assertEquals(0, new BigDecimal("50.0000").compareTo(metrics.get(1).getWeight()));
        Assertions.assertEquals("难度系数", metrics.get(2).getMetricName());
        Assertions.assertEquals(0, new BigDecimal("20.0000").compareTo(metrics.get(2).getWeight()));
        batchDto.setMetrics(metrics);

        UserContext.set(UserContext.CurrentUser.builder().userId(1L).username("admin").activeRole("ADMIN").build());
        projectService.saveProjectBatch(batchDto);

        List<AppraisalBatch> batches = projectService.listProjectBatches("SPECIAL_INNOVATION", null, "2026年度专项与创新任务评审测试");
        Assertions.assertFalse(batches.isEmpty());
        Long batchId = batches.get(0).getId();

        // 专项与创新任务默认中心领导内部细分权重：主要领导 40% + 其他中心领导 60%
        AppraisalBatch specialDetail = projectService.getProjectBatchDetail(batchId);
        Map<String, BigDecimal> specialRoleWeightMap = specialDetail.getRoleWeights().stream()
                .collect(Collectors.toMap(rw -> rw.getRoleCategory(), rw -> rw.getWeight()));
        Assertions.assertEquals(new BigDecimal("40.0000"), specialRoleWeightMap.get("PRIMARY_LEADER"));
        Assertions.assertEquals(new BigDecimal("60.0000"), specialRoleWeightMap.get("OTHER_LEADER"));

        // 2. Add candidate projects
        ProjectItemSaveDTO p1 = new ProjectItemSaveDTO();
        p1.setBatchId(batchId);
        p1.setProjectCode("SP-01");
        p1.setProjectName("数字化配方智能算法优化");
        p1.setDepartment("技术中心");
        projectService.saveProjectItem(p1);

        ProjectItemSaveDTO p2 = new ProjectItemSaveDTO();
        p2.setBatchId(batchId);
        p2.setProjectCode("SP-02");
        p2.setProjectName("烟叶醇化智能温湿度调控");
        p2.setDepartment("原料部");
        projectService.saveProjectItem(p2);

        ProjectItemSaveDTO p3 = new ProjectItemSaveDTO();
        p3.setBatchId(batchId);
        p3.setProjectCode("SP-03");
        p3.setProjectName("原料近红外快速无损检测");
        p3.setDepartment("质检部");
        projectService.saveProjectItem(p3);

        // 3. Publish and Score
        projectService.publishProjectBatch(batchId);

        List<ProjectItemVO> items = projectService.listProjectItems(batchId);
        Assertions.assertEquals(3, items.size());

        // Score as expert
        UserContext.set(UserContext.CurrentUser.builder().userId(32L).username("13085371368").activeRole("EXPERT").build());
        List<AppraisalMetric> savedSpecialMetrics = metricService.listMetricsByBatchId(batchId);
        ExpertScoreSubmitDTO s = new ExpertScoreSubmitDTO();
        s.setBatchId(batchId);
        s.setIsSubmit(true);

        ExpertScoreSubmitDTO.ProjectScoreItemDTO pi1 = new ExpertScoreSubmitDTO.ProjectScoreItemDTO();
        pi1.setProjectId(items.get(0).getId());
        Map<Long, BigDecimal> m1 = new HashMap<>();
        for (AppraisalMetric m : savedSpecialMetrics) m1.put(m.getId(), new BigDecimal("96.0000"));
        pi1.setMetricScores(m1);

        ExpertScoreSubmitDTO.ProjectScoreItemDTO pi2 = new ExpertScoreSubmitDTO.ProjectScoreItemDTO();
        pi2.setProjectId(items.get(1).getId());
        Map<Long, BigDecimal> m2 = new HashMap<>();
        for (AppraisalMetric m : savedSpecialMetrics) m2.put(m.getId(), new BigDecimal("85.0000"));
        pi2.setMetricScores(m2);

        ExpertScoreSubmitDTO.ProjectScoreItemDTO pi3 = new ExpertScoreSubmitDTO.ProjectScoreItemDTO();
        pi3.setProjectId(items.get(2).getId());
        Map<Long, BigDecimal> m3 = new HashMap<>();
        for (AppraisalMetric m : savedSpecialMetrics) m3.put(m.getId(), new BigDecimal("72.0000"));
        pi3.setMetricScores(m3);

        s.setItems(Arrays.asList(pi1, pi2, pi3));
        projectService.saveExpertScores(s);

        // 4. Summary verification
        UserContext.set(UserContext.CurrentUser.builder().userId(1L).username("admin").activeRole("ADMIN").build());
        AnnualProjectSummaryVO summary = projectService.getProjectSummary(batchId);
        Assertions.assertNotNull(summary);
        Assertions.assertEquals(3, summary.getRows().size());

        AnnualProjectSummaryVO.ProjectSummaryRowVO topRow = summary.getRows().get(0);
        Assertions.assertEquals(new BigDecimal("96.0000"), topRow.getCompositeScore());
        Assertions.assertEquals("技术中心", topRow.getDepartment());

        AnnualProjectSummaryVO.ProjectSummaryRowVO secondRow = summary.getRows().get(1);
        Assertions.assertEquals(new BigDecimal("85.0000"), secondRow.getCompositeScore());

        // 5. Test Excel Export
        byte[] excelBytes = projectService.exportProjectSummaryExcel(batchId);
        Assertions.assertNotNull(excelBytes);
        Assertions.assertTrue(excelBytes.length > 0);
    }
}
