import React, { useEffect, useState } from 'react';
import { Modal, Table, Button, Space, Tag, Typography, message, Card } from 'antd';
import {
  DownloadOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import request from '../../api/request';
import { getMonthlyBatchSummaryApi, MonthlySummaryVO } from '../../api/monthly';

const { Text } = Typography;

interface Props {
  batchId: number | null;
  open: boolean;
  onClose: () => void;
}

export const BatchSummaryModal: React.FC<Props> = ({ batchId, open, onClose }) => {
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [data, setData] = useState<MonthlySummaryVO | null>(null);

  const loadSummary = async () => {
    if (!batchId) return;
    setLoading(true);
    try {
      const res = await getMonthlyBatchSummaryApi(batchId);
      setData(res);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open && batchId) {
      loadSummary();
    }
  }, [open, batchId]);

  const handleExport = async () => {
    if (!batchId) return;
    setExporting(true);
    try {
      const response = await request.get(`/monthly/export/${batchId}`, {
        responseType: 'blob',
      });
      const blob = new Blob([response as any], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `月度绩效考核汇总表-${batchId}.xlsx`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      message.success('Excel 报表导出成功');
    } catch (e: any) {
      message.error(e.message || '导出报表失败');
    } finally {
      setExporting(false);
    }
  };

  // Expandable row rendering showing each leader's exact score on each metric
  const expandedRowRender = (record: any) => {
    const leaderMetricColumns = [
      {
        title: '考核指标项',
        dataIndex: 'metricName',
        key: 'metricName',
        width: 150,
      },
      {
        title: '满分/权重',
        key: 'weight',
        width: 130,
        render: (_: any, m: any) => `${m.maxScore}分 (${Number(m.weight).toFixed(2)}%)`,
      },
      ...(data?.leaders || []).map((l) => ({
        title: `${l.realName}评分 (${Number(l.weight).toFixed(2)}%)`,
        key: `leader_score_${l.leaderUserId}`,
        render: (_: any, m: any) => {
          const score = record.leaderMetricScores?.[`${l.leaderUserId}_${m.id}`];
          return score !== undefined ? (
            <Text strong style={{ color: '#262626' }}>
              {Number(score).toFixed(4)}
            </Text>
          ) : (
            <Text type="secondary">-</Text>
          );
        },
      })),
      {
        title: '该项综合加权得分',
        key: 'metricAvg',
        width: 150,
        render: (_: any, m: any) => {
          const score = record.metricAvgScores?.[m.id];
          return (
            <Text strong style={{ color: '#1890ff' }}>
              {score !== undefined ? Number(score).toFixed(4) : '-'}
            </Text>
          );
        },
      },
    ];

    return (
      <Card size="small" title={<Text strong>领导单项指标打分明细：</Text>} style={{ margin: '8px 0' }}>
        <Table
          rowKey="id"
          columns={leaderMetricColumns}
          dataSource={data?.metrics || []}
          pagination={false}
          size="small"
          bordered
        />
      </Card>
    );
  };

  const dynamicColumns = [
    {
      title: '排名',
      dataIndex: 'rank',
      key: 'rank',
      width: 70,
      fixed: 'left' as const,
      render: (rank: number) => {
        const color =
          rank === 1 ? '#f5222d' : rank === 2 ? '#fa8c16' : rank === 3 ? '#faad14' : '#595959';
        return <Tag color={color} style={{ fontWeight: 'bold' }}>{rank}</Tag>;
      },
    },
    {
      title: '姓名',
      dataIndex: 'realName',
      key: 'realName',
      width: 110,
      fixed: 'left' as const,
    },
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
      width: 130,
    },
    {
      title: '职务',
      dataIndex: 'position',
      key: 'position',
      width: 110,
    },
    // Metrics composite scores columns
    ...(data?.metrics || []).map((m: any) => ({
      title: `${m.metricName} (${Number(m.weight).toFixed(0)}%)`,
      key: `metric_${m.id}`,
      width: 130,
      render: (_: any, record: any) => {
        const score = record.metricAvgScores?.[m.id!];
        return score !== undefined ? (
          <span>{Number(score).toFixed(4)}</span>
        ) : (
          <Text type="secondary">-</Text>
        );
      },
    })),
    // Leaders total scores columns
    ...(data?.leaders || []).map((l) => ({
      title: `${l.realName}总分 (${Number(l.weight).toFixed(2)}%)`,
      key: `leader_${l.leaderUserId}`,
      width: 140,
      render: (_: any, record: any) => {
        const score = record.leaderScores?.[l.leaderUserId];
        return score !== undefined ? (
          <Text strong style={{ color: '#595959' }}>
            {Number(score).toFixed(4)}
          </Text>
        ) : (
          <Text type="secondary">-</Text>
        );
      },
    })),
    {
      title: '综合总分 (加权)',
      dataIndex: 'compositeScore',
      key: 'compositeScore',
      width: 150,
      fixed: 'right' as const,
      render: (score: number) => (
        <Text strong style={{ color: '#1890ff', fontSize: 16 }}>
          {Number(score).toFixed(4)}
        </Text>
      ),
    },
  ];

  return (
    <Modal
      title={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingRight: 32 }}>
          <span>月度绩效考核汇总排名 - {data?.batchName}</span>
          <Space>
            <Button icon={<ReloadOutlined />} size="small" onClick={loadSummary}>
              刷新
            </Button>
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              size="small"
              loading={exporting}
              onClick={handleExport}
            >
              导出 Excel 报表
            </Button>
          </Space>
        </div>
      }
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="close" type="primary" onClick={onClose}>
          关闭
        </Button>,
      ]}
      width={1180}
      destroyOnClose
    >
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Text strong style={{ marginRight: 12 }}>评分领导提交进度：</Text>
          <Space wrap>
            {data?.leaders.map((l) => (
              <Tag
                key={l.leaderUserId}
                icon={l.submitted ? <CheckCircleOutlined /> : <ClockCircleOutlined />}
                color={l.submitted ? 'success' : 'warning'}
              >
                {l.realName} (权重 {Number(l.weight).toFixed(2)}%)：{l.submitted ? '已提交' : '未提交/草稿'}
              </Tag>
            ))}
          </Space>
        </div>
        <Text type="secondary" style={{ fontSize: 12 }}>
          <InfoCircleOutlined style={{ marginRight: 4 }} />
          点击表格行左侧展开图标 [+] 可查看各领导对该员工的具体单项指标打分明细
        </Text>
      </div>

      <Table
        rowKey="examineeUserId"
        columns={dynamicColumns}
        dataSource={data?.rows || []}
        loading={loading}
        pagination={false}
        scroll={{ x: 'max-content', y: 480 }}
        bordered
        size="middle"
        expandable={{
          expandedRowRender,
          rowExpandable: () => true,
        }}
      />
    </Modal>
  );
};
