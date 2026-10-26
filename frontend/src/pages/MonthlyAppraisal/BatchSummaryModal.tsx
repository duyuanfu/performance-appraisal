import React, { useEffect, useState } from 'react';
import { Modal, Table, Button, Space, Tag, Typography, message, Tabs } from 'antd';
import {
  DownloadOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import request from '../../api/request';
import { getMonthlyBatchSummaryApi, MonthlySummaryVO, TierSummaryRowVO } from '../../api/monthly';
import { AppraisalDetailModal } from '../../components/AppraisalDetailModal';

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
  const [activeTab, setActiveTab] = useState<string>('head');
  const [detailRow, setDetailRow] = useState<TierSummaryRowVO | null>(null);

  const fmtScore = (value?: number | null) =>
    value === undefined || value === null ? '-' : Number(value).toFixed(4);

  const loadSummary = async () => {
    if (!batchId) return;
    setLoading(true);
    try {
      const res = await getMonthlyBatchSummaryApi(batchId);
      setData(res);
      if (res.category === 'DEPT_DEPUTY') {
        setActiveTab('deputy');
      } else {
        setActiveTab('head');
      }
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
      title: '中心主要领导',
      dataIndex: 'primaryLeaderScore',
      key: 'primaryLeaderScore',
      width: 130,
      render: (s: number) => (s === undefined || s === null ? '-' : Number(s).toFixed(4)),
    },
    {
      title: '分管领导',
      dataIndex: 'supervisorLeaderScore',
      key: 'supervisorLeaderScore',
      width: 130,
      render: (s: number) => (s === undefined || s === null ? '-' : Number(s).toFixed(4)),
    },
    {
      title: '其他中心领导',
      dataIndex: 'otherLeaderScore',
      key: 'otherLeaderScore',
      width: 150,
      render: (s: number) => (s === undefined || s === null ? '-' : Number(s).toFixed(4)),
    },
    {
      title: '部门正职互评',
      dataIndex: 'peerScore',
      key: 'peerScore',
      width: 150,
      render: (s: number) => (s === undefined || s === null ? '-' : Number(s).toFixed(4)),
    },
    {
      title: '综合得分',
      dataIndex: 'compositeScore',
      key: 'compositeScore',
      width: 130,
      render: (s: number) => (
        <Text strong style={{ color: '#2563eb', fontSize: 16 }}>
          {Number(s).toFixed(4)}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right' as const,
      render: (_: any, record: TierSummaryRowVO) => (
        <Button type="link" size="small" onClick={() => setDetailRow(record)}>
          查看明细
        </Button>
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
      title: '分管领导',
      dataIndex: 'supervisorLeaderScore',
      key: 'supervisorLeaderScore',
      width: 130,
      render: (s: number) => (s === undefined || s === null ? '-' : Number(s).toFixed(4)),
    },
    {
      title: '其他中心领导',
      dataIndex: 'otherLeaderScore',
      key: 'otherLeaderScore',
      width: 150,
      render: (s: number) => (s === undefined || s === null ? '-' : Number(s).toFixed(4)),
    },
    {
      title: '本部门正职',
      dataIndex: 'deptHeadScore',
      key: 'deptHeadScore',
      width: 150,
      render: (s: number) => (s === undefined || s === null ? '-' : Number(s).toFixed(4)),
    },
    {
      title: '部门副职互评',
      dataIndex: 'peerScore',
      key: 'peerScore',
      width: 150,
      render: (s: number) => (s === undefined || s === null ? '-' : Number(s).toFixed(4)),
    },
    {
      title: '综合得分',
      dataIndex: 'compositeScore',
      key: 'compositeScore',
      width: 130,
      render: (s: number) => (
        <Text strong style={{ color: '#2563eb', fontSize: 16 }}>
          {Number(s).toFixed(4)}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right' as const,
      render: (_: any, record: TierSummaryRowVO) => (
        <Button type="link" size="small" onClick={() => setDetailRow(record)}>
          查看明细
        </Button>
      ),
    },
  ];

  // 3. 各指标平均分明细（仅展示单项指标平均分，综合得分与排名以汇总表为准）
  const metricAverageColumns = [
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
      title: '职务',
      dataIndex: 'position',
      key: 'position',
      width: 120,
    },
    ...(data?.metrics || []).map((m: any) => ({
      title: `${m.metricName} (${Number(m.weight).toFixed(0)}%)`,
      key: `metric_${m.id}`,
      width: 140,
      render: (_: any, record: any) => {
        const score = record.metricAvgScores?.[m.id];
        return score === undefined || score === null ? (
          <Text type="secondary">-</Text>
        ) : (
          <span>{Number(score).toFixed(4)}</span>
        );
      },
    })),
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
          scroll={{ x: 1100 }}
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
          scroll={{ x: 1100 }}
        />
      ),
    },
    {
      key: 'metrics',
      label: `各指标平均分明细 (${data?.rows?.length || 0}人)`,
      children: (
        <div>
          <div style={{ marginBottom: 8 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              展示每位被考核人各考核指标的平均得分（按所有已提交评分人计算），用于分析得分构成；综合得分与排名请以汇总表为准。
            </Text>
          </div>
          <Table
            rowKey="examineeUserId"
            columns={metricAverageColumns}
            dataSource={data?.rows || []}
            loading={loading}
            pagination={false}
            size="middle"
            bordered
            scroll={{ x: 900 }}
          />
        </div>
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

      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {data?.category === 'DEPT_DEPUTY'
            ? '权重：分管领导 35% · 其他中心领导 20% · 本部门正职 30% · 部门副职互评 15%'
            : '权重：中心主要领导 25% · 分管领导 35% · 其他中心领导 15% · 部门正职互评 25%'}
        </Text>
      </div>

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />

      <AppraisalDetailModal
        open={!!detailRow}
        onClose={() => setDetailRow(null)}
        title={`评分明细 - ${detailRow?.realName || ''}`}
        subtitle={
          <Text type="secondary">
            {detailRow?.department || '-'} · {detailRow?.position || '-'}
          </Text>
        }
        metrics={data?.metrics || []}
        evaluators={detailRow?.evaluatorDetails || []}
        summaryItems={
          detailRow
            ? [
                ...(detailRow.positionCategory === 'DEPT_DEPUTY'
                  ? []
                  : [{ label: '中心主要领导', value: fmtScore(detailRow.primaryLeaderScore) }]),
                { label: '分管领导', value: fmtScore(detailRow.supervisorLeaderScore) },
                { label: '其他中心领导', value: fmtScore(detailRow.otherLeaderScore) },
                ...(detailRow.positionCategory === 'DEPT_DEPUTY'
                  ? [{ label: '本部门正职', value: fmtScore(detailRow.deptHeadScore) }]
                  : []),
                {
                  label: detailRow.positionCategory === 'DEPT_DEPUTY' ? '部门副职互评' : '部门正职互评',
                  value: fmtScore(detailRow.peerScore),
                },
                {
                  label: '综合得分',
                  value: (
                    <Text strong style={{ color: '#2563eb' }}>
                      {fmtScore(detailRow.compositeScore)}
                    </Text>
                  ),
                },
                { label: '排名', value: `第 ${detailRow.rank} 名` },
              ]
            : []
        }
      />
    </Modal>
  );
};
