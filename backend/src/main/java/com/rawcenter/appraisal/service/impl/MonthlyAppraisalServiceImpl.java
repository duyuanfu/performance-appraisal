package com.rawcenter.appraisal.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rawcenter.appraisal.common.BusinessException;
import com.rawcenter.appraisal.common.UserContext;
import com.rawcenter.appraisal.dto.MonthlyBatchSaveDTO;
import com.rawcenter.appraisal.dto.MonthlyScoreSubmitDTO;
import com.rawcenter.appraisal.entity.*;
import com.rawcenter.appraisal.mapper.*;
import com.rawcenter.appraisal.service.MetricService;
import com.rawcenter.appraisal.service.MonthlyAppraisalService;
import com.rawcenter.appraisal.vo.EvaluatorProgressVO;
import com.rawcenter.appraisal.vo.ExamineePersonalResultVO;
import com.rawcenter.appraisal.vo.MonthlyBatchVO;
import com.rawcenter.appraisal.vo.MonthlyLeaderScoringVO;
import com.rawcenter.appraisal.vo.MonthlySummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MonthlyAppraisalServiceImpl implements MonthlyAppraisalService {

    private final AppraisalBatchMapper batchMapper;
    private final MonthlyEvaluatorMapper evaluatorMapper;
    private final MonthlyExamineeMapper examineeMapper;
    private final MonthlyScoreRecordMapper scoreRecordMapper;
    private final MonthlyScoreDetailMapper scoreDetailMapper;
    private final SysUserMapper userMapper;
    private final MetricService metricService;

    @Override
    public List<MonthlyBatchVO> listMonthlyBatches(String status, String keyword) {
        Long currentUserId = UserContext.getUserId();
        String activeRole = UserContext.get() != null ? UserContext.get().getActiveRole() : null;

        LambdaQueryWrapper<AppraisalBatch> wrapper = new LambdaQueryWrapper<AppraisalBatch>()
                .eq(AppraisalBatch::getBatchType, "MONTHLY")
                .eq(StringUtils.hasText(status), AppraisalBatch::getStatus, status)
                .like(StringUtils.hasText(keyword), AppraisalBatch::getBatchName, keyword);

        // 数据权限隔离：领导角色仅可见分配给自己的打分批次，员工角色仅可见自己参评的批次；且未发布的草稿对非管理员完全不可见！
        if ("LEADER".equalsIgnoreCase(activeRole) && currentUserId != null) {
            List<MonthlyEvaluator> myEvaluations = evaluatorMapper.selectList(new LambdaQueryWrapper<MonthlyEvaluator>()
                    .eq(MonthlyEvaluator::getLeaderUserId, currentUserId));
            if (myEvaluations.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> allowedBatchIds = myEvaluations.stream().map(MonthlyEvaluator::getBatchId).collect(Collectors.toList());
            wrapper.in(AppraisalBatch::getId, allowedBatchIds);
            wrapper.ne(AppraisalBatch::getStatus, "DRAFT");
        } else if ("EMPLOYEE".equalsIgnoreCase(activeRole) && currentUserId != null) {
            List<MonthlyExaminee> myExaminees = examineeMapper.selectList(new LambdaQueryWrapper<MonthlyExaminee>()
                    .eq(MonthlyExaminee::getExamineeUserId, currentUserId));
            if (myExaminees.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> allowedBatchIds = myExaminees.stream().map(MonthlyExaminee::getBatchId).collect(Collectors.toList());
            wrapper.in(AppraisalBatch::getId, allowedBatchIds);
            wrapper.ne(AppraisalBatch::getStatus, "DRAFT");
        }

        wrapper.orderByDesc(AppraisalBatch::getCreatedAt);
        List<AppraisalBatch> batches = batchMapper.selectList(wrapper);

        if (batches.isEmpty()) {
            return Collections.emptyList();
        }

        List<MonthlyBatchVO> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (AppraisalBatch b : batches) {
            Long leaderCount = evaluatorMapper.selectCount(new LambdaQueryWrapper<MonthlyEvaluator>()
                    .eq(MonthlyEvaluator::getBatchId, b.getId()));
            Long examineeCount = examineeMapper.selectCount(new LambdaQueryWrapper<MonthlyExaminee>()
                    .eq(MonthlyExaminee::getBatchId, b.getId()));
            Long submittedLeaderCount = scoreRecordMapper.selectCount(new LambdaQueryWrapper<MonthlyScoreRecord>()
                    .eq(MonthlyScoreRecord::getBatchId, b.getId())
                    .eq(MonthlyScoreRecord::getStatus, "SUBMITTED"));

            // Calculate submitted unique leaders
            List<MonthlyScoreRecord> submittedRecords = scoreRecordMapper.selectList(new LambdaQueryWrapper<MonthlyScoreRecord>()
                    .eq(MonthlyScoreRecord::getBatchId, b.getId())
                    .eq(MonthlyScoreRecord::getStatus, "SUBMITTED"));
            long distinctSubmittedLeaders = submittedRecords.stream()
                    .map(MonthlyScoreRecord::getLeaderUserId)
                    .distinct()
                    .count();

            String myStatus = "NOT_STARTED";
            if (currentUserId != null) {
                List<MonthlyScoreRecord> myRecords = scoreRecordMapper.selectList(new LambdaQueryWrapper<MonthlyScoreRecord>()
                        .eq(MonthlyScoreRecord::getBatchId, b.getId())
                        .eq(MonthlyScoreRecord::getLeaderUserId, currentUserId));
                if (!myRecords.isEmpty()) {
                    boolean allSubmitted = myRecords.stream().allMatch(r -> "SUBMITTED".equals(r.getStatus()));
                    myStatus = allSubmitted ? "SUBMITTED" : "DRAFT";
                }
            }

            boolean isExpired = b.getEndTime() != null && now.isAfter(b.getEndTime());

            result.add(MonthlyBatchVO.builder()
                    .id(b.getId())
                    .batchName(b.getBatchName())
                    .batchType(b.getBatchType())
                    .appraisalPeriod(b.getAppraisalPeriod())
                    .startTime(b.getStartTime())
                    .endTime(b.getEndTime())
                    .status(b.getStatus())
                    .leaderCount(leaderCount.intValue())
                    .examineeCount(examineeCount.intValue())
                    .submittedLeaderCount((int) distinctSubmittedLeaders)
                    .mySubmissionStatus(myStatus)
                    .isExpired(isExpired)
                    .createdAt(b.getCreatedAt())
                    .build());
        }

        return result;
    }

    @Override
    public MonthlyBatchVO getMonthlyBatchDetail(Long batchId) {
        AppraisalBatch b = batchMapper.selectById(batchId);
        if (b == null) {
            throw new BusinessException(404, "考核批次不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        Long leaderCount = evaluatorMapper.selectCount(new LambdaQueryWrapper<MonthlyEvaluator>()
                .eq(MonthlyEvaluator::getBatchId, b.getId()));
        Long examineeCount = examineeMapper.selectCount(new LambdaQueryWrapper<MonthlyExaminee>()
                .eq(MonthlyExaminee::getBatchId, b.getId()));

        return MonthlyBatchVO.builder()
                .id(b.getId())
                .batchName(b.getBatchName())
                .batchType(b.getBatchType())
                .appraisalPeriod(b.getAppraisalPeriod())
                .startTime(b.getStartTime())
                .endTime(b.getEndTime())
                .status(b.getStatus())
                .leaderCount(leaderCount.intValue())
                .examineeCount(examineeCount.intValue())
                .isExpired(b.getEndTime() != null && now.isAfter(b.getEndTime()))
                .createdAt(b.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMonthlyBatch(MonthlyBatchSaveDTO dto) {
        BigDecimal totalLeaderWeight = BigDecimal.ZERO;
        for (MonthlyBatchSaveDTO.LeaderWeightDTO l : dto.getLeaders()) {
            if (l.getWeight() == null || l.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "领导打分权重必须大于0");
            }
            totalLeaderWeight = totalLeaderWeight.add(l.getWeight());
        }
        BigDecimal weightDiff = totalLeaderWeight.subtract(new BigDecimal("100.0000")).abs();
        if (weightDiff.compareTo(new BigDecimal("0.0001")) > 0) {
            throw new BusinessException(400, "领导打分权重合计必须为100%，当前为：" + totalLeaderWeight.stripTrailingZeros().toPlainString() + "%");
        }

        // 校验唯一性：一个月只能建立一个月度绩效考核流程
        Long existingBatchCount = batchMapper.selectCount(new LambdaQueryWrapper<AppraisalBatch>()
                .eq(AppraisalBatch::getBatchType, "MONTHLY")
                .eq(AppraisalBatch::getAppraisalPeriod, dto.getAppraisalPeriod().trim())
                .ne(dto.getId() != null, AppraisalBatch::getId, dto.getId()));
        if (existingBatchCount > 0) {
            throw new BusinessException(400, "该月份【" + dto.getAppraisalPeriod().trim() + "】已存在月度考核批次，系统规定一个月只能建立一个考核流程！");
        }

        AppraisalBatch batch;
        if (dto.getId() == null) {
            batch = AppraisalBatch.builder()
                    .batchName(dto.getBatchName())
                    .batchType("MONTHLY")
                    .appraisalPeriod(dto.getAppraisalPeriod())
                    .startTime(dto.getStartTime())
                    .endTime(dto.getEndTime())
                    .status("DRAFT")
                    .createdBy(UserContext.getUserId())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            batchMapper.insert(batch);
        } else {
            batch = batchMapper.selectById(dto.getId());
            if (batch == null) {
                throw new BusinessException(404, "批次不存在");
            }
            batch.setBatchName(dto.getBatchName());
            batch.setAppraisalPeriod(dto.getAppraisalPeriod());
            batch.setStartTime(dto.getStartTime() != null ? dto.getStartTime() : LocalDateTime.now());
            batch.setEndTime(dto.getEndTime());
            // 重新发起时将状态置为进行中，允许领导继续打分
            batch.setStatus("IN_PROGRESS");
            batch.setUpdatedAt(LocalDateTime.now());
            batchMapper.updateById(batch);
        }

        Long batchId = batch.getId();

        // 1. Save Metrics
        metricService.saveBatchMetrics(batchId, dto.getMetrics());

        // 2. Save Evaluators
        evaluatorMapper.delete(new LambdaQueryWrapper<MonthlyEvaluator>().eq(MonthlyEvaluator::getBatchId, batchId));
        for (MonthlyBatchSaveDTO.LeaderWeightDTO l : dto.getLeaders()) {
            evaluatorMapper.insert(MonthlyEvaluator.builder()
                    .batchId(batchId)
                    .leaderUserId(l.getLeaderUserId())
                    .weight(l.getWeight())
                    .build());
        }

        // 3. Save Examinees
        examineeMapper.delete(new LambdaQueryWrapper<MonthlyExaminee>().eq(MonthlyExaminee::getBatchId, batchId));
        List<SysUser> examineeUsers = userMapper.selectBatchIds(dto.getExamineeUserIds());
        for (SysUser u : examineeUsers) {
            examineeMapper.insert(MonthlyExaminee.builder()
                    .batchId(batchId)
                    .examineeUserId(u.getId())
                    .department(u.getDepartment())
                    .position(u.getPosition())
                    .build());
        }
    }

    @Override
    public void publishMonthlyBatch(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        Long leaderCount = evaluatorMapper.selectCount(new LambdaQueryWrapper<MonthlyEvaluator>()
                .eq(MonthlyEvaluator::getBatchId, batchId));
        if (leaderCount == 0) {
            throw new BusinessException(400, "该批次尚未分配评分领导，无法发布。请配置评分领导后再发布任务！");
        }

        Long examineeCount = examineeMapper.selectCount(new LambdaQueryWrapper<MonthlyExaminee>()
                .eq(MonthlyExaminee::getBatchId, batchId));
        if (examineeCount == 0) {
            throw new BusinessException(400, "该批次尚未选择被考核人员，无法发布。请选择被考核人员后再发布任务！");
        }

        batch.setStatus("IN_PROGRESS");
        if (batch.getStartTime() == null) {
            batch.setStartTime(LocalDateTime.now());
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    @Override
    public void completeMonthlyBatch(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }
        batch.setStatus("COMPLETED");
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    @Override
    public MonthlyLeaderScoringVO getLeaderScoringData(Long batchId) {
        Long leaderUserId = UserContext.getUserId();
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        if ("DRAFT".equals(batch.getStatus())) {
            throw new BusinessException(400, "该月度考核批次尚未正式发布，暂不可开展打分");
        }

        // Check if user is assigned as evaluator
        MonthlyEvaluator evaluator = evaluatorMapper.selectOne(new LambdaQueryWrapper<MonthlyEvaluator>()
                .eq(MonthlyEvaluator::getBatchId, batchId)
                .eq(MonthlyEvaluator::getLeaderUserId, leaderUserId));
        if (evaluator == null) {
            throw new BusinessException(403, "您不是该批次的评审领导");
        }

        List<AppraisalMetric> metrics = metricService.listMetricsByBatchId(batchId);

        // Fetch examinees
        List<MonthlyExaminee> examinees = examineeMapper.selectList(new LambdaQueryWrapper<MonthlyExaminee>()
                .eq(MonthlyExaminee::getBatchId, batchId));
        if (examinees.isEmpty()) {
            return MonthlyLeaderScoringVO.builder()
                    .batchId(batchId)
                    .batchName(batch.getBatchName())
                    .appraisalPeriod(batch.getAppraisalPeriod())
                    .endTime(batch.getEndTime())
                    .isExpired(batch.getEndTime() != null && LocalDateTime.now().isAfter(batch.getEndTime()))
                    .mySubmissionStatus("DRAFT")
                    .metrics(metrics)
                    .rows(Collections.emptyList())
                    .build();
        }

        List<Long> examineeUserIds = examinees.stream().map(MonthlyExaminee::getExamineeUserId).collect(Collectors.toList());
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(examineeUserIds)
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        // Fetch existing records for this leader
        List<MonthlyScoreRecord> records = scoreRecordMapper.selectList(new LambdaQueryWrapper<MonthlyScoreRecord>()
                .eq(MonthlyScoreRecord::getBatchId, batchId)
                .eq(MonthlyScoreRecord::getLeaderUserId, leaderUserId));
        Map<Long, MonthlyScoreRecord> recordMap = records.stream()
                .collect(Collectors.toMap(MonthlyScoreRecord::getExamineeUserId, r -> r));

        String leaderStatus = "DRAFT";
        if (!records.isEmpty()) {
            boolean allSubmitted = records.size() == examinees.size() && records.stream().allMatch(r -> "SUBMITTED".equals(r.getStatus()));
            if (allSubmitted) {
                leaderStatus = "SUBMITTED";
            }
        }

        List<MonthlyLeaderScoringVO.ExamineeScoringRowVO> rows = new ArrayList<>();
        for (MonthlyExaminee ex : examinees) {
            SysUser u = userMap.get(ex.getExamineeUserId());
            MonthlyScoreRecord rec = recordMap.get(ex.getExamineeUserId());

            Map<Long, BigDecimal> scoreMap = new HashMap<>();
            BigDecimal totalScore = BigDecimal.ZERO;

            if (rec != null) {
                totalScore = rec.getTotalScore();
                List<MonthlyScoreDetail> details = scoreDetailMapper.selectList(new LambdaQueryWrapper<MonthlyScoreDetail>()
                        .eq(MonthlyScoreDetail::getRecordId, rec.getId()));
                for (MonthlyScoreDetail d : details) {
                    scoreMap.put(d.getMetricId(), d.getScore());
                }
            }

            rows.add(MonthlyLeaderScoringVO.ExamineeScoringRowVO.builder()
                    .examineeUserId(ex.getExamineeUserId())
                    .realName(u != null ? u.getRealName() : "未知")
                    .department(ex.getDepartment())
                    .position(ex.getPosition())
                    .metricScores(scoreMap)
                    .totalScore(totalScore)
                    .build());
        }

        boolean isExpired = batch.getEndTime() != null && LocalDateTime.now().isAfter(batch.getEndTime());

        return MonthlyLeaderScoringVO.builder()
                .batchId(batchId)
                .batchName(batch.getBatchName())
                .appraisalPeriod(batch.getAppraisalPeriod())
                .endTime(batch.getEndTime())
                .isExpired(isExpired)
                .batchStatus(batch.getStatus())
                .mySubmissionStatus(leaderStatus)
                .metrics(metrics)
                .rows(rows)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLeaderScores(MonthlyScoreSubmitDTO dto) {
        Long leaderUserId = UserContext.getUserId();
        AppraisalBatch batch = batchMapper.selectById(dto.getBatchId());
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        if (!"IN_PROGRESS".equals(batch.getStatus())) {
            if ("DRAFT".equals(batch.getStatus())) {
                throw new BusinessException(400, "该月度考核批次尚未正式发布，暂不可进行评分打分");
            }
            throw new BusinessException(400, "该考核批次已归档封存，评分数据已锁定，无法修改");
        }

        // Deadline check
        if (batch.getEndTime() != null && LocalDateTime.now().isAfter(batch.getEndTime())) {
            throw new BusinessException(400, "考核截止时间已过，无法保存或提交评分");
        }

        List<AppraisalMetric> metrics = metricService.listMetricsByBatchId(dto.getBatchId());
        Map<Long, AppraisalMetric> metricMap = metrics.stream().collect(Collectors.toMap(AppraisalMetric::getId, m -> m));

        boolean isSubmit = Boolean.TRUE.equals(dto.getIsSubmit());

        if (CollectionUtils.isEmpty(dto.getItems())) {
            throw new BusinessException(400, "评分数据不能为空");
        }

        for (MonthlyScoreSubmitDTO.MonthlyScoreItemDTO item : dto.getItems()) {
            BigDecimal totalScore = BigDecimal.ZERO;

            if (isSubmit) {
                // Validate that all metrics are scored
                for (AppraisalMetric m : metrics) {
                    BigDecimal score = item.getMetricScores() != null ? item.getMetricScores().get(m.getId()) : null;
                    if (score == null) {
                        throw new BusinessException(400, "被考核人员得分未填完整，请填写所有指标评分后再正式提交");
                    }
                    if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(m.getMaxScore()) > 0) {
                        throw new BusinessException(400, "指标【" + m.getMetricName() + "】分值必须在 0 ~ " + m.getMaxScore() + " 之间");
                    }
                    // Add weighted contribution: score * (weight / 100)
                    BigDecimal weightedScore = score.multiply(m.getWeight())
                            .divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP);
                    totalScore = totalScore.add(weightedScore);
                }
            } else {
                // Draft mode: compute total score for whatever is filled
                if (item.getMetricScores() != null) {
                    for (Map.Entry<Long, BigDecimal> entry : item.getMetricScores().entrySet()) {
                        AppraisalMetric m = metricMap.get(entry.getKey());
                        if (m != null && entry.getValue() != null) {
                            BigDecimal weightedScore = entry.getValue().multiply(m.getWeight())
                                    .divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP);
                            totalScore = totalScore.add(weightedScore);
                        }
                    }
                }
            }

            totalScore = totalScore.setScale(4, RoundingMode.HALF_UP);

            // Find existing record
            MonthlyScoreRecord record = scoreRecordMapper.selectOne(new LambdaQueryWrapper<MonthlyScoreRecord>()
                    .eq(MonthlyScoreRecord::getBatchId, dto.getBatchId())
                    .eq(MonthlyScoreRecord::getLeaderUserId, leaderUserId)
                    .eq(MonthlyScoreRecord::getExamineeUserId, item.getExamineeUserId()));

            if (record == null) {
                record = MonthlyScoreRecord.builder()
                        .batchId(dto.getBatchId())
                        .leaderUserId(leaderUserId)
                        .examineeUserId(item.getExamineeUserId())
                        .totalScore(totalScore)
                        .status(isSubmit ? "SUBMITTED" : "DRAFT")
                        .submittedAt(isSubmit ? LocalDateTime.now() : null)
                        .build();
                scoreRecordMapper.insert(record);
            } else {
                record.setTotalScore(totalScore);
                record.setStatus(isSubmit ? "SUBMITTED" : "DRAFT");
                record.setSubmittedAt(isSubmit ? LocalDateTime.now() : null);
                scoreRecordMapper.updateById(record);
            }

            // Save details
            scoreDetailMapper.delete(new LambdaQueryWrapper<MonthlyScoreDetail>()
                    .eq(MonthlyScoreDetail::getRecordId, record.getId()));

            if (item.getMetricScores() != null) {
                for (Map.Entry<Long, BigDecimal> entry : item.getMetricScores().entrySet()) {
                    if (entry.getValue() != null) {
                        scoreDetailMapper.insert(MonthlyScoreDetail.builder()
                                .recordId(record.getId())
                                .metricId(entry.getKey())
                                .score(entry.getValue().setScale(4, RoundingMode.HALF_UP))
                                .build());
                    }
                }
            }
        }
    }

    @Override
    public void withdrawLeaderScores(Long batchId) {
        Long leaderUserId = UserContext.getUserId();
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        if (!"IN_PROGRESS".equals(batch.getStatus())) {
            if ("DRAFT".equals(batch.getStatus())) {
                throw new BusinessException(400, "该月度考核批次尚未正式发布");
            }
            throw new BusinessException(400, "该考核批次已归档封存，评分数据已锁定，无法撤回");
        }

        // Deadline check
        if (batch.getEndTime() != null && LocalDateTime.now().isAfter(batch.getEndTime())) {
            throw new BusinessException(400, "考核截止时间已过，无法撤回评分");
        }

        List<MonthlyScoreRecord> records = scoreRecordMapper.selectList(new LambdaQueryWrapper<MonthlyScoreRecord>()
                .eq(MonthlyScoreRecord::getBatchId, batchId)
                .eq(MonthlyScoreRecord::getLeaderUserId, leaderUserId));
        if (records.isEmpty()) {
            throw new BusinessException(400, "尚未提交过评分，无需撤回");
        }

        for (MonthlyScoreRecord r : records) {
            r.setStatus("DRAFT");
            scoreRecordMapper.updateById(r);
        }
    }

    @Override
    public List<EvaluatorProgressVO> getLeaderProgress(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        List<MonthlyEvaluator> evaluators = evaluatorMapper.selectList(new LambdaQueryWrapper<MonthlyEvaluator>()
                .eq(MonthlyEvaluator::getBatchId, batchId));
        if (evaluators.isEmpty()) {
            return Collections.emptyList();
        }

        Long examineeCount = examineeMapper.selectCount(new LambdaQueryWrapper<MonthlyExaminee>()
                .eq(MonthlyExaminee::getBatchId, batchId));

        List<Long> leaderUserIds = evaluators.stream().map(MonthlyEvaluator::getLeaderUserId).collect(Collectors.toList());
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(leaderUserIds)
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        List<MonthlyScoreRecord> allRecords = scoreRecordMapper.selectList(new LambdaQueryWrapper<MonthlyScoreRecord>()
                .eq(MonthlyScoreRecord::getBatchId, batchId));

        Map<Long, List<MonthlyScoreRecord>> recordsByLeader = allRecords.stream()
                .collect(Collectors.groupingBy(MonthlyScoreRecord::getLeaderUserId));

        List<EvaluatorProgressVO> result = new ArrayList<>();
        for (MonthlyEvaluator ev : evaluators) {
            SysUser u = userMap.get(ev.getLeaderUserId());
            List<MonthlyScoreRecord> records = recordsByLeader.getOrDefault(ev.getLeaderUserId(), Collections.emptyList());

            String status = "NOT_STARTED";
            LocalDateTime submittedAt = null;

            if (!records.isEmpty()) {
                boolean allSubmitted = records.size() == examineeCount && records.stream().allMatch(r -> "SUBMITTED".equals(r.getStatus()));
                if (allSubmitted) {
                    status = "SUBMITTED";
                    submittedAt = records.get(0).getSubmittedAt();
                } else {
                    status = "DRAFT";
                }
            }

            result.add(EvaluatorProgressVO.builder()
                    .userId(ev.getLeaderUserId())
                    .realName(u != null ? u.getRealName() : "未知领导")
                    .department(u != null ? u.getDepartment() : "")
                    .position(u != null ? u.getPosition() : "")
                    .phone(u != null ? u.getPhone() : "")
                    .weight(ev.getWeight())
                    .status(status)
                    .scoredCount(records.size())
                    .totalTargetCount(examineeCount.intValue())
                    .submittedAt(submittedAt)
                    .build());
        }

        return result;
    }

    @Override
    public void urgeLeader(Long batchId, Long leaderUserId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }
        if (!"IN_PROGRESS".equals(batch.getStatus())) {
            throw new BusinessException(400, "仅支持对进行中的考核批次发送催促提醒");
        }
        // System registers urge log or dispatch message
    }

    @Override
    public MonthlySummaryVO getMonthlyBatchSummary(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        // 1. Get Evaluator leaders
        List<MonthlyEvaluator> evaluators = evaluatorMapper.selectList(new LambdaQueryWrapper<MonthlyEvaluator>()
                .eq(MonthlyEvaluator::getBatchId, batchId));
        List<Long> leaderUserIds = evaluators.stream().map(MonthlyEvaluator::getLeaderUserId).collect(Collectors.toList());
        Map<Long, SysUser> leaderUserMap = leaderUserIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(leaderUserIds).stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        // Check which leaders have submitted
        List<MonthlyScoreRecord> allRecords = scoreRecordMapper.selectList(new LambdaQueryWrapper<MonthlyScoreRecord>()
                .eq(MonthlyScoreRecord::getBatchId, batchId));
        Map<String, MonthlyScoreRecord> recordKeyMap = allRecords.stream()
                .collect(Collectors.toMap(r -> r.getLeaderUserId() + "_" + r.getExamineeUserId(), r -> r, (a, b) -> a));

        List<Long> recordIds = allRecords.stream().map(MonthlyScoreRecord::getId).collect(Collectors.toList());
        Map<String, BigDecimal> recordDetailMap = new HashMap<>();
        if (!recordIds.isEmpty()) {
            List<MonthlyScoreDetail> details = scoreDetailMapper.selectList(new LambdaQueryWrapper<MonthlyScoreDetail>()
                    .in(MonthlyScoreDetail::getRecordId, recordIds));
            for (MonthlyScoreDetail d : details) {
                recordDetailMap.put(d.getRecordId() + "_" + d.getMetricId(), d.getScore());
            }
        }

        Set<Long> submittedLeaders = allRecords.stream()
                .filter(r -> "SUBMITTED".equals(r.getStatus()))
                .map(MonthlyScoreRecord::getLeaderUserId)
                .collect(Collectors.toSet());

        List<MonthlySummaryVO.LeaderHeaderVO> leaderHeaders = new ArrayList<>();
        for (MonthlyEvaluator ev : evaluators) {
            SysUser u = leaderUserMap.get(ev.getLeaderUserId());
            leaderHeaders.add(MonthlySummaryVO.LeaderHeaderVO.builder()
                    .leaderUserId(ev.getLeaderUserId())
                    .realName(u != null ? u.getRealName() : "领导")
                    .weight(ev.getWeight())
                    .submitted(submittedLeaders.contains(ev.getLeaderUserId()))
                    .build());
        }

        // 2. Metrics & Examinees
        List<AppraisalMetric> metrics = metricService.listMetricsByBatchId(batchId);
        List<MonthlyExaminee> examinees = examineeMapper.selectList(new LambdaQueryWrapper<MonthlyExaminee>()
                .eq(MonthlyExaminee::getBatchId, batchId));
        List<Long> examineeUserIds = examinees.stream().map(MonthlyExaminee::getExamineeUserId).collect(Collectors.toList());
        Map<Long, SysUser> examineeUserMap = examineeUserIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(examineeUserIds).stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        List<MonthlySummaryVO.SummaryRowVO> summaryRows = new ArrayList<>();

        for (MonthlyExaminee ex : examinees) {
            SysUser u = examineeUserMap.get(ex.getExamineeUserId());
            Map<Long, BigDecimal> leaderScoreMap = new HashMap<>();
            Map<String, BigDecimal> leaderMetricScoreMap = new HashMap<>();
            Map<Long, BigDecimal> metricAvgScores = new HashMap<>();
            BigDecimal compositeScore = BigDecimal.ZERO;

            for (MonthlyEvaluator ev : evaluators) {
                MonthlyScoreRecord rec = recordKeyMap.get(ev.getLeaderUserId() + "_" + ex.getExamineeUserId());
                BigDecimal score = BigDecimal.ZERO;
                boolean isSubmitted = rec != null && "SUBMITTED".equals(rec.getStatus());
                if (isSubmitted) {
                    score = rec.getTotalScore();
                }
                leaderScoreMap.put(ev.getLeaderUserId(), score);

                // Collect per-leader per-metric score
                for (AppraisalMetric m : metrics) {
                    BigDecimal metricScore = BigDecimal.ZERO;
                    if (isSubmitted && rec != null) {
                        BigDecimal ds = recordDetailMap.get(rec.getId() + "_" + m.getId());
                        if (ds != null) {
                            metricScore = ds;
                        }
                    }
                    leaderMetricScoreMap.put(ev.getLeaderUserId() + "_" + m.getId(), metricScore);
                }

                // Add weighted: score * (weight / 100)
                BigDecimal weighted = score.multiply(ev.getWeight())
                        .divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP);
                compositeScore = compositeScore.add(weighted);
            }

            // Compute composite weighted score for each metric across leaders
            for (AppraisalMetric m : metrics) {
                BigDecimal metricWeightedSum = BigDecimal.ZERO;
                for (MonthlyEvaluator ev : evaluators) {
                    BigDecimal lms = leaderMetricScoreMap.get(ev.getLeaderUserId() + "_" + m.getId());
                    if (lms != null) {
                        metricWeightedSum = metricWeightedSum.add(lms.multiply(ev.getWeight())
                                .divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP));
                    }
                }
                metricAvgScores.put(m.getId(), metricWeightedSum.setScale(4, RoundingMode.HALF_UP));
            }

            compositeScore = compositeScore.setScale(4, RoundingMode.HALF_UP);

            summaryRows.add(MonthlySummaryVO.SummaryRowVO.builder()
                    .examineeUserId(ex.getExamineeUserId())
                    .realName(u != null ? u.getRealName() : "员工")
                    .department(ex.getDepartment())
                    .position(ex.getPosition())
                    .metricAvgScores(metricAvgScores)
                    .leaderScores(leaderScoreMap)
                    .leaderMetricScores(leaderMetricScoreMap)
                    .compositeScore(compositeScore)
                    .rank(1)
                    .build());
        }

        // Sort descending by compositeScore and assign ranks
        summaryRows.sort((a, b) -> b.getCompositeScore().compareTo(a.getCompositeScore()));
        for (int i = 0; i < summaryRows.size(); i++) {
            summaryRows.get(i).setRank(i + 1);
        }

        return MonthlySummaryVO.builder()
                .batchId(batchId)
                .batchName(batch.getBatchName())
                .appraisalPeriod(batch.getAppraisalPeriod())
                .status(batch.getStatus())
                .metrics(metrics)
                .leaders(leaderHeaders)
                .rows(summaryRows)
                .build();
    }

    @Override
    public ExamineePersonalResultVO getExamineePersonalResult(Long batchId) {
        Long currentUserId = UserContext.getUserId();
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }
        if (!"COMPLETED".equals(batch.getStatus())) {
            throw new BusinessException(400, "该批次月度考核尚未归档公布，请等待管理员归档后查看最终考核结果与排名");
        }

        MonthlySummaryVO summary = getMonthlyBatchSummary(batchId);

        MonthlySummaryVO.SummaryRowVO myRow = summary.getRows().stream()
                .filter(r -> r.getExamineeUserId().equals(currentUserId))
                .findFirst()
                .orElse(null);

        if (myRow == null) {
            throw new BusinessException(403, "您不在该月度考核名单中");
        }

        // 规则：月度绩效结果只能看到自己总分和排名，不返回其他人员得分与榜单明细
        return ExamineePersonalResultVO.builder()
                .batchId(batchId)
                .batchName(summary.getBatchName())
                .appraisalPeriod(summary.getAppraisalPeriod())
                .myCompositeScore(myRow.getCompositeScore())
                .myRank(myRow.getRank())
                .totalExaminees(summary.getRows().size())
                .rankList(Collections.emptyList())
                .build();
    }

    @Override
    public byte[] exportMonthlyBatchExcel(Long batchId) {
        MonthlySummaryVO summary = getMonthlyBatchSummary(batchId);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<List<String>> head = new ArrayList<>();
            head.add(List.of("排名"));
            head.add(List.of("姓名"));
            head.add(List.of("部门"));
            head.add(List.of("职务"));

            // 1. 各指标综合得分列
            if (summary.getMetrics() != null) {
                for (AppraisalMetric m : summary.getMetrics()) {
                    head.add(List.of(m.getMetricName() + " (" + m.getMaxScore().stripTrailingZeros().toPlainString() + "分)"));
                }
            }

            // 2. 各领导总分列
            for (MonthlySummaryVO.LeaderHeaderVO l : summary.getLeaders()) {
                head.add(List.of(l.getRealName() + " (" + l.getWeight().stripTrailingZeros().toPlainString() + "%)"));
            }
            head.add(List.of("综合得分"));

            List<List<Object>> dataList = new ArrayList<>();
            for (MonthlySummaryVO.SummaryRowVO r : summary.getRows()) {
                List<Object> row = new ArrayList<>();
                row.add(r.getRank());
                row.add(r.getRealName());
                row.add(r.getDepartment());
                row.add(r.getPosition());

                // 各指标综合得分
                if (summary.getMetrics() != null) {
                    for (AppraisalMetric m : summary.getMetrics()) {
                        BigDecimal ms = r.getMetricAvgScores() != null ? r.getMetricAvgScores().get(m.getId()) : null;
                        row.add(ms != null ? ms.setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                    }
                }

                // 各领导总分
                for (MonthlySummaryVO.LeaderHeaderVO l : summary.getLeaders()) {
                    BigDecimal score = r.getLeaderScores().get(l.getLeaderUserId());
                    row.add(score != null ? score.setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                }
                row.add(r.getCompositeScore().setScale(4, RoundingMode.HALF_UP).toString());
                dataList.add(row);
            }

            EasyExcel.write(out)
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet(summary.getAppraisalPeriod() + "月度绩效汇总")
                    .head(head)
                    .doWrite(dataList);

            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "导出 Excel 失败: " + e.getMessage());
        }
    }
}
