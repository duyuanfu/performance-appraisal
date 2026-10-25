package com.rawcenter.appraisal.service;

import com.rawcenter.appraisal.dto.MonthlyBatchSaveDTO;
import com.rawcenter.appraisal.dto.MonthlyScoreSubmitDTO;
import com.rawcenter.appraisal.vo.EvaluatorProgressVO;
import com.rawcenter.appraisal.vo.ExamineePersonalResultVO;
import com.rawcenter.appraisal.vo.MonthlyBatchVO;
import com.rawcenter.appraisal.vo.MonthlyLeaderScoringVO;
import com.rawcenter.appraisal.vo.MonthlySummaryVO;

import java.util.List;

public interface MonthlyAppraisalService {
    List<MonthlyBatchVO> listMonthlyBatches(String status, String keyword);
    List<MonthlyBatchVO> listMonthlyBatches(String category, String status, String keyword);
    MonthlyBatchVO getMonthlyBatchDetail(Long batchId);
    void saveMonthlyBatch(MonthlyBatchSaveDTO dto);
    void publishMonthlyBatch(Long batchId);
    void completeMonthlyBatch(Long batchId);
    void deleteMonthlyBatch(Long batchId);
    MonthlyLeaderScoringVO getLeaderScoringData(Long batchId);
    void saveLeaderScores(MonthlyScoreSubmitDTO dto);
    void withdrawLeaderScores(Long batchId);
    List<EvaluatorProgressVO> getLeaderProgress(Long batchId);
    void urgeLeader(Long batchId, Long leaderUserId);
    MonthlySummaryVO getMonthlyBatchSummary(Long batchId);
    ExamineePersonalResultVO getExamineePersonalResult(Long batchId);
    byte[] exportMonthlyBatchExcel(Long batchId);
    List<com.rawcenter.appraisal.vo.UserVO> listCandidateExaminees();
    List<com.rawcenter.appraisal.vo.UserVO> listCandidateExaminees(String category);
}
