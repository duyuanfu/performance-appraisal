import React, { useEffect, useState } from 'react';
import { Modal, Table, Button, Space, Tag, Typography, message, Card } from 'antd';
import {
  DownloadOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import request from '../../api/request';
import { getProjectSummaryApi, AnnualProjectSummaryVO } from '../../api/project';

const { Text } = Typography;

const AWARD_TAG_MAP: Record<string, { label: string; color: string }> = {
  SPECIAL: { label: '特等奖', color: 'gold' },
  FIRST: { label: '一等奖', color: 'red' },
  SECOND: { label: '二等奖', color: 'orange' },
  THIRD: { label: '三等奖', color: 'blue' },
  NONE: { label: '未获奖', color: 'default' },
};

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
      link.download = `${data?.batchName || '项目评审汇总'}-${batchId}.xlsx`;
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

  const isSpecialInnovation = data?.batchType === 'SPECIAL_INNOVATION';
  const isTargetProject = data?.batchType === 'TARGET_PROJECT';

  const columns = [
    {
      title: '排序',
      dataIndex: 'rank',
      key: 'rank',
      width: 70,
      fixed: 'left' as const,
      render: (rank: number) => {
        const color = rank === 1 ? '#f5222d' : rank === 2 ? '#fa8c16' : rank === 3 ? '#faad14' : '#595959';
        return <Tag color={color} style={{ fontWeight: 'bold' }}>{rank}</Tag>;
      },
    },
    {
      title: '项目名称',
      dataIndex: 'projectName',
      key: 'projectName',
      width: 220,
      fixed: 'left' as const,
    },
    {
      title: '分管领导',
      dataIndex: 'supervisorName',
      key: 'supervisorName',
      width: 130,
      render: (name: string) => name || '-',
    },
    ...(isSpecialInnovation
      ? [
          {
            title: '评定奖项',
            dataIndex: 'awardTier',
            key: 'awardTier',
            width: 120,
            render: (tier: string) => {
              const item = AWARD_TAG_MAP[tier] || { label: '未获奖', color: 'default' };
              return (
                <Tag icon={tier && tier !== 'NONE' ? <TrophyOutlined /> : undefined} color={item.color}>
                  {item.label}
                </Tag>
              );
            },
          },
        ]
      : []),
    {
      title: '中心主要领导评分',
      dataIndex: 'primaryLeaderScore',
      key: 'primaryLeaderScore',
      width: 130,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '分管领导评分',
      dataIndex: 'supervisorLeaderScore',
      key: 'supervisorLeaderScore',
      width: 130,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '其它中心领导评分',
      dataIndex: 'otherLeaderScore',
      key: 'otherLeaderScore',
      width: 170,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '正职评分',
      dataIndex: 'headScore',
      key: 'headScore',
      width: 120,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '代表评分（评审专家评分）',
      dataIndex: 'expertScore',
      key: 'expertScore',
      width: 180,
      render: (s: number) => (s ? Number(s).toFixed(4) : '-'),
    },
    {
      title: '综合得分',
      dataIndex: 'compositeScore',
      key: 'compositeScore',
      width: 130,
      fixed: 'right' as const,
      render: (score: number) => (
        <Text strong style={{ color: '#1890ff', fontSize: 16 }}>
          {Number(score).toFixed(4)}
        </Text>
      ),
    },
    ...(isTargetProject
      ? [
          {
            title: '得分系数',
            dataIndex: 'scoreCoefficient',
            key: 'scoreCoefficient',
            width: 110,
            fixed: 'right' as const,
            render: (coef: number) => (coef ? Number(coef).toFixed(4) : '1.0000'),
          },
          {
            title: '部门奖励金额(万元)',
            dataIndex: 'bonusAmount',
            key: 'bonusAmount',
            width: 160,
            fixed: 'right' as const,
            render: (amt: number) => (
              <Text strong style={{ color: '#52c41a' }}>
                {amt ? `${Number(amt).toFixed(2)} 万元` : '-'}
              </Text>
            ),
          },
        ]
      : []),
  ];

  return (
    <Modal
      title={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingRight: 32 }}>
          <span>
            {isSpecialInnovation
              ? '专项与创新任务评审汇总与奖次评定'
              : isTargetProject
              ? '目标绩效任务评审汇总与奖金测算'
              : '项目评审汇总排名'}{' '}
            - {data?.batchName}
          </span>
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
      width={1280}
      destroyOnClose
    >
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Text strong style={{ marginRight: 12 }}>评审提交进度：</Text>
          <Space wrap>
            {data?.experts.map((e) => (
              <Tag
                key={e.expertUserId}
                icon={e.submitted ? <CheckCircleOutlined /> : <ClockCircleOutlined />}
                color={e.submitted ? 'success' : 'warning'}
              >
                {e.realName}：{e.submitted ? '已提交' : '未提交/草稿'}
              </Tag>
            ))}
          </Space>
        </div>
        {data?.averageScore !== undefined && (
          <Text strong style={{ fontSize: 13 }}>
            所有参评项目平均分：<span style={{ color: '#1890ff' }}>{Number(data.averageScore).toFixed(4)} 分</span>
          </Text>
        )}
      </div>

      <Table
        rowKey="projectId"
        columns={columns}
        dataSource={data?.rows || []}
        loading={loading}
        pagination={false}
        size="middle"
        bordered
        scroll={{ x: 1300 }}
      />
    </Modal>
  );
};
