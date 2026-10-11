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

        // 2. Test Multi-role user login (dept_qian has both EMPLOYEE and EXPERT)
        LoginRequest qianReq = new LoginRequest();
        qianReq.setUsername("dept_qian");
        qianReq.setPassword("password123");
        LoginResponse qianResp = userService.login(qianReq);
        Assertions.assertNotNull(qianResp.getToken());
        Assertions.assertTrue(qianResp.getRoles().size() >= 2);
        boolean hasExpert = qianResp.getRoles().stream().anyMatch(r -> "EXPERT".equals(r.getRoleCode()));
        boolean hasEmployee = qianResp.getRoles().stream().anyMatch(r -> "EMPLOYEE".equals(r.getRoleCode()));
        Assertions.assertTrue(hasExpert && hasEmployee, "dept_qian should have both EXPERT and EMPLOYEE roles");
    }

    @Test
    public void testMonthlyAppraisalFullFlow() {
        // Set UserContext to Admin (userId = 1)
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(1L)
                .username("admin")
                .realName("系统管理员")
                .activeRole("ADMIN")
                .build());

        // 1. Create Monthly Batch
        MonthlyBatchSaveDTO batchDto = new MonthlyBatchSaveDTO();
        batchDto.setBatchName("2026年10月部门负责人绩效考核测试");
        batchDto.setAppraisalPeriod("2026-10");
        batchDto.setStartTime(LocalDateTime.now().minusHours(1));
        batchDto.setEndTime(LocalDateTime.now().plusDays(7));

        // Leaders: leader_zhang (id=2, weight=50%), leader_li (id=3, weight=50%)
        MonthlyBatchSaveDTO.LeaderWeightDTO l1 = new MonthlyBatchSaveDTO.LeaderWeightDTO();
        l1.setLeaderUserId(2L);
        l1.setWeight(new BigDecimal("50.0000"));
        MonthlyBatchSaveDTO.LeaderWeightDTO l2 = new MonthlyBatchSaveDTO.LeaderWeightDTO();
        l2.setLeaderUserId(3L);
        l2.setWeight(new BigDecimal("50.0000"));
        batchDto.setLeaders(Arrays.asList(l1, l2));

        // Examinees: dept_qian (id=7), dept_zhou (id=8)
        batchDto.setExamineeUserIds(Arrays.asList(7L, 8L));

        // Default Metrics
        List<MetricDTO> metrics = metricService.getDefaultTemplates("MONTHLY");
        batchDto.setMetrics(metrics);

        monthlyService.saveMonthlyBatch(batchDto);

        List<MonthlyBatchVO> batches = monthlyService.listMonthlyBatches(null, "2026年10月部门负责人绩效考核测试");
        Assertions.assertFalse(batches.isEmpty());
        Long batchId = batches.get(0).getId();

        // Test Month Uniqueness: trying to create a second batch for same month "2026-10" MUST fail
        MonthlyBatchSaveDTO dupDto = new MonthlyBatchSaveDTO();
        dupDto.setBatchName("2026年10月重复批次");
        dupDto.setAppraisalPeriod("2026-10");
        dupDto.setLeaders(Arrays.asList(l1, l2));
        dupDto.setExamineeUserIds(Arrays.asList(7L, 8L));
        dupDto.setMetrics(metrics);
        Assertions.assertThrows(BusinessException.class, () -> monthlyService.saveMonthlyBatch(dupDto));

        // 2. Publish Batch
        monthlyService.publishMonthlyBatch(batchId);

        // Test Leader Progress & Urge
        List<EvaluatorProgressVO> leaderProgress = monthlyService.getLeaderProgress(batchId);
        Assertions.assertEquals(2, leaderProgress.size());
        monthlyService.urgeLeader(batchId, 2L);

        // 3. Leader 1 (leader_zhang, id=2) Scores
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(2L)
                .username("leader_zhang")
                .realName("张主任")
                .activeRole("LEADER")
                .build());

        MonthlyLeaderScoringVO leader1View = monthlyService.getLeaderScoringData(batchId);
        Assertions.assertEquals(2, leader1View.getRows().size());

        List<AppraisalMetric> metricList = leader1View.getMetrics();
        MonthlyScoreSubmitDTO submit1 = new MonthlyScoreSubmitDTO();
        submit1.setBatchId(batchId);
        submit1.setIsSubmit(true);

        List<MonthlyScoreSubmitDTO.MonthlyScoreItemDTO> items1 = new ArrayList<>();
        // Examinee 7 (qian) score 90 each metric
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO itemQian = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        itemQian.setExamineeUserId(7L);
        Map<Long, BigDecimal> scoresQian1 = new HashMap<>();
        for (AppraisalMetric m : metricList) {
            scoresQian1.put(m.getId(), new BigDecimal("90.0000"));
        }
        itemQian.setMetricScores(scoresQian1);
        items1.add(itemQian);

        // Examinee 8 (zhou) score 80 each metric
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO itemZhou = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        itemZhou.setExamineeUserId(8L);
        Map<Long, BigDecimal> scoresZhou1 = new HashMap<>();
        for (AppraisalMetric m : metricList) {
            scoresZhou1.put(m.getId(), new BigDecimal("80.0000"));
        }
        itemZhou.setMetricScores(scoresZhou1);
        items1.add(itemZhou);

        submit1.setItems(items1);
        monthlyService.saveLeaderScores(submit1);

        // Test self-withdraw before deadline
        monthlyService.withdrawLeaderScores(batchId);
        MonthlyLeaderScoringVO viewAfterWithdraw = monthlyService.getLeaderScoringData(batchId);
        Assertions.assertEquals("DRAFT", viewAfterWithdraw.getMySubmissionStatus());

        // Resubmit
        monthlyService.saveLeaderScores(submit1);

        // 4. Leader 2 (leader_li, id=3) Scores
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(3L)
                .username("leader_li")
                .realName("李副主任")
                .activeRole("LEADER")
                .build());

        MonthlyScoreSubmitDTO submit2 = new MonthlyScoreSubmitDTO();
        submit2.setBatchId(batchId);
        submit2.setIsSubmit(true);

        List<MonthlyScoreSubmitDTO.MonthlyScoreItemDTO> items2 = new ArrayList<>();
        // Examinee 7 (qian) score 100
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO itemQian2 = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        itemQian2.setExamineeUserId(7L);
        Map<Long, BigDecimal> scoresQian2 = new HashMap<>();
        for (AppraisalMetric m : metricList) {
            scoresQian2.put(m.getId(), new BigDecimal("100.0000"));
        }
        itemQian2.setMetricScores(scoresQian2);
        items2.add(itemQian2);

        // Examinee 8 (zhou) score 80
        MonthlyScoreSubmitDTO.MonthlyScoreItemDTO itemZhou2 = new MonthlyScoreSubmitDTO.MonthlyScoreItemDTO();
        itemZhou2.setExamineeUserId(8L);
        Map<Long, BigDecimal> scoresZhou2 = new HashMap<>();
        for (AppraisalMetric m : metricList) {
            scoresZhou2.put(m.getId(), new BigDecimal("80.0000"));
        }
        itemZhou2.setMetricScores(scoresZhou2);
        items2.add(itemZhou2);

        submit2.setItems(items2);
        monthlyService.saveLeaderScores(submit2);

        // 5. Admin checks Summary
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(1L)
                .username("admin")
                .realName("系统管理员")
                .activeRole("ADMIN")
                .build());

        MonthlySummaryVO summary = monthlyService.getMonthlyBatchSummary(batchId);
        Assertions.assertEquals(2, summary.getRows().size());

        // Qian composite: 90 * 0.5 + 100 * 0.5 = 95.0000
        MonthlySummaryVO.SummaryRowVO rowQian = summary.getRows().stream()
                .filter(r -> r.getExamineeUserId().equals(7L)).findFirst().orElseThrow();
        Assertions.assertEquals(new BigDecimal("95.0000"), rowQian.getCompositeScore());
        Assertions.assertEquals(1, rowQian.getRank());

        // Zhou composite: 80 * 0.5 + 80 * 0.5 = 80.0000
        MonthlySummaryVO.SummaryRowVO rowZhou = summary.getRows().stream()
                .filter(r -> r.getExamineeUserId().equals(8L)).findFirst().orElseThrow();
        Assertions.assertEquals(new BigDecimal("80.0000"), rowZhou.getCompositeScore());
        Assertions.assertEquals(2, rowZhou.getRank());

        // 6. Test Excel Export
        byte[] excelBytes = monthlyService.exportMonthlyBatchExcel(batchId);
        Assertions.assertNotNull(excelBytes);
        Assertions.assertTrue(excelBytes.length > 0);

        // 7. Examinee checks personal result BEFORE archiving -> MUST be blocked with BusinessException
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(7L)
                .username("dept_qian")
                .realName("钱部长")
                .activeRole("EMPLOYEE")
                .build());
        Assertions.assertThrows(BusinessException.class, () -> monthlyService.getExamineePersonalResult(batchId));

        // 8. Admin completes/archives the batch
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(1L)
                .username("admin")
                .realName("系统管理员")
                .activeRole("ADMIN")
                .build());
        monthlyService.completeMonthlyBatch(batchId);

        // 9. Examinee checks personal result AFTER archiving -> Successfully gets score & rankings
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(7L)
                .username("dept_qian")
                .realName("钱部长")
                .activeRole("EMPLOYEE")
                .build());

        ExamineePersonalResultVO personalResult = monthlyService.getExamineePersonalResult(batchId);
        Assertions.assertEquals(new BigDecimal("95.0000"), personalResult.getMyCompositeScore());
        Assertions.assertEquals(1, personalResult.getMyRank());
        Assertions.assertTrue(personalResult.getRankList().isEmpty(), "根据保密规则，不返回部门全员榜单，仅能查看本人总分和排名");
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
        batchDto.setAppraisalPeriod("2026");
        batchDto.setStartTime(LocalDateTime.now().minusHours(1));
        batchDto.setEndTime(LocalDateTime.now().plusDays(10));

        // Experts: expert_wang (id=4, weight=50%), expert_zhao (id=5, weight=50%) 保持与月度绩效一致满分100%
        ProjectBatchSaveDTO.ExpertWeightDTO e1 = new ProjectBatchSaveDTO.ExpertWeightDTO();
        e1.setExpertUserId(4L);
        e1.setWeight(new BigDecimal("50.0000"));
        ProjectBatchSaveDTO.ExpertWeightDTO e2 = new ProjectBatchSaveDTO.ExpertWeightDTO();
        e2.setExpertUserId(5L);
        e2.setWeight(new BigDecimal("50.0000"));
        batchDto.setExperts(Arrays.asList(e1, e2));

        // Metrics
        List<MetricDTO> metrics = metricService.getDefaultTemplates("ANNUAL_PROJECT");
        batchDto.setMetrics(metrics);

        projectService.saveProjectBatch(batchDto);

        List<AppraisalBatch> batches = projectService.listProjectBatches(null, "2026年度目标管理绩效项目评审测试");
        Assertions.assertFalse(batches.isEmpty());
        Long batchId = batches.get(0).getId();

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
        p1.setOwnerName("钱部长");
        p1.setSortOrder(1);
        projectService.saveProjectItem(p1);

        ProjectItemSaveDTO p2 = new ProjectItemSaveDTO();
        p2.setBatchId(batchId);
        p2.setProjectCode("2");
        p2.setProjectName("仓储物流智能调度系统开发项目");
        p2.setOwnerName("周部长");
        p2.setSortOrder(2);
        projectService.saveProjectItem(p2);

        List<ProjectItemVO> projectList = projectService.listProjectItems(batchId);
        Assertions.assertEquals(2, projectList.size());
        Long p1Id = projectList.get(0).getId();
        Long p2Id = projectList.get(1).getId();

        // 3. Publish Batch
        projectService.publishProjectBatch(batchId);

        // Test Expert Progress & Urge
        List<EvaluatorProgressVO> expertProgress = projectService.getExpertProgress(batchId);
        Assertions.assertEquals(2, expertProgress.size());
        projectService.urgeExpert(batchId, 4L);

        // 4. Expert 1 (expert_wang, id=4) Scores
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(4L)
                .username("expert_wang")
                .realName("王专家")
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

        // 5. Expert 2 (expert_zhao, id=5) Scores
        UserContext.set(UserContext.CurrentUser.builder()
                .userId(5L)
                .username("expert_zhao")
                .realName("赵专家")
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
}
