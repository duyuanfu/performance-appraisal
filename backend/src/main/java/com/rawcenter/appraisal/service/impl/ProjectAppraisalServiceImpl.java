package com.rawcenter.appraisal.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rawcenter.appraisal.common.BusinessException;
import com.rawcenter.appraisal.common.UserContext;
import com.rawcenter.appraisal.dto.ExpertScoreSubmitDTO;
import com.rawcenter.appraisal.dto.ProjectBatchSaveDTO;
import com.rawcenter.appraisal.dto.ProjectItemSaveDTO;
import com.rawcenter.appraisal.entity.*;
import com.rawcenter.appraisal.mapper.*;
import com.rawcenter.appraisal.service.MetricService;
import com.rawcenter.appraisal.service.ProjectAppraisalService;
import com.rawcenter.appraisal.vo.AnnualProjectSummaryVO;
import com.rawcenter.appraisal.vo.ExpertEvaluationVO;
import com.rawcenter.appraisal.vo.ProjectItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectAppraisalServiceImpl implements ProjectAppraisalService {

    private final AppraisalBatchMapper batchMapper;
    private final ProjectItemMapper projectItemMapper;
    private final ProjectExpertMapper projectExpertMapper;
    private final ProjectScoreRecordMapper scoreRecordMapper;
    private final ProjectScoreDetailMapper scoreDetailMapper;
    private final SysUserMapper userMapper;
    private final MetricService metricService;

    @Value("${app.upload.dir:./data/uploads/projects}")
    private String uploadDir;

    @Override
    public List<AppraisalBatch> listProjectBatches(String status, String keyword) {
        Long currentUserId = UserContext.getUserId();
        String activeRole = UserContext.get() != null ? UserContext.get().getActiveRole() : null;

        LambdaQueryWrapper<AppraisalBatch> wrapper = new LambdaQueryWrapper<AppraisalBatch>()
                .eq(AppraisalBatch::getBatchType, "ANNUAL_PROJECT")
                .eq(StringUtils.hasText(status), AppraisalBatch::getStatus, status)
                .like(StringUtils.hasText(keyword), AppraisalBatch::getBatchName, keyword);

        // 数据权限隔离：评审专家角色仅可见分配给自己评审的批次，且未发布的草稿对专家完全不可见！
        if ("EXPERT".equalsIgnoreCase(activeRole) && currentUserId != null) {
            List<ProjectExpert> myAssignments = projectExpertMapper.selectList(new LambdaQueryWrapper<ProjectExpert>()
                    .eq(ProjectExpert::getExpertUserId, currentUserId));
            if (myAssignments.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> allowedBatchIds = myAssignments.stream().map(ProjectExpert::getBatchId).collect(Collectors.toList());
            wrapper.in(AppraisalBatch::getId, allowedBatchIds);
            wrapper.ne(AppraisalBatch::getStatus, "DRAFT");
        }

        wrapper.orderByDesc(AppraisalBatch::getCreatedAt);
        return batchMapper.selectList(wrapper);
    }

    @Override
    public AppraisalBatch getProjectBatchDetail(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }
        return batch;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveProjectBatch(ProjectBatchSaveDTO dto) {
        // 校验唯一性：一年只能建立一个年度目标管理项目评审流程
        Long existingBatchCount = batchMapper.selectCount(new LambdaQueryWrapper<AppraisalBatch>()
                .eq(AppraisalBatch::getBatchType, "ANNUAL_PROJECT")
                .eq(AppraisalBatch::getAppraisalPeriod, dto.getAppraisalPeriod().trim())
                .ne(dto.getId() != null, AppraisalBatch::getId, dto.getId()));
        if (existingBatchCount > 0) {
            throw new BusinessException(400, "该年度【" + dto.getAppraisalPeriod().trim() + "】已存在项目评审批次，系统规定一年只能建立一个评审流程！");
        }

        // 校验专家权重：保持与月度绩效一致，权重合计必须为 100%
        BigDecimal totalExpertWeight = BigDecimal.ZERO;
        for (ProjectBatchSaveDTO.ExpertWeightDTO ew : dto.getExperts()) {
            if (ew.getWeight() == null || ew.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "评审专家打分权重必须大于0");
            }
            totalExpertWeight = totalExpertWeight.add(ew.getWeight());
        }
        BigDecimal weightDiff = totalExpertWeight.subtract(new BigDecimal("100.0000")).abs();
        if (weightDiff.compareTo(new BigDecimal("0.0001")) > 0) {
            throw new BusinessException(400, "评审专家权重合计必须为100%，当前为：" + totalExpertWeight.stripTrailingZeros().toPlainString() + "%");
        }

        AppraisalBatch batch;
        if (dto.getId() == null) {
            batch = AppraisalBatch.builder()
                    .batchName(dto.getBatchName())
                    .batchType("ANNUAL_PROJECT")
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
            // 重新发起时将状态置为进行中，允许专家继续打分
            batch.setStatus("IN_PROGRESS");
            batch.setUpdatedAt(LocalDateTime.now());
            batchMapper.updateById(batch);
        }

        Long batchId = batch.getId();

        // 1. Save Metrics
        metricService.saveBatchMetrics(batchId, dto.getMetrics());

        // 2. Save Experts with weights
        projectExpertMapper.delete(new LambdaQueryWrapper<ProjectExpert>().eq(ProjectExpert::getBatchId, batchId));
        for (ProjectBatchSaveDTO.ExpertWeightDTO ew : dto.getExperts()) {
            projectExpertMapper.insert(ProjectExpert.builder()
                    .batchId(batchId)
                    .expertUserId(ew.getExpertUserId())
                    .weight(ew.getWeight() != null ? ew.getWeight() : new BigDecimal("1.0000"))
                    .build());
        }
    }

    @Override
    public void publishProjectBatch(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        Long projectCount = projectItemMapper.selectCount(new LambdaQueryWrapper<ProjectItem>()
                .eq(ProjectItem::getBatchId, batchId));
        if (projectCount == 0) {
            throw new BusinessException(400, "该批次尚未录入参评项目，无法发布。请先录入参评项目后再发布任务！");
        }

        Long expertCount = projectExpertMapper.selectCount(new LambdaQueryWrapper<ProjectExpert>()
                .eq(ProjectExpert::getBatchId, batchId));
        if (expertCount == 0) {
            throw new BusinessException(400, "该批次尚未配置评审专家，无法发布。请先配置评审专家后再发布任务！");
        }

        batch.setStatus("IN_PROGRESS");
        if (batch.getStartTime() == null) {
            batch.setStartTime(LocalDateTime.now());
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    @Override
    public void completeProjectBatch(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }
        batch.setStatus("COMPLETED");
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    @Override
    public List<ProjectItemVO> listProjectItems(Long batchId) {
        List<ProjectItem> items = projectItemMapper.selectList(new LambdaQueryWrapper<ProjectItem>()
                .eq(ProjectItem::getBatchId, batchId)
                .orderByAsc(ProjectItem::getSortOrder)
                .orderByAsc(ProjectItem::getId));

        return items.stream().map(item -> ProjectItemVO.builder()
                .id(item.getId())
                .batchId(item.getBatchId())
                .projectCode(item.getProjectCode())
                .projectName(item.getProjectName())
                .ownerName(item.getOwnerName())
                .materialPdfName(item.getMaterialPdfName())
                .hasMaterial(StringUtils.hasText(item.getMaterialPdfPath()))
                .attachmentPdfName(item.getAttachmentPdfName())
                .hasAttachment(StringUtils.hasText(item.getAttachmentPdfPath()))
                .sortOrder(item.getSortOrder())
                .createdAt(item.getCreatedAt())
                .build()).collect(Collectors.toList());
    }

    @Override
    public void saveProjectItem(ProjectItemSaveDTO dto) {
        if (dto.getId() == null) {
            ProjectItem item = ProjectItem.builder()
                    .batchId(dto.getBatchId())
                    .projectCode(dto.getProjectCode())
                    .projectName(dto.getProjectName())
                    .ownerName(dto.getOwnerName())
                    .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 1)
                    .materialPdfName("")
                    .materialPdfPath("")
                    .attachmentPdfName("")
                    .attachmentPdfPath("")
                    .createdAt(LocalDateTime.now())
                    .build();
            projectItemMapper.insert(item);
        } else {
            ProjectItem item = projectItemMapper.selectById(dto.getId());
            if (item == null) {
                throw new BusinessException(404, "项目不存在");
            }
            item.setProjectCode(dto.getProjectCode());
            item.setProjectName(dto.getProjectName());
            item.setOwnerName(dto.getOwnerName());
            if (dto.getSortOrder() != null) {
                item.setSortOrder(dto.getSortOrder());
            }
            projectItemMapper.updateById(item);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProjectItem(Long id) {
        ProjectItem item = projectItemMapper.selectById(id);
        if (item == null) return;

        // Clean score records for this project
        List<ProjectScoreRecord> records = scoreRecordMapper.selectList(new LambdaQueryWrapper<ProjectScoreRecord>()
                .eq(ProjectScoreRecord::getProjectId, id));
        for (ProjectScoreRecord r : records) {
            scoreDetailMapper.delete(new LambdaQueryWrapper<ProjectScoreDetail>()
                    .eq(ProjectScoreDetail::getRecordId, r.getId()));
            scoreRecordMapper.deleteById(r.getId());
        }

        projectItemMapper.deleteById(id);
    }

    @Override
    public void uploadProjectPdf(Long projectId, String type, MultipartFile file) {
        ProjectItem item = projectItemMapper.selectById(projectId);
        if (item == null) {
            throw new BusinessException(404, "项目不存在");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException(400, "仅支持上传 PDF 格式文件");
        }

        File baseDir = new File(uploadDir).getAbsoluteFile();
        File targetDir = new File(baseDir, String.valueOf(projectId));
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        String savedFilename = type + "_" + System.currentTimeMillis() + ".pdf";
        File targetFile = new File(targetDir, savedFilename);

        try (java.io.InputStream in = file.getInputStream()) {
            java.nio.file.Files.copy(in, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(500, "保存 PDF 文件失败: " + e.getMessage());
        }

        if ("material".equalsIgnoreCase(type)) {
            item.setMaterialPdfName(originalFilename);
            item.setMaterialPdfPath(targetFile.getAbsolutePath());
        } else {
            item.setAttachmentPdfName(originalFilename);
            item.setAttachmentPdfPath(targetFile.getAbsolutePath());
        }
        projectItemMapper.updateById(item);
    }

    @Override
    public File getProjectPdfFile(Long projectId, String type) {
        ProjectItem item = projectItemMapper.selectById(projectId);
        if (item == null) {
            throw new BusinessException(404, "项目不存在");
        }

        String path = "material".equalsIgnoreCase(type) ? item.getMaterialPdfPath() : item.getAttachmentPdfPath();
        if (!StringUtils.hasText(path)) {
            throw new BusinessException(404, "该项目尚未上传对应 PDF 文件");
        }

        File file = new File(path);
        if (!file.exists()) {
            throw new BusinessException(404, "服务器未找到 PDF 文件实体");
        }
        return file;
    }

    @Override
    public ExpertEvaluationVO getExpertEvaluationData(Long batchId) {
        Long expertUserId = UserContext.getUserId();
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        if ("DRAFT".equals(batch.getStatus())) {
            throw new BusinessException(400, "该项目评审批次尚未正式发布，暂不可开展评审打分");
        }

        ProjectExpert expert = projectExpertMapper.selectOne(new LambdaQueryWrapper<ProjectExpert>()
                .eq(ProjectExpert::getBatchId, batchId)
                .eq(ProjectExpert::getExpertUserId, expertUserId));
        if (expert == null) {
            throw new BusinessException(403, "您不是该批次的评审专家");
        }

        List<AppraisalMetric> metrics = metricService.listMetricsByBatchId(batchId);
        List<ProjectItem> projects = projectItemMapper.selectList(new LambdaQueryWrapper<ProjectItem>()
                .eq(ProjectItem::getBatchId, batchId)
                .orderByAsc(ProjectItem::getSortOrder)
                .orderByAsc(ProjectItem::getId));

        List<ProjectScoreRecord> records = scoreRecordMapper.selectList(new LambdaQueryWrapper<ProjectScoreRecord>()
                .eq(ProjectScoreRecord::getBatchId, batchId)
                .eq(ProjectScoreRecord::getExpertUserId, expertUserId));
        Map<Long, ProjectScoreRecord> recordMap = records.stream()
                .collect(Collectors.toMap(ProjectScoreRecord::getProjectId, r -> r));

        String submissionStatus = "DRAFT";
        if (!records.isEmpty()) {
            boolean allSubmitted = records.size() == projects.size() && records.stream().allMatch(r -> "SUBMITTED".equals(r.getStatus()));
            if (allSubmitted) {
                submissionStatus = "SUBMITTED";
            }
        }

        List<ExpertEvaluationVO.ProjectEvaluationRowVO> projectRows = new ArrayList<>();
        for (ProjectItem p : projects) {
            ProjectScoreRecord rec = recordMap.get(p.getId());
            Map<Long, BigDecimal> metricScoreMap = new HashMap<>();
            BigDecimal totalScore = BigDecimal.ZERO;

            if (rec != null) {
                totalScore = rec.getTotalScore();
                List<ProjectScoreDetail> details = scoreDetailMapper.selectList(new LambdaQueryWrapper<ProjectScoreDetail>()
                        .eq(ProjectScoreDetail::getRecordId, rec.getId()));
                for (ProjectScoreDetail d : details) {
                    metricScoreMap.put(d.getMetricId(), d.getScore());
                }
            }

            projectRows.add(ExpertEvaluationVO.ProjectEvaluationRowVO.builder()
                    .projectId(p.getId())
                    .projectCode(p.getProjectCode())
                    .projectName(p.getProjectName())
                    .ownerName(p.getOwnerName())
                    .materialPdfName(p.getMaterialPdfName())
                    .hasMaterial(StringUtils.hasText(p.getMaterialPdfPath()))
                    .attachmentPdfName(p.getAttachmentPdfName())
                    .hasAttachment(StringUtils.hasText(p.getAttachmentPdfPath()))
                    .metricScores(metricScoreMap)
                    .totalScore(totalScore)
                    .build());
        }

        boolean isExpired = batch.getEndTime() != null && LocalDateTime.now().isAfter(batch.getEndTime());

        return ExpertEvaluationVO.builder()
                .batchId(batchId)
                .batchName(batch.getBatchName())
                .appraisalPeriod(batch.getAppraisalPeriod())
                .endTime(batch.getEndTime())
                .isExpired(isExpired)
                .batchStatus(batch.getStatus())
                .mySubmissionStatus(submissionStatus)
                .metrics(metrics)
                .projects(projectRows)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveExpertScores(ExpertScoreSubmitDTO dto) {
        Long expertUserId = UserContext.getUserId();
        AppraisalBatch batch = batchMapper.selectById(dto.getBatchId());
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        if (!"IN_PROGRESS".equals(batch.getStatus())) {
            if ("DRAFT".equals(batch.getStatus())) {
                throw new BusinessException(400, "该项目评审批次尚未正式发布，暂不可进行评分打分");
            }
            throw new BusinessException(400, "该项目评审批次已归档封存，评分数据已锁定，无法修改");
        }

        if (batch.getEndTime() != null && LocalDateTime.now().isAfter(batch.getEndTime())) {
            throw new BusinessException(400, "项目评审截止时间已过，无法保存或提交评分");
        }

        List<AppraisalMetric> metrics = metricService.listMetricsByBatchId(dto.getBatchId());
        Map<Long, AppraisalMetric> metricMap = metrics.stream().collect(Collectors.toMap(AppraisalMetric::getId, m -> m));

        boolean isSubmit = Boolean.TRUE.equals(dto.getIsSubmit());

        if (CollectionUtils.isEmpty(dto.getItems())) {
            throw new BusinessException(400, "评分数据不能为空");
        }

        for (ExpertScoreSubmitDTO.ProjectScoreItemDTO item : dto.getItems()) {
            BigDecimal totalScore = BigDecimal.ZERO;

            if (isSubmit) {
                for (AppraisalMetric m : metrics) {
                    BigDecimal score = item.getMetricScores() != null ? item.getMetricScores().get(m.getId()) : null;
                    if (score == null) {
                        throw new BusinessException(400, "项目指标得分未填完整，请填写所有指标评分后再正式提交");
                    }
                    if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(m.getMaxScore()) > 0) {
                        throw new BusinessException(400, "指标【" + m.getMetricName() + "】分值必须在 0 ~ " + m.getMaxScore() + " 之间");
                    }
                    BigDecimal weightedScore = score.multiply(m.getWeight())
                            .divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP);
                    totalScore = totalScore.add(weightedScore);
                }
            } else {
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

            ProjectScoreRecord record = scoreRecordMapper.selectOne(new LambdaQueryWrapper<ProjectScoreRecord>()
                    .eq(ProjectScoreRecord::getBatchId, dto.getBatchId())
                    .eq(ProjectScoreRecord::getExpertUserId, expertUserId)
                    .eq(ProjectScoreRecord::getProjectId, item.getProjectId()));

            if (record == null) {
                record = ProjectScoreRecord.builder()
                        .batchId(dto.getBatchId())
                        .expertUserId(expertUserId)
                        .projectId(item.getProjectId())
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

            scoreDetailMapper.delete(new LambdaQueryWrapper<ProjectScoreDetail>()
                    .eq(ProjectScoreDetail::getRecordId, record.getId()));

            if (item.getMetricScores() != null) {
                for (Map.Entry<Long, BigDecimal> entry : item.getMetricScores().entrySet()) {
                    if (entry.getValue() != null) {
                        scoreDetailMapper.insert(ProjectScoreDetail.builder()
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
    public void withdrawExpertScores(Long batchId) {
        Long expertUserId = UserContext.getUserId();
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        if (!"IN_PROGRESS".equals(batch.getStatus())) {
            if ("DRAFT".equals(batch.getStatus())) {
                throw new BusinessException(400, "该项目评审批次尚未正式发布");
            }
            throw new BusinessException(400, "该项目评审批次已归档封存，评分数据已锁定，无法撤回修改评分");
        }

        if (batch.getEndTime() != null && LocalDateTime.now().isAfter(batch.getEndTime())) {
            throw new BusinessException(400, "评审截止时间已过，无法撤回评分");
        }

        List<ProjectScoreRecord> records = scoreRecordMapper.selectList(new LambdaQueryWrapper<ProjectScoreRecord>()
                .eq(ProjectScoreRecord::getBatchId, batchId)
                .eq(ProjectScoreRecord::getExpertUserId, expertUserId));
        if (records.isEmpty()) {
            throw new BusinessException(400, "尚未提交过评分，无需撤回");
        }

        for (ProjectScoreRecord r : records) {
            r.setStatus("DRAFT");
            scoreRecordMapper.updateById(r);
        }
    }

    @Override
    public List<com.rawcenter.appraisal.vo.EvaluatorProgressVO> getExpertProgress(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        List<ProjectExpert> experts = projectExpertMapper.selectList(new LambdaQueryWrapper<ProjectExpert>()
                .eq(ProjectExpert::getBatchId, batchId));
        if (experts.isEmpty()) {
            return Collections.emptyList();
        }

        Long projectCount = projectItemMapper.selectCount(new LambdaQueryWrapper<ProjectItem>()
                .eq(ProjectItem::getBatchId, batchId));

        List<Long> expertUserIds = experts.stream().map(ProjectExpert::getExpertUserId).collect(Collectors.toList());
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(expertUserIds)
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        List<ProjectScoreRecord> allRecords = scoreRecordMapper.selectList(new LambdaQueryWrapper<ProjectScoreRecord>()
                .eq(ProjectScoreRecord::getBatchId, batchId));

        Map<Long, List<ProjectScoreRecord>> recordsByExpert = allRecords.stream()
                .collect(Collectors.groupingBy(ProjectScoreRecord::getExpertUserId));

        List<com.rawcenter.appraisal.vo.EvaluatorProgressVO> result = new ArrayList<>();
        for (ProjectExpert exp : experts) {
            SysUser u = userMap.get(exp.getExpertUserId());
            List<ProjectScoreRecord> records = recordsByExpert.getOrDefault(exp.getExpertUserId(), Collections.emptyList());

            String status = "NOT_STARTED";
            LocalDateTime submittedAt = null;

            if (!records.isEmpty()) {
                boolean allSubmitted = records.size() == projectCount && records.stream().allMatch(r -> "SUBMITTED".equals(r.getStatus()));
                if (allSubmitted) {
                    status = "SUBMITTED";
                    submittedAt = records.get(0).getSubmittedAt();
                } else {
                    status = "DRAFT";
                }
            }

            result.add(com.rawcenter.appraisal.vo.EvaluatorProgressVO.builder()
                    .userId(exp.getExpertUserId())
                    .realName(u != null ? u.getRealName() : "未知专家")
                    .department(u != null ? u.getDepartment() : "")
                    .position(u != null ? u.getPosition() : "")
                    .phone(u != null ? u.getPhone() : "")
                    .weight(exp.getWeight())
                    .status(status)
                    .scoredCount(records.size())
                    .totalTargetCount(projectCount.intValue())
                    .submittedAt(submittedAt)
                    .build());
        }

        return result;
    }

    @Override
    public void urgeExpert(Long batchId, Long expertUserId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }
        if (!"IN_PROGRESS".equals(batch.getStatus())) {
            throw new BusinessException(400, "仅支持对进行中的评审批次发送催促提醒");
        }
    }

    @Override
    public AnnualProjectSummaryVO getProjectSummary(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }

        // 1. Get Experts
        List<ProjectExpert> experts = projectExpertMapper.selectList(new LambdaQueryWrapper<ProjectExpert>()
                .eq(ProjectExpert::getBatchId, batchId));
        List<Long> expertUserIds = experts.stream().map(ProjectExpert::getExpertUserId).collect(Collectors.toList());
        Map<Long, SysUser> expertUserMap = expertUserIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(expertUserIds).stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        List<ProjectScoreRecord> allRecords = scoreRecordMapper.selectList(new LambdaQueryWrapper<ProjectScoreRecord>()
                .eq(ProjectScoreRecord::getBatchId, batchId));
        Map<String, ProjectScoreRecord> recordKeyMap = allRecords.stream()
                .collect(Collectors.toMap(r -> r.getExpertUserId() + "_" + r.getProjectId(), r -> r, (a, b) -> a));

        Set<Long> submittedExperts = allRecords.stream()
                .filter(r -> "SUBMITTED".equals(r.getStatus()))
                .map(ProjectScoreRecord::getExpertUserId)
                .collect(Collectors.toSet());

        List<AnnualProjectSummaryVO.ExpertHeaderVO> expertHeaders = new ArrayList<>();
        BigDecimal totalExpertWeight = BigDecimal.ZERO;
        for (ProjectExpert exp : experts) {
            SysUser u = expertUserMap.get(exp.getExpertUserId());
            expertHeaders.add(AnnualProjectSummaryVO.ExpertHeaderVO.builder()
                    .expertUserId(exp.getExpertUserId())
                    .realName(u != null ? u.getRealName() : "专家")
                    .weight(exp.getWeight())
                    .submitted(submittedExperts.contains(exp.getExpertUserId()))
                    .build());
            totalExpertWeight = totalExpertWeight.add(exp.getWeight());
        }

        // 2. Metrics & Projects
        List<AppraisalMetric> metrics = metricService.listMetricsByBatchId(batchId);
        List<ProjectItem> projects = projectItemMapper.selectList(new LambdaQueryWrapper<ProjectItem>()
                .eq(ProjectItem::getBatchId, batchId)
                .orderByAsc(ProjectItem::getSortOrder)
                .orderByAsc(ProjectItem::getId));

        List<AnnualProjectSummaryVO.ProjectSummaryRowVO> summaryRows = new ArrayList<>();

        for (ProjectItem p : projects) {
            Map<Long, BigDecimal> expertScoreMap = new HashMap<>();
            BigDecimal weightedTotal = BigDecimal.ZERO;

            for (ProjectExpert exp : experts) {
                ProjectScoreRecord rec = recordKeyMap.get(exp.getExpertUserId() + "_" + p.getId());
                BigDecimal score = BigDecimal.ZERO;
                if (rec != null && "SUBMITTED".equals(rec.getStatus())) {
                    score = rec.getTotalScore();
                }
                expertScoreMap.put(exp.getExpertUserId(), score);

                // Add to weighted total: score * weight
                weightedTotal = weightedTotal.add(score.multiply(exp.getWeight()));
            }

            // Weighted average composite score = weightedTotal / totalExpertWeight
            BigDecimal compositeScore = BigDecimal.ZERO;
            if (totalExpertWeight.compareTo(BigDecimal.ZERO) > 0) {
                compositeScore = weightedTotal.divide(totalExpertWeight, 4, RoundingMode.HALF_UP);
            }

            // Calculate metric average scores across submitted experts
            Map<Long, BigDecimal> metricAvgScores = new HashMap<>();
            for (AppraisalMetric m : metrics) {
                BigDecimal metricSum = BigDecimal.ZERO;
                int count = 0;
                for (ProjectExpert exp : experts) {
                    ProjectScoreRecord rec = recordKeyMap.get(exp.getExpertUserId() + "_" + p.getId());
                    if (rec != null && "SUBMITTED".equals(rec.getStatus())) {
                        ProjectScoreDetail detail = scoreDetailMapper.selectOne(new LambdaQueryWrapper<ProjectScoreDetail>()
                                .eq(ProjectScoreDetail::getRecordId, rec.getId())
                                .eq(ProjectScoreDetail::getMetricId, m.getId()));
                        if (detail != null) {
                            metricSum = metricSum.add(detail.getScore());
                            count++;
                        }
                    }
                }
                BigDecimal avg = count > 0
                        ? metricSum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                metricAvgScores.put(m.getId(), avg);
            }

            summaryRows.add(AnnualProjectSummaryVO.ProjectSummaryRowVO.builder()
                    .projectId(p.getId())
                    .projectCode(p.getProjectCode())
                    .projectName(p.getProjectName())
                    .ownerName(p.getOwnerName())
                    .metricAvgScores(metricAvgScores)
                    .expertScores(expertScoreMap)
                    .compositeScore(compositeScore)
                    .rank(1)
                    .build());
        }

        // Sort descending by compositeScore and assign ranks
        summaryRows.sort((a, b) -> b.getCompositeScore().compareTo(a.getCompositeScore()));
        for (int i = 0; i < summaryRows.size(); i++) {
            summaryRows.get(i).setRank(i + 1);
        }

        return AnnualProjectSummaryVO.builder()
                .batchId(batchId)
                .batchName(batch.getBatchName())
                .appraisalPeriod(batch.getAppraisalPeriod())
                .status(batch.getStatus())
                .metrics(metrics)
                .experts(expertHeaders)
                .rows(summaryRows)
                .build();
    }

    @Override
    public byte[] exportProjectSummaryExcel(Long batchId) {
        AnnualProjectSummaryVO summary = getProjectSummary(batchId);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<List<String>> head = new ArrayList<>();
            head.add(List.of("项目序号"));
            head.add(List.of("项目名称"));
            head.add(List.of("项目负责人"));

            // Metrics columns
            for (AppraisalMetric m : summary.getMetrics()) {
                head.add(List.of(m.getMetricName() + " (" + m.getMaxScore().stripTrailingZeros().toPlainString() + "分)"));
            }

            // Experts columns
            for (AnnualProjectSummaryVO.ExpertHeaderVO e : summary.getExperts()) {
                head.add(List.of(e.getRealName() + " (" + e.getWeight().stripTrailingZeros().toPlainString() + "%)"));
            }

            head.add(List.of("总分 (加权平均)"));
            head.add(List.of("排名"));

            List<List<Object>> dataList = new ArrayList<>();
            for (AnnualProjectSummaryVO.ProjectSummaryRowVO r : summary.getRows()) {
                List<Object> row = new ArrayList<>();
                row.add(r.getProjectCode());
                row.add(r.getProjectName());
                row.add(r.getOwnerName());

                for (AppraisalMetric m : summary.getMetrics()) {
                    BigDecimal avg = r.getMetricAvgScores().get(m.getId());
                    row.add(avg != null ? avg.setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                }

                for (AnnualProjectSummaryVO.ExpertHeaderVO e : summary.getExperts()) {
                    BigDecimal score = r.getExpertScores().get(e.getExpertUserId());
                    row.add(score != null ? score.setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                }

                row.add(r.getCompositeScore().setScale(4, RoundingMode.HALF_UP).toString());
                row.add(r.getRank());
                dataList.add(row);
            }

            EasyExcel.write(out)
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet("年度目标管理项目评审")
                    .head(head)
                    .doWrite(dataList);

            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "导出 Excel 失败: " + e.getMessage());
        }
    }
}
