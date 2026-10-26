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
import com.rawcenter.appraisal.vo.EvaluatorScoreDetailVO;
import com.rawcenter.appraisal.vo.ExpertEvaluationVO;
import com.rawcenter.appraisal.vo.ProjectItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
    private final ProjectRoleWeightMapper projectRoleWeightMapper;
    private final MetricService metricService;

    @Value("${app.upload.dir:./data/uploads/projects}")
    private String uploadDir;

    @Override
    public List<AppraisalBatch> listProjectBatches(String status, String keyword) {
        return listProjectBatches(null, status, keyword);
    }

    @Override
    public List<AppraisalBatch> listProjectBatches(String batchType, String status, String keyword) {
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

        // 数据权限隔离：未发布的草稿对非管理员完全不可见；所有评分主体（中心领导、部门正职、评审专家）均可查看已发布的进行中和已归档批次
        boolean isAdmin = "ADMIN".equalsIgnoreCase(activeRole) || "SYS_ADMIN".equalsIgnoreCase(activeRole);
        if (!isAdmin) {
            wrapper.ne(AppraisalBatch::getStatus, "DRAFT");
        }

        wrapper.orderByDesc(AppraisalBatch::getCreatedAt);
        List<AppraisalBatch> batches = batchMapper.selectList(wrapper);

        for (AppraisalBatch b : batches) {
            List<com.rawcenter.appraisal.vo.EvaluatorProgressVO> progressList = getExpertProgress(b.getId());
            int totalEvaluators = progressList.size();
            int submittedEvaluators = (int) progressList.stream().filter(p -> "SUBMITTED".equals(p.getStatus())).count();

            int centerCount = (int) progressList.stream()
                    .filter(p -> "PRIMARY_LEADER".equals(p.getEvaluatorRoleType()) || "CENTER_LEADER".equals(p.getEvaluatorRoleType()))
                    .count();
            int submittedCenter = (int) progressList.stream()
                    .filter(p -> ("PRIMARY_LEADER".equals(p.getEvaluatorRoleType()) || "CENTER_LEADER".equals(p.getEvaluatorRoleType())) && "SUBMITTED".equals(p.getStatus()))
                    .count();

            int deptHeadCount = (int) progressList.stream()
                    .filter(p -> "DEPT_HEAD".equals(p.getEvaluatorRoleType()))
                    .count();
            int submittedDeptHead = (int) progressList.stream()
                    .filter(p -> "DEPT_HEAD".equals(p.getEvaluatorRoleType()) && "SUBMITTED".equals(p.getStatus()))
                    .count();

            int expertCount = (int) progressList.stream()
                    .filter(p -> "EXPERT".equals(p.getEvaluatorRoleType()))
                    .count();
            int submittedExpert = (int) progressList.stream()
                    .filter(p -> "EXPERT".equals(p.getEvaluatorRoleType()) && "SUBMITTED".equals(p.getStatus()))
                    .count();

            b.setTotalEvaluatorCount(totalEvaluators);
            b.setSubmittedEvaluatorCount(submittedEvaluators);
            b.setCenterLeaderCount(centerCount);
            b.setSubmittedCenterLeaderCount(submittedCenter);
            b.setDeptHeadCount(deptHeadCount);
            b.setSubmittedDeptHeadCount(submittedDeptHead);
            b.setExpertCount(expertCount);
            b.setSubmittedExpertCount(submittedExpert);
        }

        return batches;
    }

    @Override
    public AppraisalBatch getProjectBatchDetail(Long batchId) {
        AppraisalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }
        List<ProjectRoleWeight> savedRoleWeights = projectRoleWeightMapper.selectList(new LambdaQueryWrapper<ProjectRoleWeight>()
                .eq(ProjectRoleWeight::getBatchId, batchId)
                .orderByAsc(ProjectRoleWeight::getSortOrder));
        batch.setRoleWeights(savedRoleWeights);
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

        // 1. 考评主体角色及权重校验与默认装配（按《需求文档.md》标准权重）
        List<ProjectBatchSaveDTO.RoleWeightDTO> roleWeights = dto.getRoleWeights();
        if (CollectionUtils.isEmpty(roleWeights)) {
            roleWeights = new ArrayList<>();
            roleWeights.add(createRoleWeightDTO("CENTER_LEADER_TOTAL", "中心领导综合小计", new BigDecimal("50.0000"), 1));
            roleWeights.add(createRoleWeightDTO("PRIMARY_LEADER", "中心主要领导", new BigDecimal("20.0000"), 2));
            roleWeights.add(createRoleWeightDTO("SUPERVISOR_LEADER", "中心分管领导", new BigDecimal("30.0000"), 3));
            roleWeights.add(createRoleWeightDTO("OTHER_LEADER", "其他中心领导", new BigDecimal("50.0000"), 4));
            roleWeights.add(createRoleWeightDTO("DEPT_HEAD", "部门正职评分", new BigDecimal("40.0000"), 5));
            roleWeights.add(createRoleWeightDTO("EXPERT", "代表评分（评审专家）", new BigDecimal("10.0000"), 6));
        } else {
            Map<String, BigDecimal> rMap = roleWeights.stream()
                    .collect(Collectors.toMap(ProjectBatchSaveDTO.RoleWeightDTO::getRoleCategory, ProjectBatchSaveDTO.RoleWeightDTO::getWeight, (a, b) -> a));
            BigDecimal wCenter = rMap.getOrDefault("CENTER_LEADER_TOTAL", new BigDecimal("50.0000"));
            BigDecimal wHead = rMap.getOrDefault("DEPT_HEAD", new BigDecimal("40.0000"));
            BigDecimal wExpert = rMap.getOrDefault("EXPERT", new BigDecimal("10.0000"));
            BigDecimal sumMain = wCenter.add(wHead).add(wExpert);
            if (sumMain.subtract(new BigDecimal("100.0000")).abs().compareTo(new BigDecimal("0.0001")) > 0) {
                throw new BusinessException(400, "三大考评主体权重（中心领导 + 正职评分 + 代表评分）合计必须为 100%，当前为：" + sumMain.stripTrailingZeros().toPlainString() + "%");
            }
            BigDecimal wPrimary = rMap.getOrDefault("PRIMARY_LEADER", new BigDecimal("20.0000"));
            BigDecimal wSupervisor = rMap.getOrDefault("SUPERVISOR_LEADER", new BigDecimal("30.0000"));
            BigDecimal wOther = rMap.getOrDefault("OTHER_LEADER", new BigDecimal("50.0000"));
            BigDecimal sumLeaders = wPrimary.add(wSupervisor).add(wOther);
            if (sumLeaders.subtract(new BigDecimal("100.0000")).abs().compareTo(new BigDecimal("0.0001")) > 0) {
                throw new BusinessException(400, "中心领导内部细分权重（主要领导 + 分管领导 + 其他中心领导）合计必须为 100%，当前为：" + sumLeaders.stripTrailingZeros().toPlainString() + "%");
            }
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

        // 2. Save Project Role Weights
        projectRoleWeightMapper.delete(new LambdaQueryWrapper<ProjectRoleWeight>().eq(ProjectRoleWeight::getBatchId, batchId));
        int rOrder = 1;
        for (ProjectBatchSaveDTO.RoleWeightDTO rw : roleWeights) {
            projectRoleWeightMapper.insert(ProjectRoleWeight.builder()
                    .batchId(batchId)
                    .roleCategory(rw.getRoleCategory())
                    .roleName(rw.getRoleName())
                    .weight(rw.getWeight())
                    .sortOrder(rw.getSortOrder() != null ? rw.getSortOrder() : rOrder++)
                    .build());
        }

        // 3. Save Experts (自动装配系统评审专家或按指定专家名单)
        projectExpertMapper.delete(new LambdaQueryWrapper<ProjectExpert>().eq(ProjectExpert::getBatchId, batchId));
        List<Long> targetExpertIds = dto.getExpertUserIds();
        if (CollectionUtils.isEmpty(targetExpertIds) && !CollectionUtils.isEmpty(dto.getExperts())) {
            targetExpertIds = dto.getExperts().stream().map(ProjectBatchSaveDTO.ExpertWeightDTO::getExpertUserId).collect(Collectors.toList());
        }
        if (CollectionUtils.isEmpty(targetExpertIds)) {
            targetExpertIds = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPositionCategory, "EXPERT")
                    .eq(SysUser::getStatus, 1))
                    .stream().map(SysUser::getId).collect(Collectors.toList());
        }
        int expCount = targetExpertIds.size();
        BigDecimal perWeight = expCount > 0 ? new BigDecimal("100").divide(new BigDecimal(expCount), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        for (Long eId : targetExpertIds) {
            projectExpertMapper.insert(ProjectExpert.builder()
                    .batchId(batchId)
                    .expertUserId(eId)
                    .weight(perWeight)
                    .build());
        }
    }

    private ProjectBatchSaveDTO.RoleWeightDTO createRoleWeightDTO(String category, String name, BigDecimal weight, int order) {
        ProjectBatchSaveDTO.RoleWeightDTO dto = new ProjectBatchSaveDTO.RoleWeightDTO();
        dto.setRoleCategory(category);
        dto.setRoleName(name);
        dto.setWeight(weight);
        dto.setSortOrder(order);
        return dto;
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
            // 自动装配系统全部评审专家
            List<SysUser> systemExperts = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPositionCategory, "EXPERT")
                    .eq(SysUser::getStatus, 1));
            int expCount = systemExperts.size();
            BigDecimal perWeight = expCount > 0 ? new BigDecimal("100").divide(new BigDecimal(expCount), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            for (SysUser exp : systemExperts) {
                projectExpertMapper.insert(ProjectExpert.builder()
                        .batchId(batchId)
                        .expertUserId(exp.getId())
                        .weight(perWeight)
                        .build());
            }
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

        List<Long> supervisorIds = items.stream()
                .map(ProjectItem::getSupervisorId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> supervisorMap = supervisorIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(supervisorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        return items.stream().map(item -> {
            SysUser supervisor = item.getSupervisorId() != null ? supervisorMap.get(item.getSupervisorId()) : null;
            return ProjectItemVO.builder()
                    .id(item.getId())
                    .batchId(item.getBatchId())
                    .projectCode(item.getProjectCode())
                    .projectName(item.getProjectName())
                    .ownerName(item.getOwnerName())
                    .category(item.getCategory())
                    .department(item.getDepartment())
                    .supervisorId(item.getSupervisorId())
                    .supervisorName(supervisor != null ? supervisor.getRealName() : "")
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
                    .build();
        }).collect(Collectors.toList());
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
        projectRoleWeightMapper.delete(new LambdaQueryWrapper<ProjectRoleWeight>().eq(ProjectRoleWeight::getBatchId, batchId));
        batchMapper.deleteById(batchId);
    }

    @Override
    public void saveProjectItem(ProjectItemSaveDTO dto) {
        if (dto.getId() == null) {
            ProjectItem item = ProjectItem.builder()
                    .batchId(dto.getBatchId())
                    .projectCode(dto.getProjectCode())
                    .projectName(dto.getProjectName())
                    .ownerName(dto.getOwnerName())
                    .category(dto.getCategory() != null ? dto.getCategory() : "")
                    .department(dto.getDepartment() != null ? dto.getDepartment() : "")
                    .supervisorId(dto.getSupervisorId())
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
            item.setSupervisorId(dto.getSupervisorId());
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

        List<Long> supervisorIds = projects.stream()
                .map(ProjectItem::getSupervisorId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> supervisorMap = supervisorIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(supervisorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

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
                    .supervisorId(p.getSupervisorId())
                    .supervisorName(p.getSupervisorId() != null && supervisorMap.get(p.getSupervisorId()) != null
                            ? supervisorMap.get(p.getSupervisorId()).getRealName() : "")
                    .isSupervisor(p.getSupervisorId() != null && p.getSupervisorId().equals(expertUserId))
                    .myRoleName(resolveProjectRoleName(evaluatorPos, p.getSupervisorId(), expertUserId))
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

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String resolveProjectRoleName(String evaluatorPos, Long supervisorId, Long currentUserId) {
        if (supervisorId != null && supervisorId.equals(currentUserId)) {
            return "分管领导";
        }
        if ("PRIMARY_LEADER".equalsIgnoreCase(evaluatorPos)) {
            return "中心主要领导";
        }
        if ("CENTER_LEADER".equalsIgnoreCase(evaluatorPos)) {
            return "其他中心领导";
        }
        if ("DEPT_HEAD".equalsIgnoreCase(evaluatorPos)) {
            return "部门正职";
        }
        if ("EXPERT".equalsIgnoreCase(evaluatorPos)) {
            return "评审专家";
        }
        return "考评人员";
    }

    /**
     * 构建某个项目的评分人明细（含未提交人员），用于汇总排名“查看明细”。
     */
    private List<EvaluatorScoreDetailVO> buildProjectEvaluatorDetails(
            ProjectItem project,
            List<ProjectExpert> experts,
            Map<Long, SysUser> expertUserMap,
            Map<Long, SysUser> evaluatorUserMap,
            List<ProjectScoreRecord> allRecords,
            Map<String, BigDecimal> recordDetailMap,
            List<AppraisalMetric> metrics) {
        List<EvaluatorScoreDetailVO> details = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<ProjectScoreRecord> projectRecords = allRecords.stream()
                .filter(r -> r.getProjectId().equals(project.getId()))
                .collect(Collectors.toList());

        for (ProjectExpert exp : experts) {
            Long uid = exp.getExpertUserId();
            SysUser u = expertUserMap.get(uid);
            if (u == null) {
                continue;
            }
            details.add(buildProjectEvaluatorDetail(project, uid, u, projectRecords, recordDetailMap, metrics));
            seen.add(uid);
        }

        // 兼容历史批次：若存在评分记录但不在 project_expert 清单中，也补充展示
        for (ProjectScoreRecord rec : projectRecords) {
            if (seen.contains(rec.getExpertUserId())) {
                continue;
            }
            SysUser u = evaluatorUserMap.get(rec.getExpertUserId());
            if (u == null) {
                continue;
            }
            details.add(buildProjectEvaluatorDetail(project, rec.getExpertUserId(), u, projectRecords, recordDetailMap, metrics));
            seen.add(rec.getExpertUserId());
        }

        details.sort(Comparator
                .comparingInt((EvaluatorScoreDetailVO d) -> roleOrder(d.getRoleType()))
                .thenComparing(EvaluatorScoreDetailVO::getRealName, Comparator.nullsLast(String::compareTo)));
        return details;
    }

    private EvaluatorScoreDetailVO buildProjectEvaluatorDetail(
            ProjectItem project,
            Long evaluatorUserId,
            SysUser evaluatorUser,
            List<ProjectScoreRecord> projectRecords,
            Map<String, BigDecimal> recordDetailMap,
            List<AppraisalMetric> metrics) {
        ProjectScoreRecord rec = projectRecords.stream()
                .filter(r -> r.getExpertUserId().equals(evaluatorUserId))
                .findFirst()
                .orElse(null);

        String roleType = evaluatorUser.getPositionCategory();
        String roleName;
        if ("PRIMARY_LEADER".equalsIgnoreCase(roleType)) {
            roleName = "中心主要领导";
        } else if ("CENTER_LEADER".equalsIgnoreCase(roleType)) {
            if (project.getSupervisorId() != null && project.getSupervisorId().equals(evaluatorUserId)) {
                roleType = "SUPERVISOR_LEADER";
                roleName = "分管领导";
            } else {
                roleType = "OTHER_LEADER";
                roleName = "其他中心领导";
            }
        } else if ("DEPT_HEAD".equalsIgnoreCase(roleType)) {
            roleName = "部门正职";
        } else if ("EXPERT".equalsIgnoreCase(roleType)) {
            roleName = "评审专家";
        } else {
            roleName = "考评人员";
        }

        Map<Long, BigDecimal> metricScores = new HashMap<>();
        if (rec != null) {
            for (AppraisalMetric m : metrics) {
                BigDecimal score = recordDetailMap.get(rec.getId() + "_" + m.getId());
                if (score != null) {
                    metricScores.put(m.getId(), score);
                }
            }
        }

        return EvaluatorScoreDetailVO.builder()
                .evaluatorUserId(evaluatorUserId)
                .realName(evaluatorUser.getRealName())
                .roleType(roleType)
                .roleName(roleName)
                .status(rec != null ? rec.getStatus() : "NOT_STARTED")
                .metricScores(metricScores)
                .totalScore(rec != null ? rec.getTotalScore() : null)
                .submittedAt(rec != null ? rec.getSubmittedAt() : null)
                .build();
    }

    private int roleOrder(String roleType) {
        if (roleType == null) {
            return 99;
        }
        switch (roleType) {
            case "PRIMARY_LEADER":
                return 0;
            case "SUPERVISOR_LEADER":
                return 1;
            case "OTHER_LEADER":
            case "CENTER_LEADER":
                return 2;
            case "DEPT_HEAD":
                return 3;
            case "EXPERT":
                return 6;
            default:
                return 99;
        }
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

        Long projectCountLong = projectItemMapper.selectCount(new LambdaQueryWrapper<ProjectItem>()
                .eq(ProjectItem::getBatchId, batchId));
        int projectCount = projectCountLong.intValue();

        List<ProjectScoreRecord> allRecords = scoreRecordMapper.selectList(new LambdaQueryWrapper<ProjectScoreRecord>()
                .eq(ProjectScoreRecord::getBatchId, batchId));
        Map<Long, List<ProjectScoreRecord>> recordsByEvaluator = allRecords.stream()
                .collect(Collectors.groupingBy(ProjectScoreRecord::getExpertUserId));

        List<com.rawcenter.appraisal.vo.EvaluatorProgressVO> result = new ArrayList<>();

        // 1. 中心领导（中心主要领导与中心领导）
        List<SysUser> centerLeaders = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getPositionCategory, Arrays.asList("PRIMARY_LEADER", "CENTER_LEADER"))
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        for (SysUser cl : centerLeaders) {
            String roleType = "PRIMARY_LEADER".equalsIgnoreCase(cl.getPositionCategory()) ? "PRIMARY_LEADER" : "CENTER_LEADER";
            String roleName = "PRIMARY_LEADER".equalsIgnoreCase(cl.getPositionCategory()) ? "中心主要领导" : "中心领导";
            result.add(buildProjectEvaluatorProgress(cl, roleType, roleName, projectCount, recordsByEvaluator.get(cl.getId())));
        }

        // 2. 部门正职
        List<SysUser> deptHeads = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPositionCategory, "DEPT_HEAD")
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        for (SysUser dh : deptHeads) {
            result.add(buildProjectEvaluatorProgress(dh, "DEPT_HEAD", "部门正职", projectCount, recordsByEvaluator.get(dh.getId())));
        }

        // 3. 评审专家
        List<SysUser> experts = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPositionCategory, "EXPERT")
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        for (SysUser exp : experts) {
            result.add(buildProjectEvaluatorProgress(exp, "EXPERT", "评审专家", projectCount, recordsByEvaluator.get(exp.getId())));
        }

        return result;
    }

    private com.rawcenter.appraisal.vo.EvaluatorProgressVO buildProjectEvaluatorProgress(
            SysUser u, String roleType, String roleName, int projectCount, List<ProjectScoreRecord> records) {
        String status = "NOT_STARTED";
        LocalDateTime submittedAt = null;
        int scoredCount = 0;

        if (!CollectionUtils.isEmpty(records)) {
            scoredCount = records.size();
            boolean allSubmitted = projectCount > 0 && scoredCount >= projectCount && records.stream().allMatch(r -> "SUBMITTED".equals(r.getStatus()));
            if (allSubmitted) {
                status = "SUBMITTED";
                submittedAt = records.stream()
                        .map(ProjectScoreRecord::getSubmittedAt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
            } else {
                status = "DRAFT";
            }
        }

        return com.rawcenter.appraisal.vo.EvaluatorProgressVO.builder()
                .userId(u.getId())
                .realName(u.getRealName())
                .department(u.getDepartment())
                .position(u.getPosition())
                .phone(u.getPhone())
                .evaluatorRoleType(roleType)
                .evaluatorRoleName(roleName)
                .targetDesc("参评项目 " + projectCount + " 个")
                .status(status)
                .scoredCount(scoredCount)
                .totalTargetCount(projectCount)
                .submittedAt(submittedAt)
                .build();
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
        if (expertUserId != null) {
            SysUser u = userMapper.selectById(expertUserId);
            log.info("[Urge] 向项目考评人员 {} (ID: {}) 发送催办提醒: 请尽快完成【{}】项目评审打分！",
                    u != null ? u.getRealName() : expertUserId, expertUserId, batch.getBatchName());
        } else {
            List<com.rawcenter.appraisal.vo.EvaluatorProgressVO> progressList = getExpertProgress(batchId);
            for (com.rawcenter.appraisal.vo.EvaluatorProgressVO p : progressList) {
                if (!"SUBMITTED".equals(p.getStatus())) {
                    log.info("[Urge] 批量催办未完成项目考评人 {} (ID: {}, 角色: {}): 请尽快完成【{}】项目评审打分！",
                            p.getRealName(), p.getUserId(), p.getEvaluatorRoleName(), batch.getBatchName());
                }
            }
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

        // 一次性加载全部评分明细，避免逐项目/逐专家查询
        Map<String, BigDecimal> recordDetailMap = new HashMap<>();
        List<Long> recordIds = allRecords.stream().map(ProjectScoreRecord::getId).collect(Collectors.toList());
        if (!recordIds.isEmpty()) {
            List<ProjectScoreDetail> details = scoreDetailMapper.selectList(new LambdaQueryWrapper<ProjectScoreDetail>()
                    .in(ProjectScoreDetail::getRecordId, recordIds));
            for (ProjectScoreDetail d : details) {
                recordDetailMap.put(d.getRecordId() + "_" + d.getMetricId(), d.getScore());
            }
        }

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

        // 3. Project Role Weights
        List<ProjectRoleWeight> savedRoleWeights = projectRoleWeightMapper.selectList(new LambdaQueryWrapper<ProjectRoleWeight>()
                .eq(ProjectRoleWeight::getBatchId, batchId)
                .orderByAsc(ProjectRoleWeight::getSortOrder));
        Map<String, BigDecimal> rwMap = savedRoleWeights.stream()
                .collect(Collectors.toMap(ProjectRoleWeight::getRoleCategory, ProjectRoleWeight::getWeight, (a, b) -> a));

        BigDecimal wCenterTotal = rwMap.getOrDefault("CENTER_LEADER_TOTAL", new BigDecimal("50.0000")).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal wPrimary = rwMap.getOrDefault("PRIMARY_LEADER", new BigDecimal("20.0000")).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal wSupervisor = rwMap.getOrDefault("SUPERVISOR_LEADER", new BigDecimal("30.0000")).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal wOther = rwMap.getOrDefault("OTHER_LEADER", new BigDecimal("50.0000")).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal wHead = rwMap.getOrDefault("DEPT_HEAD", new BigDecimal("40.0000")).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal wExpert = rwMap.getOrDefault("EXPERT", new BigDecimal("10.0000")).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        List<ProjectBatchSaveDTO.RoleWeightDTO> roleWeightDTOs = savedRoleWeights.stream()
                .map(rw -> {
                    ProjectBatchSaveDTO.RoleWeightDTO r = new ProjectBatchSaveDTO.RoleWeightDTO();
                    r.setRoleCategory(rw.getRoleCategory());
                    r.setRoleName(rw.getRoleName());
                    r.setWeight(rw.getWeight());
                    r.setSortOrder(rw.getSortOrder());
                    return r;
                }).collect(Collectors.toList());

        List<AnnualProjectSummaryVO.ProjectSummaryRowVO> summaryRows = new ArrayList<>();

        List<Long> supervisorIds = projects.stream()
                .map(ProjectItem::getSupervisorId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> supervisorUserMap = supervisorIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(supervisorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        List<Long> evaluatorUserIds = allRecords.stream()
                .map(ProjectScoreRecord::getExpertUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> evaluatorUserMap = evaluatorUserIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(evaluatorUserIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

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
            BigDecimal primaryScore = null;
            BigDecimal supervisorScore = null;
            List<BigDecimal> otherLeaderScores = new ArrayList<>();
            List<BigDecimal> headScores = new ArrayList<>();
            List<BigDecimal> expertScoresList = new ArrayList<>();
            boolean hasTierEvaluators = false;

            Long supervisorId = p.getSupervisorId();
            if (supervisorId == null && StringUtils.hasText(p.getDepartment())) {
                // 兼容历史数据：旧版按申报部门推导部门正职的分管领导
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

                SysUser evUser = evaluatorUserMap.get(rec.getExpertUserId());
                if (evUser == null) continue;
                String pos = evUser.getPositionCategory();

                if ("PRIMARY_LEADER".equalsIgnoreCase(pos)) {
                    primaryScore = rec.getTotalScore();
                    hasTierEvaluators = true;
                } else if ("CENTER_LEADER".equalsIgnoreCase(pos)) {
                    hasTierEvaluators = true;
                    if (supervisorId != null && supervisorId.equals(rec.getExpertUserId())) {
                        supervisorScore = rec.getTotalScore();
                    } else {
                        otherLeaderScores.add(rec.getTotalScore());
                    }
                } else if ("DEPT_HEAD".equalsIgnoreCase(pos)) {
                    headScores.add(rec.getTotalScore());
                    hasTierEvaluators = true;
                } else if ("EXPERT".equalsIgnoreCase(pos)) {
                    expertScoresList.add(rec.getTotalScore());
                }
            }

            BigDecimal otherLeaderAvg = otherLeaderScores.isEmpty() ? null
                    : otherLeaderScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(otherLeaderScores.size()), 4, RoundingMode.HALF_UP);

            BigDecimal headAvg = headScores.isEmpty() ? null
                    : headScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(headScores.size()), 4, RoundingMode.HALF_UP);

            BigDecimal expertAvg = expertScoresList.isEmpty() ? null
                    : expertScoresList.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(expertScoresList.size()), 4, RoundingMode.HALF_UP);

            // Compute composite score
            BigDecimal compositeScore = BigDecimal.ZERO;

            if (hasTierEvaluators) {
                // 统一综合得分计算公式（按配置的主体角色权重，默认对齐《需求文档.md》最新规则）：
                // 综合得分 = (中心主要领导评分×wPrimary + 分管领导评分×wSupervisor + 其他中心领导评分×wOther)×wCenterTotal + 正职评分×wHead + 代表评分(即评审专家)×wExpert
                BigDecimal centerPart = nz(primaryScore).multiply(wPrimary)
                        .add(nz(supervisorScore).multiply(wSupervisor))
                        .add(nz(otherLeaderAvg).multiply(wOther))
                        .multiply(wCenterTotal);
                compositeScore = centerPart
                        .add(nz(headAvg).multiply(wHead))
                        .add(nz(expertAvg).multiply(wExpert))
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
                        BigDecimal detailScore = recordDetailMap.get(rec.getId() + "_" + m.getId());
                        if (detailScore != null) {
                            metricSum = metricSum.add(detailScore);
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
                    .supervisorId(p.getSupervisorId())
                    .supervisorName(p.getSupervisorId() != null && supervisorUserMap.get(p.getSupervisorId()) != null
                            ? supervisorUserMap.get(p.getSupervisorId()).getRealName() : "")
                    .primaryLeaderScore(primaryScore)
                    .supervisorLeaderScore(supervisorScore)
                    .otherLeaderScore(otherLeaderAvg)
                    .headScore(headAvg)
                    .expertScore(expertAvg)
                    .metricAvgScores(metricAvgScores)
                    .expertScores(expertScoreMap)
                    .evaluatorDetails(buildProjectEvaluatorDetails(p, experts, expertUserMap, evaluatorUserMap, allRecords, recordDetailMap, metrics))
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
                .roleWeights(roleWeightDTOs)
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
                head.add(List.of("分管领导"));
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
                    row.add(r.getSupervisorName() != null ? r.getSupervisorName() : "");
                    row.add(r.getPrimaryLeaderScore() != null ? r.getPrimaryLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "");
                    row.add(r.getSupervisorLeaderScore() != null ? r.getSupervisorLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "");
                    row.add(r.getOtherLeaderScore() != null ? r.getOtherLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "");
                    row.add(r.getHeadScore() != null ? r.getHeadScore().setScale(4, RoundingMode.HALF_UP).toString() : "");
                    row.add(r.getExpertScore() != null ? r.getExpertScore().setScale(4, RoundingMode.HALF_UP).toString() : "");
                    row.add(r.getCompositeScore().setScale(4, RoundingMode.HALF_UP).toString());
                    row.add(r.getScoreCoefficient() != null ? r.getScoreCoefficient().setScale(4, RoundingMode.HALF_UP).toString() : "1.0000");
                    row.add(r.getRank());
                    dataList.add(row);
                }
            } else if ("SPECIAL_INNOVATION".equalsIgnoreCase(summary.getBatchType())) {
                // 专项与创新任务评审得分表: 序号, 项目名称, 部门, 中心主要领导评分, 分管领导评分, 其它中心领导评分, 正职评分, 代表评分（评审专家评分）, 综合得分, 评定奖项等次, 排序
                head.add(List.of("序号"));
                head.add(List.of("项目名称"));
                head.add(List.of("分管领导"));
                head.add(List.of("中心主要领导评分"));
                head.add(List.of("分管领导评分"));
                head.add(List.of("其它中心领导评分"));
                head.add(List.of("正职评分"));
                head.add(List.of("代表评分（评审专家评分）"));
                head.add(List.of("综合得分"));
                head.add(List.of("评定奖项等次"));
                head.add(List.of("排序"));

                int idx = 1;
                for (AnnualProjectSummaryVO.ProjectSummaryRowVO r : summary.getRows()) {
                    List<Object> row = new ArrayList<>();
                    row.add(idx++);
                    row.add(r.getProjectName());
                    row.add(r.getSupervisorName() != null ? r.getSupervisorName() : "");
                    row.add(r.getPrimaryLeaderScore() != null ? r.getPrimaryLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "");
                    row.add(r.getSupervisorLeaderScore() != null ? r.getSupervisorLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "");
                    row.add(r.getOtherLeaderScore() != null ? r.getOtherLeaderScore().setScale(4, RoundingMode.HALF_UP).toString() : "");
                    row.add(r.getHeadScore() != null ? r.getHeadScore().setScale(4, RoundingMode.HALF_UP).toString() : "");
                    row.add(r.getExpertScore() != null ? r.getExpertScore().setScale(4, RoundingMode.HALF_UP).toString() : "");
                    row.add(r.getCompositeScore().setScale(4, RoundingMode.HALF_UP).toString());
                    row.add(r.getAwardTierName() != null ? r.getAwardTierName() : "未获奖");
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
