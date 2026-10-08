import React, { useEffect, useState } from 'react';
import { Modal, Table, Button, Space, Tag, Typography, message } from 'antd';
import {
  DownloadOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import request from '../../api/request';
import { getProjectSummaryApi, AnnualProjectSummaryVO } from '../../api/project';

const { Text } = Typography;

interface Props {
  batchId: number | null;
  open: boolean;
  onClose: () => void;
}

export const ProjectBatchSummaryModal: React.FC<Props> = ({ batchId, open, onClose }) => {
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [data, setData] = useState<AnnualProjectSummaryVO | null>(null);

  const loadSummary = async () => {
    if (!batchId) return;
    setLoading(true);
    try {
      const res = await getProjectSummaryApi(batchId);
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
      const response = await request.get(`/projects/export/${batchId}`, {
        responseType: 'blob',
      });
      const blob = new Blob([response as any], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `年度目标管理项目评审汇总表-${batchId}.xlsx`;
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
      title: '项目序号',
      dataIndex: 'projectCode',
      key: 'projectCode',
      width: 90,
      fixed: 'left' as const,
    },
    {
      title: '项目名称',
      dataIndex: 'projectName',
      key: 'projectName',
      width: 200,
      fixed: 'left' as const,
    },
    {
      title: '项目负责人',
      dataIndex: 'ownerName',
      key: 'ownerName',
      width: 110,
    },
    // Metrics average scores
    ...(data?.metrics || []).map((m) => ({
      title: `${m.metricName} (${Number(m.maxScore).toFixed(0)}分)`,
      key: `metric_${m.id}`,
      width: 120,
      render: (_: any, record: any) => {
        const avg = record.metricAvgScores?.[m.id!];
        return avg !== undefined ? Number(avg).toFixed(4) : <Text type="secondary">-</Text>;
      },
    })),
    // Experts scores
    ...(data?.experts || []).map((e) => ({
      title: `${e.realName} (${Number(e.weight).toFixed(2)}%)`,
      key: `expert_${e.expertUserId}`,
      width: 130,
      render: (_: any, record: any) => {
        const score = record.expertScores?.[e.expertUserId];
        return score !== undefined ? Number(score).toFixed(4) : <Text type="secondary">-</Text>;
      },
    })),
    {
      title: '最终综合得分',
      dataIndex: 'compositeScore',
      key: 'compositeScore',
      width: 140,
      fixed: 'right' as const,
      render: (score: number) => (
        <Text strong style={{ color: '#1890ff', fontSize: 15 }}>
          {Number(score).toFixed(4)}
        </Text>
      ),
    },
  ];

  return (
    <Modal
      title={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingRight: 32 }}>
          <span>年度目标管理项目评审汇总 - {data?.batchName}</span>
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
      width={1100}
      destroyOnClose
    >
      <div style={{ marginBottom: 16 }}>
        <Text strong style={{ marginRight: 12 }}>评审专家打分提交进度：</Text>
        <Space wrap>
          {data?.experts.map((e) => (
            <Tag
              key={e.expertUserId}
              icon={e.submitted ? <CheckCircleOutlined /> : <ClockCircleOutlined />}
              color={e.submitted ? 'success' : 'warning'}
            >
              {e.realName} (权重 {Number(e.weight).toFixed(2)}%)：{e.submitted ? '已提交' : '未提交/草稿'}
            </Tag>
          ))}
        </Space>
      </div>

      <Table
        rowKey="projectId"
        columns={dynamicColumns}
        dataSource={data?.rows || []}
        loading={loading}
        pagination={false}
        scroll={{ x: 'max-content', y: 480 }}
        bordered
        size="middle"
      />
    </Modal>
  );
};
