import React, { useEffect, useState } from 'react';
import { Modal, Table, Button, Space, Tag, Typography, message, Card, Row, Col, Statistic } from 'antd';
import {
  BellOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { getExpertProgressApi, urgeExpertApi } from '../../api/project';
import { EvaluatorProgressVO } from '../../api/monthly';

const { Text } = Typography;

interface Props {
  batchId: number | null;
  batchName?: string;
  open: boolean;
  onClose: () => void;
}

export const ExpertProgressModal: React.FC<Props> = ({ batchId, batchName, open, onClose }) => {
  const [loading, setLoading] = useState(false);
  const [urging, setUrging] = useState(false);
  const [list, setList] = useState<EvaluatorProgressVO[]>([]);

  const loadProgress = async () => {
    if (!batchId) return;
    setLoading(true);
    try {
      const data = await getExpertProgressApi(batchId);
      setList(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open && batchId) {
      loadProgress();
    }
  }, [open, batchId]);

  const handleUrgeSingle = async (record: EvaluatorProgressVO) => {
    if (!batchId) return;
    try {
      await urgeExpertApi(batchId, record.userId);
      message.success(`已向【${record.realName}】成功发送项目评审催办提醒！`);
    } catch (e) {
      // Error handled
    }
  };

  const handleUrgeAll = async () => {
    if (!batchId) return;
    setUrging(true);
    try {
      await urgeExpertApi(batchId);
      message.success('已向所有未完成评审的专家一键发送催办提醒！');
    } finally {
      setUrging(false);
    }
  };

  const submittedCount = list.filter((i) => i.status === 'SUBMITTED').length;
  const unsubmittedCount = list.length - submittedCount;

  const columns = [
    {
      title: '序号',
      key: 'idx',
      width: 60,
      render: (_: any, __: any, index: number) => index + 1,
    },
    {
      title: '专家姓名',
      dataIndex: 'realName',
      key: 'realName',
      width: 120,
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: '所属部门',
      dataIndex: 'department',
      key: 'department',
      width: 150,
    },
    {
      title: '职务',
      dataIndex: 'position',
      key: 'position',
      width: 120,
    },
    {
      title: '专家权重',
      dataIndex: 'weight',
      key: 'weight',
      width: 110,
      render: (w: number) => `${Number(w).toFixed(2)}`,
    },
    {
      title: '评审进度',
      key: 'progress',
      width: 130,
      render: (_: any, record: EvaluatorProgressVO) => (
        <span>
          {record.scoredCount} / {record.totalTargetCount} 个项目
        </span>
      ),
    },
    {
      title: '提交状态',
      dataIndex: 'status',
      key: 'status',
      width: 170,
      render: (status: string, record: EvaluatorProgressVO) => {
        if (status === 'SUBMITTED') {
          return (
            <div>
              <Tag color="success" icon={<CheckCircleOutlined />}>
                已提交
              </Tag>
              {record.submittedAt && (
                <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 2 }}>
                  {dayjs(record.submittedAt).format('MM-DD HH:mm')}
                </div>
              )}
            </div>
          );
        }
        if (status === 'DRAFT') {
          return (
            <Tag color="warning" icon={<ClockCircleOutlined />}>
              草稿 (未提交)
            </Tag>
          );
        }
        return (
          <Tag color="error" icon={<ExclamationCircleOutlined />}>
            未开始打分
          </Tag>
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: any, record: EvaluatorProgressVO) => {
        if (record.status === 'SUBMITTED') {
          return <Text type="secondary">-</Text>;
        }
        return (
          <Button
            type="primary"
            size="small"
            ghost
            icon={<BellOutlined />}
            onClick={() => handleUrgeSingle(record)}
          >
            催促打分
          </Button>
        );
      },
    },
  ];

  return (
    <Modal
      title={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingRight: 32 }}>
          <span>评审专家打分进度与催促 - {batchName}</span>
          <Space>
            <Button icon={<ReloadOutlined />} size="small" onClick={loadProgress}>
              刷新
            </Button>
            {unsubmittedCount > 0 && (
              <Button
                type="primary"
                danger
                icon={<SendOutlined />}
                size="small"
                loading={urging}
                onClick={handleUrgeAll}
              >
                一键催办未提交专家 ({unsubmittedCount}人)
              </Button>
            )}
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
      width={920}
      destroyOnClose
    >
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Card size="small" style={{ background: '#fafafa' }}>
            <Statistic title="评审专家总数" value={list.length} suffix="人" />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" style={{ background: '#f6ffed' }}>
            <Statistic
              title="已完成提交"
              value={submittedCount}
              valueStyle={{ color: '#52c41a' }}
              suffix="人"
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" style={{ background: unsubmittedCount > 0 ? '#fff1f0' : '#fafafa' }}>
            <Statistic
              title="未完成打分"
              value={unsubmittedCount}
              valueStyle={{ color: unsubmittedCount > 0 ? '#f5222d' : '#8c8c8c' }}
              suffix="人"
            />
          </Card>
        </Col>
      </Row>

      <Table
        rowKey="userId"
        columns={columns}
        dataSource={list}
        loading={loading}
        pagination={false}
        bordered
        size="middle"
      />
    </Modal>
  );
};
