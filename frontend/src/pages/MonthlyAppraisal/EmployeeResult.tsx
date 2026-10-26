import React, { useState, useEffect } from 'react';
import { Card, Select, Space, Typography, Tag, Row, Col, Statistic, Empty, Button, Alert } from 'antd';
import { BarChartOutlined, UserOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import {
  listMonthlyBatchesApi,
  getExamineePersonalResultApi,
  MonthlyBatchVO,
  ExamineePersonalResultVO,
} from '../../api/monthly';

const { Text } = Typography;

export const EmployeeResultPage: React.FC = () => {
  const [batches, setBatches] = useState<MonthlyBatchVO[]>([]);
  const [selectedBatchId, setSelectedBatchId] = useState<number | null>(null);
  const [result, setResult] = useState<ExamineePersonalResultVO | null>(null);
  const [loading, setLoading] = useState(false);

  const loadBatches = async () => {
    try {
      const list = await listMonthlyBatchesApi();
      setBatches(list);
      if (list.length > 0 && !selectedBatchId) {
        setSelectedBatchId(list[0].id);
      }
    } catch (e) {
      // Handled
    }
  };

  const loadResult = async (batchId: number) => {
    setLoading(true);
    try {
      const data = await getExamineePersonalResultApi(batchId);
      setResult(data);
    } catch (e) {
      setResult(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBatches();
  }, []);

  useEffect(() => {
    if (selectedBatchId) {
      loadResult(selectedBatchId);
    }
  }, [selectedBatchId]);

  const selectedBatch = batches.find((b) => b.id === selectedBatchId);

  return (
    <div>
      <Card
        className="app-page-card"
        title={
          <div>
            <div className="app-page-title">我的月度考核结果</div>
            <div className="app-page-subtitle">查看本人月度综合得分与部门全员排名</div>
          </div>
        }
        style={{ marginBottom: 16 }}
      >
        <Space size="middle" wrap>
          <Text strong style={{ fontSize: 14 }}>选择考核月份批次</Text>
          <Select
            value={selectedBatchId}
            onChange={setSelectedBatchId}
            style={{ width: 440, maxWidth: '100%' }}
            placeholder="请选择月度考核批次"
          >
            {batches.map((b) => (
              <Select.Option key={b.id} value={b.id}>
                <span>
                  {b.batchName} ({b.appraisalPeriod})
                </span>
                <Tag
                  color={b.status === 'COMPLETED' ? 'success' : 'processing'}
                  style={{ marginLeft: 8 }}
                >
                  {b.status === 'COMPLETED' ? '已归档公布' : '评分进行中'}
                </Tag>
              </Select.Option>
            ))}
          </Select>
          <Button icon={<ReloadOutlined />} onClick={() => selectedBatchId && loadResult(selectedBatchId)}>
            刷新
          </Button>
        </Space>
      </Card>

      {result ? (
        <>
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            <Col xs={24} md={12}>
              <Card bordered={false} style={{ background: '#f0fdf4', border: '1px solid #bbf7d0' }}>
                <Statistic
                  title={<Text strong style={{ color: '#15803d', fontSize: 16 }}>我的月度综合得分</Text>}
                  value={Number(result.myCompositeScore).toFixed(4)}
                  precision={4}
                  valueStyle={{ color: '#15803d', fontSize: 40, fontWeight: 'bold' }}
                  prefix={<BarChartOutlined />}
                  suffix="分"
                />
                <Text type="secondary">（中心领导按考核指标与配置权重综合打分加权计算得出）</Text>
              </Card>
            </Col>
            <Col xs={24} md={12}>
              <Card bordered={false} style={{ background: '#eff6ff', border: '1px solid #bfdbfe' }}>
                <Statistic
                  title={<Text strong style={{ color: '#1d4ed8', fontSize: 16 }}>全员排名</Text>}
                  value={result.myRank}
                  valueStyle={{ color: '#1d4ed8', fontSize: 40, fontWeight: 'bold' }}
                  prefix={<UserOutlined />}
                  suffix={`/ 共 ${result.totalExaminees} 人`}
                />
                <Text type="secondary">（部门全体参评负责人与员工横向综合排名）</Text>
              </Card>
            </Col>
          </Row>

          <Alert
            type="info"
            showIcon
            icon={<SafetyCertificateOutlined />}
            message="考核结果保密说明"
            description="根据原料中心绩效考核保密管理规范，被考核人员仅展示本人考核综合总分及最终部门排名，不公开其他人员的得分及榜单明细。"
          />
        </>
      ) : (
        <Card>
          {selectedBatch?.status && selectedBatch.status !== 'COMPLETED' ? (
            <Alert
              type="info"
              showIcon
              message="该月度考核处于评分阶段，尚未归档公布"
              description="为保证考核客观公正与过程保密，被考核人员仅可在管理员归档发布后查看本人最终综合得分及部门全员排名，请等待考核归档。"
            />
          ) : (
            <Empty description="该批次您未参评或尚未生成有效考核结果" />
          )}
        </Card>
      )}
    </div>
  );
};
