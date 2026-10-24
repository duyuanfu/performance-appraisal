import request from './request';

export interface MetricItem {
  id?: number;
  batchId?: number;
  metricName: string;
  metricDesc?: string;
  maxScore: number;
  weight: number;
  sortOrder?: number;
}

export function listMetricsApi(batchId: number): Promise<MetricItem[]> {
  return request.get(`/batches/${batchId}/metrics`);
}

export function saveBatchMetricsApi(batchId: number, metrics: MetricItem[]): Promise<void> {
  return request.post(`/batches/${batchId}/metrics`, metrics);
}

export function getDefaultTemplatesApi(batchType: string): Promise<MetricItem[]> {
  return request.get('/metrics/templates', { params: { batchType } });
}
