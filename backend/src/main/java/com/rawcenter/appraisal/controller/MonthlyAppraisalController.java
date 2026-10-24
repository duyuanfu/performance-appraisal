package com.rawcenter.appraisal.controller;

import com.rawcenter.appraisal.common.Result;
import com.rawcenter.appraisal.dto.MonthlyBatchSaveDTO;
import com.rawcenter.appraisal.dto.MonthlyScoreSubmitDTO;
import com.rawcenter.appraisal.service.MonthlyAppraisalService;
import com.rawcenter.appraisal.vo.ExamineePersonalResultVO;
import com.rawcenter.appraisal.vo.MonthlyBatchVO;
import com.rawcenter.appraisal.vo.MonthlyLeaderScoringVO;
import com.rawcenter.appraisal.vo.MonthlySummaryVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/monthly")
@RequiredArgsConstructor
public class MonthlyAppraisalController {

    private final MonthlyAppraisalService monthlyAppraisalService;

    @GetMapping("/batches")
    public Result<List<MonthlyBatchVO>> listMonthlyBatches(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(monthlyAppraisalService.listMonthlyBatches(status, keyword));
    }

    @GetMapping("/batches/{id}")
    public Result<MonthlyBatchVO> getMonthlyBatchDetail(@PathVariable Long id) {
        return Result.success(monthlyAppraisalService.getMonthlyBatchDetail(id));
    }

    @PostMapping("/batches")
    public Result<Void> saveMonthlyBatch(@Valid @RequestBody MonthlyBatchSaveDTO dto) {
        monthlyAppraisalService.saveMonthlyBatch(dto);
        return Result.success();
    }

    @PutMapping("/batches/{id}/publish")
    public Result<Void> publishMonthlyBatch(@PathVariable Long id) {
        monthlyAppraisalService.publishMonthlyBatch(id);
        return Result.success();
    }

    @PutMapping("/batches/{id}/complete")
    public Result<Void> completeMonthlyBatch(@PathVariable Long id) {
        monthlyAppraisalService.completeMonthlyBatch(id);
        return Result.success();
    }

    @DeleteMapping("/batches/{id}")
    public Result<Void> deleteMonthlyBatch(@PathVariable Long id) {
        checkSysAdmin();
        monthlyAppraisalService.deleteMonthlyBatch(id);
        return Result.success();
    }

    private void checkSysAdmin() {
        com.rawcenter.appraisal.common.UserContext.CurrentUser u = com.rawcenter.appraisal.common.UserContext.get();
        if (u != null && !"SYS_ADMIN".equalsIgnoreCase(u.getActiveRole()) && !"sys_admin".equalsIgnoreCase(u.getUsername())) {
            throw new com.rawcenter.appraisal.common.BusinessException(403, "权限不足：删除批次功能仅限【系统管理员】操作");
        }
    }

    @GetMapping("/candidate-examinees")
    public Result<List<com.rawcenter.appraisal.vo.UserVO>> getCandidateExaminees() {
        return Result.success(monthlyAppraisalService.listCandidateExaminees());
    }

    @GetMapping("/batches/{id}/progress")
    public Result<List<com.rawcenter.appraisal.vo.EvaluatorProgressVO>> getLeaderProgress(@PathVariable Long id) {
        return Result.success(monthlyAppraisalService.getLeaderProgress(id));
    }

    @PostMapping("/batches/{id}/urge")
    public Result<Void> urgeLeader(@PathVariable Long id, @RequestParam(required = false) Long leaderUserId) {
        monthlyAppraisalService.urgeLeader(id, leaderUserId);
        return Result.success();
    }

    @GetMapping("/scoring/{batchId}")
    public Result<MonthlyLeaderScoringVO> getLeaderScoringData(@PathVariable Long batchId) {
        return Result.success(monthlyAppraisalService.getLeaderScoringData(batchId));
    }

    @PostMapping("/scoring")
    public Result<Void> saveLeaderScores(@Valid @RequestBody MonthlyScoreSubmitDTO dto) {
        monthlyAppraisalService.saveLeaderScores(dto);
        return Result.success();
    }

    @PutMapping("/scoring/{batchId}/withdraw")
    public Result<Void> withdrawLeaderScores(@PathVariable Long batchId) {
        monthlyAppraisalService.withdrawLeaderScores(batchId);
        return Result.success();
    }

    @GetMapping("/summary/{batchId}")
    public Result<MonthlySummaryVO> getMonthlyBatchSummary(@PathVariable Long batchId) {
        return Result.success(monthlyAppraisalService.getMonthlyBatchSummary(batchId));
    }

    @GetMapping("/my-result/{batchId}")
    public Result<ExamineePersonalResultVO> getExamineePersonalResult(@PathVariable Long batchId) {
        return Result.success(monthlyAppraisalService.getExamineePersonalResult(batchId));
    }

    @GetMapping("/export/{batchId}")
    public void exportMonthlyBatchExcel(@PathVariable Long batchId, HttpServletResponse response) throws IOException {
        byte[] bytes = monthlyAppraisalService.exportMonthlyBatchExcel(batchId);
        String fileName = URLEncoder.encode("月度绩效考核汇总表-" + batchId + ".xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
