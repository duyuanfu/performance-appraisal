import React, { useEffect, useState } from 'react';
import { Modal, Table, Button, Space, Tag, Typography, message, Card, Row, Col, Statistic, Radio, Progress } from 'antd';
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
  const [roleFilter, setRoleFilter] = useState<string>('ALL');

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
      message.success('已向所有未完成评审打分的考评人员一键发送催办提醒！');
    } finally {
      setUrging(false);
    }
  };

  const submittedCount = list.filter((i) => i.status === 'SUBMITTED').length;
  const unsubmittedCount = list.length - submittedCount;
  const completionRate = list.length > 0 ? Math.round((submittedCount / list.length) * 100) : 0;

  const centerLeaderCount = list.filter(
    (i) => i.evaluatorRoleType === 'PRIMARY_LEADER' || i.evaluatorRoleType === 'CENTER_LEADER'
  ).length;
  const deptHeadCount = list.filter((i) => i.evaluatorRoleType === 'DEPT_HEAD').length;
  const expertCount = list.filter((i) => i.evaluatorRoleType === 'EXPERT').length;

  const filteredList = list.filter((item) => {
    if (roleFilter === 'CENTER_LEADER') {
      return item.evaluatorRoleType === 'PRIMARY_LEADER' || item.evaluatorRoleType === 'CENTER_LEADER';
    }
    if (roleFilter === 'DEPT_HEAD') {
      return item.evaluatorRoleType === 'DEPT_HEAD';
    }
    if (roleFilter === 'EXPERT') {
      return item.evaluatorRoleType === 'EXPERT';
    }
    if (roleFilter === 'UNSUBMITTED') {
      return item.status !== 'SUBMITTED';
    }
    return true;
  });

  const getRoleTag = (roleType?: string, roleName?: string) => {
    switch (roleType) {
      case 'PRIMARY_LEADER':
        return <Tag color="gold">{roleName || '中心主要领导'}</Tag>;
      case 'CENTER_LEADER':
        return <Tag color="blue">{roleName || '其他中心领导'}</Tag>;
      case 'DEPT_HEAD':
        return <Tag color="purple">{roleName || '部门正职'}</Tag>;
      case 'EXPERT':
        return <Tag color="orange">{roleName || '评审专家'}</Tag>;
      default:
        return <Tag>{roleName || '评审人员'}</Tag>;
    }
  };

  const columns = [
    {
      title: '序号',
      key: 'idx',
      width: 55,
      align: 'center' as const,
      render: (_: any, __: any, index: number) => index + 1,
    },
    {
      title: '考评人姓名',
      dataIndex: 'realName',
      key: 'realName',
      width: 110,
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: '主体角色分类',
      key: 'role',
      width: 140,
      render: (_: any, record: EvaluatorProgressVO) => getRoleTag(record.evaluatorRoleType, record.evaluatorRoleName),
    },
    {
      title: '所属部门',
      dataIndex: 'department',
      key: 'department',
      width: 140,
    },
    {
      title: '职务',
      dataIndex: 'position',
      key: 'position',
      width: 110,
    },
    {
      title: '评审项目范围',
      dataIndex: 'targetDesc',
      key: 'targetDesc',
      width: 130,
      render: (desc: string) => <Text type="secondary">{desc || '-'}</Text>,
    },
    {
      title: '打分进度',
      key: 'progress',
      width: 120,
      render: (_: any, record: EvaluatorProgressVO) => (
        <span style={{ fontWeight: 500 }}>
          {record.scoredCount} / {record.totalTargetCount} 个项目
        </span>
      ),
    },
    {
      title: '提交状态',
      dataIndex: 'status',
      key: 'status',
      width: 150,
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
              草稿中 (未提交)
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
      width: 110,
      align: 'center' as const,
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
            催办打分
          </Button>
        );
      },
    },
  ];

  return (
    <Modal
      title={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingRight: 32 }}>
          <span>项目评审打分进度与催促 - {batchName}</span>
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
                一键催办未提交人员 ({unsubmittedCount}人)
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
      width={1020}
      destroyOnClose
    >
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small" style={{ background: '#fafafa' }}>
            <Statistic title="考评人员总数" value={list.length} suffix="人" />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small" style={{ background: '#f6ffed' }}>
            <Statistic
              title="已完成提交"
              value={submittedCount}
              valueStyle={{ color: '#52c41a' }}
              suffix="人"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small" style={{ background: unsubmittedCount > 0 ? '#fff1f0' : '#fafafa' }}>
            <Statistic
              title="未完成待催办"
              value={unsubmittedCount}
              valueStyle={{ color: unsubmittedCount > 0 ? '#f5222d' : '#8c8c8c' }}
              suffix="人"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small" style={{ background: '#e6f7ff' }}>
            <div style={{ marginBottom: 4 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>整体提交进度</Text>
            </div>
            <Progress percent={completionRate} size="small" status={completionRate === 100 ? 'success' : 'active'} />
          </Card>
        </Col>
      </Row>

      <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Radio.Group value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)} size="small">
          <Radio.Button value="ALL">全部考评人 ({list.length})</Radio.Button>
          <Radio.Button value="CENTER_LEADER">中心领导 ({centerLeaderCount})</Radio.Button>
          <Radio.Button value="DEPT_HEAD">部门正职 ({deptHeadCount})</Radio.Button>
          <Radio.Button value="EXPERT">评审专家 ({expertCount})</Radio.Button>
          <Radio.Button value="UNSUBMITTED">未完成 ({unsubmittedCount})</Radio.Button>
        </Radio.Group>
        <Text type="secondary" style={{ fontSize: 12 }}>
          涵盖中心主要领导、其他中心领导、部门正职与评审专家
        </Text>
      </div>

      <Table
        rowKey="userId"
        columns={columns}
        dataSource={filteredList}
        loading={loading}
        pagination={false}
        bordered
        size="small"
      />
    </Modal>
  );
};
