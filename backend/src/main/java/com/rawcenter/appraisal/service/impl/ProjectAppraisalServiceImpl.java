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
    private final AppraisalMetricMapper metricMapper;
    private final MetricService metricService;

    @Value("${app.upload.dir:./data/uploads/projects}")
    private String uploadDir;

    @Override
    public List<AppraisalBatch> listProjectBatches(String status, String keyword) {
        return listProjectBatches(null, status, keyword);
    }

    @Override
    public List<AppraisalBatch> listProjectBatches(String batchType, String status, String keyword) {
        Long currentUserId = UserContext.getUserId();
        String activeRole = UserContext.get() != null ? UserContext.get().getActiveRole() : null;

        LambdaQueryWrapper<AppraisalBatch> wrapper = new LambdaQueryWrapper<AppraisalBatch>()
                .eq(StringUtils.hasText(status), AppraisalBatch::getStatus, status)
                .like(StringUtils.hasText(keyword), AppraisalBatch::getBatchName, keyword);

        if (StringUtils.hasText(batchType)) {
            if ("TARGET_PROJECT".equalsIgnoreCase(batchType)) {
                wrapper.in(AppraisalBatch::getBatchType, Arrays.asList("TARGET_PROJECT", "ANNUAL_PROJECT"));
            } else {
                wrapper.eq(AppraisalBatch::getBatchType, batchType);
            }
        } else {
            wrapper.in(AppraisalBatch::getBatchType, Arrays.asList("TARGET_PROJECT", "SPECIAL_INNOVATION", "ANNUAL_PROJECT"));
        }

        // 数据权限隔离：评审专家/领导角色仅可见分配给自己评审的批次，且未发布的草稿对专家完全不可见！
        if (("EXPERT".equalsIgnoreCase(activeRole) || "LEADER".equalsIgnoreCase(activeRole)) && currentUserId != null) {
            List<ProjectExpert> myAssignments = projectExpertMapper.selectList(new LambdaQueryWrapper<ProjectExpert>()
                    .eq(ProjectExpert::getExpertUserId, currentUserId));
            if (!myAssignments.isEmpty()) {
                List<Long> allowedBatchIds = myAssignments.stream().map(ProjectExpert::getBatchId).collect(Collectors.toList());
                wrapper.in(AppraisalBatch::getId, allowedBatchIds);
            }
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
        // 校验唯一性：取消一年一个的限制，只要评审批次名称不同即可
        Long existingBatchCount = batchMapper.selectCount(new LambdaQueryWrapper<AppraisalBatch>()
                .in(AppraisalBatch::getBatchType, Arrays.asList("TARGET_PROJECT", "SPECIAL_INNOVATION", "ANNUAL_PROJECT"))
                .eq(AppraisalBatch::getBatchName, dto.getBatchName().trim())
                .ne(dto.getId() != null, AppraisalBatch::getId, dto.getId()));
        if (existingBatchCount > 0) {
            throw new BusinessException(400, "评审批次名称【" + dto.getBatchName().trim() + "】已存在，请使用不同的批次名称！");
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

        String batchType = StringUtils.hasText(dto.getBatchType()) ? dto.getBatchType() : "TARGET_PROJECT";

        AppraisalBatch batch;
        if (dto.getId() == null) {
            batch = AppraisalBatch.builder()
                    .batchName(dto.getBatchName())
                    .batchType(batchType)
                    .category(dto.getCategory() != null ? dto.getCategory() : "")
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
            if (StringUtils.hasText(dto.getBatchType())) {
                batch.setBatchType(dto.getBatchType());
            }
            if (dto.getCategory() != null) {
                batch.setCategory(dto.getCategory());
            }
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
                .category(item.getCategory())
                .department(item.getDepartment())
                .awardTier(item.getAwardTier())
                .scoreCoefficient(item.getScoreCoefficient())
                .bonusAmount(item.getBonusAmount())
                .rankNum(item.getRankNum())
                .materialPdfName(item.getMaterialPdfName())
                .hasMaterial(StringUtils.hasText(item.getMaterialPdfPath()))
                .attachmentPdfName(item.getAttachmentPdfName())
                .hasAttachment(StringUtils.hasText(item.getAttachmentPdfPath()))
                .sortOrder(item.getSortOrder())
                .createdAt(item.getCreatedAt())
                .build()).collect(Collectors.toList());
    }

    @Override
    public void deleteProjectBatch(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }
        List<ProjectScoreRecord> records = scoreRecordMapper.selectList(new LambdaQueryWrapper<ProjectScoreRecord>()
                .eq(ProjectScoreRecord::getBatchId, batchId));
        if (!records.isEmpty()) {
            List<Long> recordIds = records.stream().map(ProjectScoreRecord::getId).collect(Collectors.toList());
            scoreDetailMapper.delete(new LambdaQueryWrapper<ProjectScoreDetail>().in(ProjectScoreDetail::getRecordId, recordIds));
            scoreRecordMapper.delete(new LambdaQueryWrapper<ProjectScoreRecord>().eq(ProjectScoreRecord::getBatchId, batchId));
        }
        projectExpertMapper.delete(new LambdaQueryWrapper<ProjectExpert>().eq(ProjectExpert::getBatchId, batchId));
        projectItemMapper.delete(new LambdaQueryWrapper<ProjectItem>().eq(ProjectItem::getBatchId, batchId));
        metricMapper.delete(new LambdaQueryWrapper<AppraisalMetric>().eq(AppraisalMetric::getBatchId, batchId));
        batchMapper.deleteById(batchId);
    }

    @Override
    public void saveProjectItem(ProjectItemSaveDTO dto) {
        AppraisalBatch batch = batchMapper.selectById(dto.getBatchId());
        if (dto.getId() == null && batch != null && StringUtils.hasText(dto.getDepartment())) {
            String dept = dto.getDepartment().trim();
            if ("SPECIAL_INNOVATION".equalsIgnoreCase(batch.getBatchType())) {
                String cat = dto.getCategory() != null ? dto.getCategory().trim().toUpperCase() : "BUSINESS";
                if ("MANAGEMENT".equalsIgnoreCase(cat)) {
                    Long count = projectItemMapper.selectCount(new LambdaQueryWrapper<ProjectItem>()
                            .eq(ProjectItem::getBatchId, dto.getBatchId())
                            .eq(ProjectItem::getDepartment, dept)
                            .eq(ProjectItem::getCategory, "MANAGEMENT"));
                    if (count >= 2) {
                        throw new BusinessException(400, "部门【" + dept + "】申报管理类专项与创新任务已达到上限（最多2项）");
                    }
                } else {
                    Long count = projectItemMapper.selectCount(new LambdaQueryWrapper<ProjectItem>()
                            .eq(ProjectItem::getBatchId, dto.getBatchId())
                            .eq(ProjectItem::getDepartment, dept)
                            .ne(ProjectItem::getCategory, "MANAGEMENT"));
                    if (count >= 3) {
                        throw new BusinessException(400, "部门【" + dept + "】申报业务类专项与创新任务已达到上限（最多3项）");
                    }
                }
            } else if ("TARGET_PROJECT".equalsIgnoreCase(batch.getBatchType())) {
                Long count = projectItemMapper.selectCount(new LambdaQueryWrapper<ProjectItem>()
                        .eq(ProjectItem::getBatchId, dto.getBatchId())
                        .eq(ProjectItem::getDepartment, dept));
                if (count >= 1) {
                    throw new BusinessException(400, "部门【" + dept + "】申报目标绩效任务已达到上限（每部门仅限申报1项）");
                }
            }
        }

        if (dto.getId() == null) {
            ProjectItem item = ProjectItem.builder()
                    .batchId(dto.getBatchId())
                    .projectCode(dto.getProjectCode())
                    .projectName(dto.getProjectName())
                    .ownerName(dto.getOwnerName())
                    .category(dto.getCategory() != null ? dto.getCategory() : "")
                    .department(dto.getDepartment() != null ? dto.getDepartment() : "")
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
            if (dto.getCategory() != null) {
                item.setCategory(dto.getCategory());
            }
            if (dto.getDepartment() != null) {
                item.setDepartment(dto.getDepartment());
            }
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
        SysUser currentEvaluator = userMapper.selectById(expertUserId);
        String evaluatorPos = currentEvaluator != null && currentEvaluator.getPositionCategory() != null ? currentEvaluator.getPositionCategory() : "";

        if (expert == null) {
            boolean isAllowed = "PRIMARY_LEADER".equalsIgnoreCase(evaluatorPos)
                    || "CENTER_LEADER".equalsIgnoreCase(evaluatorPos)
                    || "DEPT_HEAD".equalsIgnoreCase(evaluatorPos)
                    || "EXPERT".equalsIgnoreCase(evaluatorPos);
            if (!isAllowed) {
                throw new BusinessException(403, "您不是该批次的评审人员");
            }
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

            SysUser currentEvaluator = userMapper.selectById(expertUserId);
            String evRoleType = currentEvaluator != null && currentEvaluator.getPositionCategory() != null
                    ? currentEvaluator.getPositionCategory() : "EXPERT";

            if (record == null) {
                record = ProjectScoreRecord.builder()
                        .batchId(dto.getBatchId())
                        .expertUserId(expertUserId)
                        .projectId(item.getProjectId())
                        .evaluatorRoleType(evRoleType)
                        .totalScore(totalScore)
                        .status(isSubmit ? "SUBMITTED" : "DRAFT")
                        .submittedAt(isSubmit ? LocalDateTime.now() : null)
                        .build();
                scoreRecordMapper.insert(record);
            } else {
                record.setTotalScore(totalScore);
                record.setEvaluatorRoleType(evRoleType);
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
                weightedTotal = weightedTotal.add(score.multiply(exp.getWeight()));
            }

            // Category score collection for multi-tier formulas
            BigDecimal primaryScore = BigDecimal.ZERO;
            BigDecimal supervisorScore = BigDecimal.ZERO;
            List<BigDecimal> otherLeaderScores = new ArrayList<>();
            List<BigDecimal> headScores = new ArrayList<>();
            List<BigDecimal> expertScoresList = new ArrayList<>();

            Long supervisorId = null;
            if (StringUtils.hasText(p.getDepartment())) {
                List<SysUser> deptHeads = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getDepartment, p.getDepartment())
                        .eq(SysUser::getPositionCategory, "DEPT_HEAD"));
                if (!deptHeads.isEmpty()) {
                    supervisorId = deptHeads.get(0).getSupervisorId();
                }
            }

            for (ProjectScoreRecord rec : allRecords) {
                if (!rec.getProjectId().equals(p.getId())) continue;
                if (!"SUBMITTED".equals(rec.getStatus())) continue;

                SysUser evUser = userMapper.selectById(rec.getExpertUserId());
                if (evUser == null) continue;
                String pos = evUser.getPositionCategory();

                if ("PRIMARY_LEADER".equalsIgnoreCase(pos)) {
                    primaryScore = rec.getTotalScore();
                } else if ("CENTER_LEADER".equalsIgnoreCase(pos)) {
                    if (supervisorId != null && supervisorId.equals(rec.getExpertUserId())) {
                        supervisorScore = rec.getTotalScore();
                    } else {
                        otherLeaderScores.add(rec.getTotalScore());
                    }
                } else if ("DEPT_HEAD".equalsIgnoreCase(pos)) {
                    headScores.add(rec.getTotalScore());
                } else if ("EXPERT".equalsIgnoreCase(pos)) {
                    expertScoresList.add(rec.getTotalScore());
                }
            }

            BigDecimal otherLeaderAvg = otherLeaderScores.isEmpty() ? BigDecimal.ZERO
                    : otherLeaderScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(otherLeaderScores.size()), 4, RoundingMode.HALF_UP);

            BigDecimal headAvg = headScores.isEmpty() ? BigDecimal.ZERO
                    : headScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(headScores.size()), 4, RoundingMode.HALF_UP);

            BigDecimal expertAvg = expertScoresList.isEmpty() ? BigDecimal.ZERO
                    : expertScoresList.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(expertScoresList.size()), 4, RoundingMode.HALF_UP);

            // Compute composite score
            BigDecimal compositeScore = BigDecimal.ZERO;
            boolean hasTierEvaluators = primaryScore.compareTo(BigDecimal.ZERO) > 0
                    || supervisorScore.compareTo(BigDecimal.ZERO) > 0
                    || !otherLeaderScores.isEmpty()
                    || !headScores.isEmpty();

            if ("SPECIAL_INNOVATION".equalsIgnoreCase(batch.getBatchType()) && hasTierEvaluators) {
                // (中心主要领导评分×40% + 其他中心领导评分×60%)×50% + 正职评分×40% + 代表评分×10%
                BigDecimal centerPart = primaryScore.multiply(new BigDecimal("0.40"))
                        .add(otherLeaderAvg.multiply(new BigDecimal("0.60")))
                        .multiply(new BigDecimal("0.50"));
                compositeScore = centerPart
                        .add(headAvg.multiply(new BigDecimal("0.40")))
                        .add(expertAvg.multiply(new BigDecimal("0.10")))
                        .setScale(4, RoundingMode.HALF_UP);
            } else if ("TARGET_PROJECT".equalsIgnoreCase(batch.getBatchType()) && hasTierEvaluators) {
                // (中心主要领导评分×20% + 分管领导评分×30% + 其他中心领导评分×50%)×50% + 正职评分×40% + 代表评分×10%
                BigDecimal centerPart = primaryScore.multiply(new BigDecimal("0.20"))
                        .add(supervisorScore.multiply(new BigDecimal("0.30")))
                        .add(otherLeaderAvg.multiply(new BigDecimal("0.50")))
                        .multiply(new BigDecimal("0.50"));
                compositeScore = centerPart
                        .add(headAvg.multiply(new BigDecimal("0.40")))
                        .add(expertAvg.multiply(new BigDecimal("0.10")))
                        .setScale(4, RoundingMode.HALF_UP);
            } else if (totalExpertWeight.compareTo(BigDecimal.ZERO) > 0) {
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
                    .category(p.getCategory())
                    .department(p.getDepartment())
                    .primaryLeaderScore(primaryScore)
                    .supervisorLeaderScore(supervisorScore)
                    .otherLeaderScore(otherLeaderAvg)
                    .headScore(headAvg)
                    .expertScore(expertAvg)
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

        // Calculate average score across all projects
        BigDecimal sumComposite = summaryRows.stream().map(AnnualProjectSummaryVO.ProjectSummaryRowVO::getCompositeScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageScore = summaryRows.isEmpty() ? BigDecimal.ZERO
                : sumComposite.divide(new BigDecimal(summaryRows.size()), 4, RoundingMode.HALF_UP);

        // Calculate score coefficient and bonus amount
        for (AnnualProjectSummaryVO.ProjectSummaryRowVO r : summaryRows) {
            BigDecimal coef = averageScore.compareTo(BigDecimal.ZERO) > 0
                    ? r.getCompositeScore().divide(averageScore, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ONE;
            r.setScoreCoefficient(coef);
            BigDecimal baseAmount = new BigDecimal("320.0000").divide(new BigDecimal(Math.max(1, summaryRows.size())), 4, RoundingMode.HALF_UP);
            r.setBonusAmount(baseAmount.multiply(coef).setScale(4, RoundingMode.HALF_UP));
        }

        // Award tier assignment for SPECIAL_INNOVATION
        if ("SPECIAL_INNOVATION".equalsIgnoreCase(batch.getBatchType())) {
            assignAwardTiers(summaryRows.stream().filter(r -> !"MANAGEMENT".equalsIgnoreCase(r.getCategory())).collect(Collectors.toList()));
            assignAwardTiers(summaryRows.stream().filter(r -> "MANAGEMENT".equalsIgnoreCase(r.getCategory())).collect(Collectors.toList()));
        }

        return AnnualProjectSummaryVO.builder()
                .batchId(batchId)
                .batchName(batch.getBatchName())
                .batchType(batch.getBatchType())
                .appraisalPeriod(batch.getAppraisalPeriod())
                .status(batch.getStatus())
                .averageScore(averageScore)
                .metrics(metrics)
                .experts(expertHeaders)
                .rows(summaryRows)
                .build();
    }

    private void assignAwardTiers(List<AnnualProjectSummaryVO.ProjectSummaryRowVO> list) {
        if (list.isEmpty()) return;
        list.sort((a, b) -> b.getCompositeScore().compareTo(a.getCompositeScore()));
        int n = list.size();
        int maxSpecial = Math.max(1, (int) Math.floor(n * 0.05));
        int maxFirst = Math.max(1, (int) Math.floor(n * 0.10));
        int maxSecond = Math.max(1, (int) Math.floor(n * 0.20));
        int maxThird = Math.max(1, (int) Math.floor(n * 0.30));

        int specialCount = 0;
        int firstCount = 0;
        int secondCount = 0;
        int thirdCount = 0;

        for (AnnualProjectSummaryVO.ProjectSummaryRowVO r : list) {
            BigDecimal score = r.getCompositeScore();
            if (score.compareTo(new BigDecimal("95.0000")) >= 0 && specialCount < maxSpecial) {
                r.setAwardTier("SPECIAL");
                r.setAwardTierName("特等奖");
                specialCount++;
            } else if (score.compareTo(new BigDecimal("90.0000")) >= 0 && firstCount < maxFirst) {
                r.setAwardTier("FIRST");
                r.setAwardTierName("一等奖");
                firstCount++;
            } else if (score.compareTo(new BigDecimal("80.0000")) >= 0 && secondCount < maxSecond) {
                r.setAwardTier("SECOND");
                r.setAwardTierName("二等奖");
                secondCount++;
            } else if (score.compareTo(new BigDecimal("70.0000")) >= 0 && thirdCount < maxThird) {
                r.setAwardTier("THIRD");
                r.setAwardTierName("三等奖");
                thirdCount++;
            } else {
                r.setAwardTier("NONE");
                r.setAwardTierName("未获奖");
            }

            ProjectItem pi = projectItemMapper.selectById(r.getProjectId());
            if (pi != null) {
                pi.setAwardTier(r.getAwardTier());
                pi.setRankNum(r.getRank());
                pi.setScoreCoefficient(r.getScoreCoefficient());
                pi.setBonusAmount(r.getBonusAmount());
                projectItemMapper.updateById(pi);
            }
        }
    }

    @Override
    public byte[] exportProjectSummaryExcel(Long batchId) {
        AnnualProjectSummaryVO summary = getProjectSummary(batchId);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<List<String>> head = new ArrayList<>();
            List<List<Object>> dataList = new ArrayList<>();

            if ("TARGET_PROJECT".equalsIgnoreCase(summary.getBatchType())) {
                // 目标绩效评审得分表: 序号, 项目名称, 部门, 中心主要领导评分, 分管领导评分, 其它中心领导评分（其它4位中心领导）, 正职评分, 代表评分（评审专家评分）, 综合得分, 得分系数, 排序
                head.add(List.of("序号"));
                head.add(List.of("项目名称"));
                head.add(List.of("部门"));
                head.add(List.of("中心主要领导评分"));
                head.add(List.of("分管领导评分"));
                head.add(List.of("其它中心领导评分（其它4位中心领导）"));
                head.add(List.of("正职评分"));
                head.add(List.of("代表评分（评审专家评分）"));
                head.add(List.of("综合得分"));
                head.add(List.of("得分系数"));
                head.add(List.of("排序"));

                int idx = 1;
                for (AnnualProjectSummaryVO.ProjectSummaryRowVO r : summary.getRows()) {
                    List<Object> row = new ArrayList<>();
                    row.add(idx++);
                    row.add(r.getProjectName());
                    row.add(r.getDepartment() != null ? r.getDepartment() : "");
                    row.add(r.getPrimaryLeaderScore() != null ? r.getPrimaryLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                    row.add(r.getSupervisorLeaderScore() != null ? r.getSupervisorLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                    row.add(r.getOtherLeaderScore() != null ? r.getOtherLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                    row.add(r.getHeadScore() != null ? r.getHeadScore().setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                    row.add(r.getExpertScore() != null ? r.getExpertScore().setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                    row.add(r.getCompositeScore().setScale(4, RoundingMode.HALF_UP).toString());
                    row.add(r.getScoreCoefficient() != null ? r.getScoreCoefficient().setScale(4, RoundingMode.HALF_UP).toString() : "1.0000");
                    row.add(r.getRank());
                    dataList.add(row);
                }
            } else if ("SPECIAL_INNOVATION".equalsIgnoreCase(summary.getBatchType())) {
                // 专项与创新任务评审得分表: 序号, 项目名称, 部门, 中心主要领导评分, 其他中心领导评分（其它5位中心领导）, 正职评分（11位正职）, 代表评分（评审专家评分）, 综合得分, 排序
                head.add(List.of("序号"));
                head.add(List.of("项目名称"));
                head.add(List.of("部门"));
                head.add(List.of("中心主要领导评分"));
                head.add(List.of("其他中心领导评分（其它5位中心领导）"));
                head.add(List.of("正职评分（11位正职）"));
                head.add(List.of("代表评分（评审专家评分）"));
                head.add(List.of("综合得分"));
                head.add(List.of("排序"));

                int idx = 1;
                for (AnnualProjectSummaryVO.ProjectSummaryRowVO r : summary.getRows()) {
                    List<Object> row = new ArrayList<>();
                    row.add(idx++);
                    row.add(r.getProjectName());
                    row.add(r.getDepartment() != null ? r.getDepartment() : "");
                    row.add(r.getPrimaryLeaderScore() != null ? r.getPrimaryLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                    row.add(r.getOtherLeaderScore() != null ? r.getOtherLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                    row.add(r.getHeadScore() != null ? r.getHeadScore().setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                    row.add(r.getExpertScore() != null ? r.getExpertScore().setScale(4, RoundingMode.HALF_UP).toString() : "0.0000");
                    row.add(r.getCompositeScore().setScale(4, RoundingMode.HALF_UP).toString());
                    row.add(r.getRank());
                    dataList.add(row);
                }
            } else {
                // Legacy ANNUAL_PROJECT
                head.add(List.of("项目序号"));
                head.add(List.of("项目名称"));
                head.add(List.of("项目负责人"));

                for (AppraisalMetric m : summary.getMetrics()) {
                    head.add(List.of(m.getMetricName() + " (" + m.getMaxScore().stripTrailingZeros().toPlainString() + "分)"));
                }

                for (AnnualProjectSummaryVO.ExpertHeaderVO e : summary.getExperts()) {
                    head.add(List.of(e.getRealName() + " (" + e.getWeight().stripTrailingZeros().toPlainString() + "%)"));
                }

                head.add(List.of("总分 (加权平均)"));
                head.add(List.of("排名"));

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
            }

            EasyExcel.write(out)
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet(summary.getBatchName())
                    .head(head)
                    .doWrite(dataList);

            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "导出 Excel 失败: " + e.getMessage());
        }
    }
}
