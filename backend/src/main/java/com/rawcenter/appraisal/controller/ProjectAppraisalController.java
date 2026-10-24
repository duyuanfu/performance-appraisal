package com.rawcenter.appraisal.controller;

import com.rawcenter.appraisal.common.BusinessException;
import com.rawcenter.appraisal.common.Result;
import com.rawcenter.appraisal.dto.ExpertScoreSubmitDTO;
import com.rawcenter.appraisal.dto.ProjectBatchSaveDTO;
import com.rawcenter.appraisal.dto.ProjectItemSaveDTO;
import com.rawcenter.appraisal.entity.AppraisalBatch;
import com.rawcenter.appraisal.service.ProjectAppraisalService;
import com.rawcenter.appraisal.vo.AnnualProjectSummaryVO;
import com.rawcenter.appraisal.vo.ExpertEvaluationVO;
import com.rawcenter.appraisal.vo.ProjectItemVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectAppraisalController {

    private final ProjectAppraisalService projectAppraisalService;

    @GetMapping("/batches")
    public Result<List<AppraisalBatch>> listProjectBatches(
            @RequestParam(required = false) String batchType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(projectAppraisalService.listProjectBatches(batchType, status, keyword));
    }

    @GetMapping("/batches/{id}")
    public Result<AppraisalBatch> getProjectBatchDetail(@PathVariable Long id) {
        return Result.success(projectAppraisalService.getProjectBatchDetail(id));
    }

    @PostMapping("/batches")
    public Result<Void> saveProjectBatch(@Valid @RequestBody ProjectBatchSaveDTO dto) {
        projectAppraisalService.saveProjectBatch(dto);
        return Result.success();
    }

    @PutMapping("/batches/{id}/publish")
    public Result<Void> publishProjectBatch(@PathVariable Long id) {
        projectAppraisalService.publishProjectBatch(id);
        return Result.success();
    }

    @PutMapping("/batches/{id}/complete")
    public Result<Void> completeProjectBatch(@PathVariable Long id) {
        projectAppraisalService.completeProjectBatch(id);
        return Result.success();
    }

    @GetMapping("/batches/{id}/progress")
    public Result<List<com.rawcenter.appraisal.vo.EvaluatorProgressVO>> getExpertProgress(@PathVariable Long id) {
        return Result.success(projectAppraisalService.getExpertProgress(id));
    }

    @PostMapping("/batches/{id}/urge")
    public Result<Void> urgeExpert(@PathVariable Long id, @RequestParam(required = false) Long expertUserId) {
        projectAppraisalService.urgeExpert(id, expertUserId);
        return Result.success();
    }

    @GetMapping("/batches/{batchId}/items")
    public Result<List<ProjectItemVO>> listProjectItems(@PathVariable Long batchId) {
        return Result.success(projectAppraisalService.listProjectItems(batchId));
    }

    @PostMapping("/items")
    public Result<Void> saveProjectItem(@Valid @RequestBody ProjectItemSaveDTO dto) {
        projectAppraisalService.saveProjectItem(dto);
        return Result.success();
    }

    @DeleteMapping("/items/{id}")
    public Result<Void> deleteProjectItem(@PathVariable Long id) {
        checkSysAdmin();
        projectAppraisalService.deleteProjectItem(id);
        return Result.success();
    }

    @DeleteMapping("/batches/{id}")
    public Result<Void> deleteProjectBatch(@PathVariable Long id) {
        checkSysAdmin();
        projectAppraisalService.deleteProjectBatch(id);
        return Result.success();
    }

    private void checkSysAdmin() {
        com.rawcenter.appraisal.common.UserContext.CurrentUser u = com.rawcenter.appraisal.common.UserContext.get();
        if (u != null && !"SYS_ADMIN".equalsIgnoreCase(u.getActiveRole()) && !"sys_admin".equalsIgnoreCase(u.getUsername())) {
            throw new com.rawcenter.appraisal.common.BusinessException(403, "权限不足：项目与评审批次删除功能仅限【系统管理员】操作");
        }
    }

    @PostMapping("/items/{id}/upload-pdf")
    public Result<Void> uploadProjectPdf(
            @PathVariable Long id,
            @RequestParam String type,
            @RequestParam("file") MultipartFile file) {
        projectAppraisalService.uploadProjectPdf(id, type, file);
        return Result.success();
    }

    @GetMapping("/items/{id}/pdf/{type}")
    public ResponseEntity<Resource> getProjectPdfFile(
            @PathVariable Long id,
            @PathVariable String type) {
        File file = projectAppraisalService.getProjectPdfFile(id, type);
        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8) + "\"")
                .header("X-Frame-Options", "SAMEORIGIN")
                .body(resource);
    }

    @GetMapping("/evaluation/{batchId}")
    public Result<ExpertEvaluationVO> getExpertEvaluationData(@PathVariable Long batchId) {
        return Result.success(projectAppraisalService.getExpertEvaluationData(batchId));
    }

    @PostMapping("/evaluation")
    public Result<Void> saveExpertScores(@Valid @RequestBody ExpertScoreSubmitDTO dto) {
        projectAppraisalService.saveExpertScores(dto);
        return Result.success();
    }

    @PutMapping("/evaluation/{batchId}/withdraw")
    public Result<Void> withdrawExpertScores(@PathVariable Long batchId) {
        projectAppraisalService.withdrawExpertScores(batchId);
        return Result.success();
    }

    @GetMapping("/summary/{batchId}")
    public Result<AnnualProjectSummaryVO> getProjectSummary(@PathVariable Long batchId) {
        return Result.success(projectAppraisalService.getProjectSummary(batchId));
    }

    @GetMapping("/export/{batchId}")
    public void exportProjectSummaryExcel(@PathVariable Long batchId, HttpServletResponse response) throws IOException {
        byte[] bytes = projectAppraisalService.exportProjectSummaryExcel(batchId);
        String fileName = URLEncoder.encode("年度目标管理项目评审汇总表-" + batchId + ".xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
