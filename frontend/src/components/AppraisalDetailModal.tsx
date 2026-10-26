import React from 'react';
import { Modal, Table, Tag, Typography, Descriptions } from 'antd';
import dayjs from 'dayjs';
import type { EvaluatorScoreDetail } from '../api/monthly';

const { Text } = Typography;

interface SummaryItem {
  label: string;
  value: React.ReactNode;
}

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: React.ReactNode;
  metrics: { id?: number; metricName: string; weight: number }[];
  evaluators: EvaluatorScoreDetail[];
  summaryItems?: SummaryItem[];
}

const ROLE_COLORS: Record<string, string> = {
  PRIMARY_LEADER: 'gold',
  SUPERVISOR_LEADER: 'blue',
  CENTER_LEADER: 'blue',
  OTHER_LEADER: 'cyan',
  DEPT_HEAD: 'purple',
  DEPT_HEAD_PEER: 'geekblue',
  DEPT_DEPUTY_PEER: 'geekblue',
  DEPT_DEPUTY: 'geekblue',
  EXPERT: 'green',
};

const renderStatus = (status?: string) => {
  if (status === 'SUBMITTED') {
    return <Tag color="success">已提交</Tag>;
  }
  if (status === 'DRAFT') {
    return <Tag color="warning">草稿</Tag>;
  }
  return <Tag color="error">未开始</Tag>;
};

const formatScore = (value?: number | null) =>
  value === undefined || value === null ? '-' : Number(value).toFixed(4);

export const AppraisalDetailModal: React.FC<Props> = ({
  open,
  onClose,
  title,
  subtitle,
  metrics,
  evaluators,
  summaryItems,
}) => {
  const columns = [
    {
      title: '序号',
      key: 'index',
      width: 60,
      align: 'center' as const,
      render: (_: any, __: any, index: number) => index + 1,
    },
    {
      title: '评分人',
      dataIndex: 'realName',
      key: 'realName',
      width: 110,
      fixed: 'left' as const,
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: '角色',
      key: 'role',
      width: 140,
      render: (_: any, record: EvaluatorScoreDetail) => (
        <Tag color={ROLE_COLORS[record.roleType || ''] || 'default'}>
          {record.roleName || record.roleType || '-'}
        </Tag>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: (_: any, record: EvaluatorScoreDetail) => renderStatus(record.status),
    },
    ...metrics.map((metric) => ({
      title: `${metric.metricName} (${Number(metric.weight).toFixed(0)}%)`,
      key: `metric_${metric.id}`,
      width: 130,
      render: (_: any, record: EvaluatorScoreDetail) => {
        const score = metric.id !== undefined ? record.metricScores?.[metric.id] : undefined;
        return score === undefined || score === null ? (
          <Text type="secondary">-</Text>
        ) : (
          <span>{Number(score).toFixed(2)}</span>
        );
      },
    })),
    {
      title: '总分',
      dataIndex: 'totalScore',
      key: 'totalScore',
      width: 110,
      render: (score?: number | null) =>
        score === undefined || score === null ? (
          <Text type="secondary">-</Text>
        ) : (
          <Text strong style={{ color: '#2563eb' }}>
            {formatScore(score)}
          </Text>
        ),
    },
    {
      title: '提交时间',
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      width: 150,
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
  ];

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      width={1100}
      destroyOnClose
      title={title}
    >
      {subtitle && <div style={{ marginBottom: 12 }}>{subtitle}</div>}
      {summaryItems && summaryItems.length > 0 && (
        <Descriptions
          bordered
          size="small"
          column={{ xs: 1, sm: 2, md: 4 }}
          style={{ marginBottom: 16 }}
        >
          {summaryItems.map((item) => (
            <Descriptions.Item key={item.label} label={item.label}>
              {item.value}
            </Descriptions.Item>
          ))}
        </Descriptions>
      )}
      <Table
        rowKey={(record) => `${record.evaluatorUserId}_${record.roleType || ''}`}
        columns={columns as any}
        dataSource={evaluators}
        pagination={false}
        size="small"
        bordered
        scroll={{ x: 900 }}
      />
    </Modal>
  );
};
