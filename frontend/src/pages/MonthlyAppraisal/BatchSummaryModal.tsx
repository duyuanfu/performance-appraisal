import React, { useEffect, useState } from 'react';
import { Modal, Table, Button, Space, Tag, Typography, message, Card, Tabs } from 'antd';
import {
  DownloadOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import request from '../../api/request';
import { getMonthlyBatchSummaryApi, MonthlySummaryVO, TierSummaryRowVO } from '../../api/monthly';

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
      message.success('Excel 报表导出成功（包含正职/副职双Sheet）');
    } catch (e: any) {
      message.error(e.message || '导出报表失败');
    } finally {
      setExporting(false);
    }
  };

  // 1. 部门正职汇总列
  const headColumns = [
    {
      title: '排名',
      dataIndex: 'rank',
      key: 'rank',
      width: 70,
      render: (r: number) => <Tag color={r <= 3 ? 'volcano' : 'default'} style={{ fontWeight: 'bold' }}>{r}</Tag>,
    },
    {
      title: '姓名',
      dataIndex: 'realName',
      key: 'realName',
      width: 110,
    },
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
      width: 160,
    },
    {
      title: '主要领导 (25%)',
      dataIndex: 'primaryLeaderScore',
      key: 'primaryLeaderScore',
      width: 130,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '分管领导 (35%)',
      dataIndex: 'supervisorLeaderScore',
      key: 'supervisorLeaderScore',
      width: 130,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '其他中心领导均分 (15%)',
      dataIndex: 'otherLeaderScore',
      key: 'otherLeaderScore',
      width: 170,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '部门正职互评均分 (25%)',
      dataIndex: 'peerScore',
      key: 'peerScore',
      width: 170,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '综合得分',
      dataIndex: 'compositeScore',
      key: 'compositeScore',
      width: 130,
      render: (s: number) => (
        <Text strong style={{ color: '#1890ff', fontSize: 16 }}>
          {Number(s).toFixed(4)}
        </Text>
      ),
    },
  ];

  // 2. 部门副职汇总列
  const deputyColumns = [
    {
      title: '排名',
      dataIndex: 'rank',
      key: 'rank',
      width: 70,
      render: (r: number) => <Tag color={r <= 3 ? 'volcano' : 'default'} style={{ fontWeight: 'bold' }}>{r}</Tag>,
    },
    {
      title: '姓名',
      dataIndex: 'realName',
      key: 'realName',
      width: 110,
    },
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
      width: 160,
    },
    {
      title: '分管领导 (35%)',
      dataIndex: 'supervisorLeaderScore',
      key: 'supervisorLeaderScore',
      width: 130,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '其他中心领导均分 (20%)',
      dataIndex: 'otherLeaderScore',
      key: 'otherLeaderScore',
      width: 170,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '本部门正职 (30%)',
      dataIndex: 'deptHeadScore',
      key: 'deptHeadScore',
      width: 140,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '部门副职互评均分 (15%)',
      dataIndex: 'peerScore',
      key: 'peerScore',
      width: 170,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '综合得分',
      dataIndex: 'compositeScore',
      key: 'compositeScore',
      width: 130,
      render: (s: number) => (
        <Text strong style={{ color: '#1890ff', fontSize: 16 }}>
          {Number(s).toFixed(4)}
        </Text>
      ),
    },
  ];

  // 3. 全员明细列
  const detailColumns = [
    {
      title: '排名',
      dataIndex: 'rank',
      key: 'rank',
      width: 70,
      render: (rank: number) => <Tag color="blue" style={{ fontWeight: 'bold' }}>{rank}</Tag>,
    },
    {
      title: '姓名',
      dataIndex: 'realName',
      key: 'realName',
      width: 110,
    },
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
      width: 150,
    },
    {
      title: '职务',
      dataIndex: 'position',
      key: 'position',
      width: 110,
    },
    ...(data?.metrics || []).map((m: any) => ({
      title: `${m.metricName} (${Number(m.weight).toFixed(0)}%)`,
      key: `metric_${m.id}`,
      width: 130,
      render: (_: any, record: any) => {
        const score = record.metricAvgScores?.[m.id!];
        return score !== undefined ? <span>{Number(score).toFixed(4)}</span> : <Text type="secondary">-</Text>;
      },
    })),
    {
      title: '综合得分',
      dataIndex: 'compositeScore',
      key: 'compositeScore',
      width: 130,
      render: (score: number) => (
        <Text strong style={{ color: '#1890ff', fontSize: 16 }}>
          {Number(score).toFixed(4)}
        </Text>
      ),
    },
  ];

  const tabItems = [
    {
      key: 'head',
      label: `内设部门正职考评汇总表 (${data?.headRows?.length || 0}人)`,
      children: (
        <Table
          rowKey="examineeUserId"
          columns={headColumns}
          dataSource={data?.headRows || []}
          loading={loading}
          pagination={false}
          size="middle"
          bordered
        />
      ),
    },
    {
      key: 'deputy',
      label: `内设部门副职考评汇总表 (${data?.deputyRows?.length || 0}人)`,
      children: (
        <Table
          rowKey="examineeUserId"
          columns={deputyColumns}
          dataSource={data?.deputyRows || []}
          loading={loading}
          pagination={false}
          size="middle"
          bordered
        />
      ),
    },
    {
      key: 'all',
      label: `全员指标综合打分明细 (${data?.rows?.length || 0}人)`,
      children: (
        <Table
          rowKey="examineeUserId"
          columns={detailColumns}
          dataSource={data?.rows || []}
          loading={loading}
          pagination={false}
          size="middle"
          bordered
        />
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
      width={1200}
      destroyOnClose
    >
      <div style={{ marginBottom: 16 }}>
        <Text strong style={{ marginRight: 12 }}>考评提交进度：</Text>
        <Space wrap>
          {data?.leaders.map((l) => (
            <Tag
              key={l.leaderUserId}
              icon={l.submitted ? <CheckCircleOutlined /> : <ClockCircleOutlined />}
              color={l.submitted ? 'success' : 'warning'}
            >
              {l.realName}：{l.submitted ? '已提交' : '未提交/草稿'}
            </Tag>
          ))}
        </Space>
      </div>

      <Tabs defaultActiveKey="head" items={tabItems} />
    </Modal>
  );
};
