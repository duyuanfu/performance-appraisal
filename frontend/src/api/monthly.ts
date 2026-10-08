import request from './request';
import { MetricItem } from './metric';

export interface LeaderWeightItem {
  leaderUserId: number;
  weight: number;
}

export interface MonthlyBatchSaveDTO {
  id?: number;
  batchName: string;
  appraisalPeriod: string;
  startTime?: string;
  endTime?: string;
  leaders: LeaderWeightItem[];
  examineeUserIds: number[];
  metrics: MetricItem[];
}

export interface MonthlyBatchVO {
  id: number;
  batchName: string;
  batchType: string;
  appraisalPeriod: string;
  startTime?: string;
  endTime?: string;
  status: 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED';
  leaderCount: number;
  examineeCount: number;
  submittedLeaderCount: number;
  mySubmissionStatus: 'NOT_STARTED' | 'DRAFT' | 'SUBMITTED';
  isExpired: boolean;
  createdAt: string;
}

export interface MonthlyLeaderScoringVO {
  batchId: number;
  batchName: string;
  appraisalPeriod: string;
  endTime?: string;
  isExpired: boolean;
  batchStatus?: string;
  mySubmissionStatus: 'DRAFT' | 'SUBMITTED';
  metrics: MetricItem[];
  rows: {
    examineeUserId: number;
    realName: string;
    department: string;
    position: string;
    metricScores: Record<number, number>;
    totalScore: number;
  }[];
}

export interface MonthlySummaryVO {
  batchId: number;
  batchName: string;
  appraisalPeriod: string;
  status: string;
  metrics?: MetricItem[];
  leaders: {
    leaderUserId: number;
    realName: string;
    weight: number;
    submitted: boolean;
  }[];
  rows: {
    examineeUserId: number;
    realName: string;
    department: string;
    position: string;
    metricAvgScores?: Record<number, number>;
    leaderScores: Record<number, number>;
    leaderMetricScores?: Record<string, number>;
    compositeScore: number;
    rank: number;
  }[];
}

export interface ExamineePersonalResultVO {
  batchId: number;
  batchName: string;
  appraisalPeriod: string;
  myCompositeScore: number;
  myRank: number;
  totalExaminees: number;
  rankList: {
    rank: number;
    examineeUserId: number;
    realName: string;
    department: string;
    compositeScore: number;
    isMe: boolean;
  }[];
}

export interface EvaluatorProgressVO {
  userId: number;
  realName: string;
  department: string;
  position: string;
  phone: string;
  weight: number;
  status: 'NOT_STARTED' | 'DRAFT' | 'SUBMITTED';
  scoredCount: number;
  totalTargetCount: number;
  submittedAt?: string;
}

export function listMonthlyBatchesApi(params?: { status?: string; keyword?: string }): Promise<MonthlyBatchVO[]> {
  return request.get('/monthly/batches', { params });
}

export function getLeaderProgressApi(batchId: number): Promise<EvaluatorProgressVO[]> {
  return request.get(`/monthly/batches/${batchId}/progress`);
}

export function urgeLeaderApi(batchId: number, leaderUserId?: number): Promise<void> {
  return request.post(`/monthly/batches/${batchId}/urge`, null, {
    params: leaderUserId ? { leaderUserId } : {},
  });
}

export function getMonthlyBatchDetailApi(id: number): Promise<MonthlyBatchVO> {
  return request.get(`/monthly/batches/${id}`);
}

export function saveMonthlyBatchApi(data: MonthlyBatchSaveDTO): Promise<void> {
  return request.post('/monthly/batches', data);
}

export function publishMonthlyBatchApi(id: number): Promise<void> {
  return request.put(`/monthly/batches/${id}/publish`);
}

export function completeMonthlyBatchApi(id: number): Promise<void> {
  return request.put(`/monthly/batches/${id}/complete`);
}

export function getLeaderScoringDataApi(batchId: number): Promise<MonthlyLeaderScoringVO> {
  return request.get(`/monthly/scoring/${batchId}`);
}

export function saveLeaderScoresApi(data: {
  batchId: number;
  items: { examineeUserId: number; metricScores: Record<number, number> }[];
  isSubmit: boolean;
}): Promise<void> {
  return request.post('/monthly/scoring', data);
}

export function withdrawLeaderScoresApi(batchId: number): Promise<void> {
  return request.put(`/monthly/scoring/${batchId}/withdraw`);
}

export function getMonthlyBatchSummaryApi(batchId: number): Promise<MonthlySummaryVO> {
  return request.get(`/monthly/summary/${batchId}`);
}

export function getExamineePersonalResultApi(batchId: number): Promise<ExamineePersonalResultVO> {
  return request.get(`/monthly/my-result/${batchId}`);
}
