import request from './request';
import { MetricItem } from './metric';
import type { EvaluatorScoreDetail } from './monthly';

export interface ExpertWeightItem {
  expertUserId: number;
  weight: number;
}

export interface ProjectRoleWeightItem {
  roleCategory: string;
  roleName: string;
  weight: number;
  sortOrder?: number;
}

export interface ProjectBatchSaveDTO {
  id?: number;
  batchName: string;
  batchType?: string; // TARGET_PROJECT, SPECIAL_INNOVATION, ANNUAL_PROJECT
  category?: string; // BUSINESS, MANAGEMENT, ALL
  appraisalPeriod: string;
  startTime?: string;
  endTime?: string;
  roleWeights?: ProjectRoleWeightItem[];
  expertUserIds?: number[];
  experts?: ExpertWeightItem[];
  metrics: MetricItem[];
}

export interface ProjectBatchVO {
  id: number;
  batchName: string;
  batchType: string;
  category?: string;
  appraisalPeriod: string;
  startTime?: string;
  endTime?: string;
  status: 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED';
  totalEvaluatorCount?: number;
  submittedEvaluatorCount?: number;
  centerLeaderCount?: number;
  submittedCenterLeaderCount?: number;
  deptHeadCount?: number;
  submittedDeptHeadCount?: number;
  expertCount?: number;
  submittedExpertCount?: number;
  roleWeights?: ProjectRoleWeightItem[];
  createdAt: string;
}

export interface ProjectItemVO {
  id: number;
  batchId: number;
  projectCode: string;
  projectName: string;
  ownerName: string;
  category?: string;
  department?: string;
  supervisorId?: number;
  supervisorName?: string;
  scoreCoefficient?: number;
  bonusAmount?: number;
  rankNum?: number;
  materialPdfName?: string;
  hasMaterial: boolean;
  attachmentPdfName?: string;
  hasAttachment: boolean;
  sortOrder: number;
  createdAt: string;
}

export interface ProjectItemSaveDTO {
  id?: number;
  batchId: number;
  projectCode: string;
  projectName: string;
  ownerName: string;
  category?: string;
  department?: string;
  supervisorId?: number;
  sortOrder?: number;
}

export interface ExpertEvaluationVO {
  batchId: number;
  batchName: string;
  appraisalPeriod: string;
  endTime?: string;
  isExpired: boolean;
  batchStatus?: string;
  mySubmissionStatus: 'DRAFT' | 'SUBMITTED';
  metrics: MetricItem[];
  projects: {
    projectId: number;
    projectCode: string;
    projectName: string;
    ownerName: string;
    supervisorId?: number;
    supervisorName?: string;
    myRoleName?: string;
    isSupervisor?: boolean;
    materialPdfName?: string;
    hasMaterial: boolean;
    attachmentPdfName?: string;
    hasAttachment: boolean;
    metricScores: Record<number, number>;
    totalScore: number;
  }[];
}

export interface AnnualProjectSummaryVO {
  batchId: number;
  batchName: string;
  batchType?: string;
  appraisalPeriod: string;
  status: string;
  averageScore?: number;
  metrics: MetricItem[];
  experts: {
    expertUserId: number;
    realName: string;
    weight: number;
    submitted: boolean;
  }[];
  roleWeights?: ProjectRoleWeightItem[];
  rows: {
    projectId: number;
    projectCode: string;
    projectName: string;
    ownerName: string;
    category?: string;
    department?: string;
    supervisorId?: number;
    supervisorName?: string;
    primaryLeaderScore?: number;
    supervisorLeaderScore?: number;
    otherLeaderScore?: number;
    headScore?: number;
    expertScore?: number;
    scoreCoefficient?: number;
    bonusAmount?: number;
    metricAvgScores: Record<number, number>;
    expertScores: Record<number, number>;
    evaluatorDetails?: EvaluatorScoreDetail[];
    compositeScore: number;
    rank: number;
  }[];
}

export function listProjectBatchesApi(params?: { batchType?: string; status?: string; keyword?: string }): Promise<ProjectBatchVO[]> {
  return request.get('/projects/batches', { params });
}

export function deleteProjectBatchApi(id: number): Promise<void> {
  return request.delete(`/projects/batches/${id}`);
}

export function getExpertProgressApi(batchId: number): Promise<import('./monthly').EvaluatorProgressVO[]> {
  return request.get(`/projects/batches/${batchId}/progress`);
}

export function urgeExpertApi(batchId: number, expertUserId?: number): Promise<void> {
  return request.post(`/projects/batches/${batchId}/urge`, null, {
    params: expertUserId ? { expertUserId } : {},
  });
}

export function getProjectBatchDetailApi(id: number): Promise<ProjectBatchVO> {
  return request.get(`/projects/batches/${id}`);
}

export function saveProjectBatchApi(data: ProjectBatchSaveDTO): Promise<void> {
  return request.post('/projects/batches', data);
}

export function publishProjectBatchApi(id: number): Promise<void> {
  return request.put(`/projects/batches/${id}/publish`);
}

export function completeProjectBatchApi(id: number): Promise<void> {
  return request.put(`/projects/batches/${id}/complete`);
}

export function listProjectItemsApi(batchId: number): Promise<ProjectItemVO[]> {
  return request.get(`/projects/batches/${batchId}/items`);
}

export function saveProjectItemApi(data: ProjectItemSaveDTO): Promise<void> {
  return request.post('/projects/items', data);
}

export function deleteProjectItemApi(id: number): Promise<void> {
  return request.delete(`/projects/items/${id}`);
}

export function uploadProjectPdfApi(projectId: number, type: 'material' | 'attachment', file: File): Promise<void> {
  const formData = new FormData();
  formData.append('file', file);
  return request.post(`/projects/items/${projectId}/upload-pdf?type=${type}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export function getExpertEvaluationDataApi(batchId: number): Promise<ExpertEvaluationVO> {
  return request.get(`/projects/evaluation/${batchId}`);
}

export function saveExpertScoresApi(data: {
  batchId: number;
  items: { projectId: number; metricScores: Record<number, number> }[];
  isSubmit: boolean;
}): Promise<void> {
  return request.post('/projects/evaluation', data);
}

export function withdrawExpertScoresApi(batchId: number): Promise<void> {
  return request.put(`/projects/evaluation/${batchId}/withdraw`);
}

export function getProjectSummaryApi(batchId: number): Promise<AnnualProjectSummaryVO> {
  return request.get(`/projects/summary/${batchId}`);
}
