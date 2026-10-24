package com.rawcenter.appraisal.service;

import com.rawcenter.appraisal.dto.ExpertScoreSubmitDTO;
import com.rawcenter.appraisal.dto.ProjectBatchSaveDTO;
import com.rawcenter.appraisal.dto.ProjectItemSaveDTO;
import com.rawcenter.appraisal.entity.AppraisalBatch;
import com.rawcenter.appraisal.vo.AnnualProjectSummaryVO;
import com.rawcenter.appraisal.vo.ExpertEvaluationVO;
import com.rawcenter.appraisal.vo.ProjectItemVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

public interface ProjectAppraisalService {
    List<AppraisalBatch> listProjectBatches(String status, String keyword);
    List<AppraisalBatch> listProjectBatches(String batchType, String status, String keyword);
    AppraisalBatch getProjectBatchDetail(Long batchId);
    void saveProjectBatch(ProjectBatchSaveDTO dto);
    void publishProjectBatch(Long batchId);
    void completeProjectBatch(Long batchId);
    void deleteProjectBatch(Long batchId);

    List<ProjectItemVO> listProjectItems(Long batchId);
    void saveProjectItem(ProjectItemSaveDTO dto);
    void deleteProjectItem(Long id);
    void uploadProjectPdf(Long projectId, String type, MultipartFile file);
    File getProjectPdfFile(Long projectId, String type);

    ExpertEvaluationVO getExpertEvaluationData(Long batchId);
    void saveExpertScores(ExpertScoreSubmitDTO dto);
    void withdrawExpertScores(Long batchId);
    List<com.rawcenter.appraisal.vo.EvaluatorProgressVO> getExpertProgress(Long batchId);
    void urgeExpert(Long batchId, Long expertUserId);

    AnnualProjectSummaryVO getProjectSummary(Long batchId);
    byte[] exportProjectSummaryExcel(Long batchId);
}
