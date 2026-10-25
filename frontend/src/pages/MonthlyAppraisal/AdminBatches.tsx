import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  DatePicker,
  Select,
  InputNumber,
  Row,
  Col,
  message,
  Typography,
  Divider,
  Radio,
  Alert,
} from 'antd';
import {
  PlusOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  BarChartOutlined,
  ReloadOutlined,
  BellOutlined,
  RedoOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  listMonthlyBatchesApi,
  publishMonthlyBatchApi,
  completeMonthlyBatchApi,
  deleteMonthlyBatchApi,
  saveMonthlyBatchApi,
  getMonthlyBatchSummaryApi,
  getCandidateExamineesApi,
  MonthlyBatchVO,
  MonthlyBatchSaveDTO,
  RoleWeightItem,
} from '../../api/monthly';
import { listUsersApi } from '../../api/user';
import { UserVO } from '../../api/auth';
import { useAuth } from '../../context/AuthContext';
import { MetricItem, getDefaultTemplatesApi } from '../../api/metric';
import { MetricConfigTable } from '../../components/MetricConfig/MetricConfigTable';
import { BatchSummaryModal } from './BatchSummaryModal';
import { LeaderProgressModal } from './LeaderProgressModal';
import { Popconfirm } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';

const { Text } = Typography;

const DEFAULT_HEAD_ROLE_WEIGHTS: RoleWeightItem[] = [
  { roleCategory: 'PRIMARY_LEADER', roleName: '中心主要领导 (陈剑主任)', weight: 25, sortOrder: 1 },
  { roleCategory: 'SUPERVISOR_LEADER', roleName: '分管领导 (对应部门领导)', weight: 35, sortOrder: 2 },
  { roleCategory: 'OTHER_LEADER', roleName: '其他中心领导 (其余4位领导均分)', weight: 15, sortOrder: 3 },
  { roleCategory: 'PEER_HEAD', roleName: '部门正职互评 (其余10位正职均分)', weight: 25, sortOrder: 4 },
];

const DEFAULT_DEPUTY_ROLE_WEIGHTS: RoleWeightItem[] = [
  { roleCategory: 'SUPERVISOR_LEADER', roleName: '中心分管领导 (对应部门领导)', weight: 35, sortOrder: 1 },
  { roleCategory: 'OTHER_LEADER', roleName: '其他中心领导 (其余5位领导均分)', weight: 20, sortOrder: 2 },
  { roleCategory: 'DEPT_HEAD', roleName: '本部门正职 (直属正职领导)', weight: 30, sortOrder: 3 },
  { roleCategory: 'PEER_DEPUTY', roleName: '部门副职互评 (其余11位副职均分)', weight: 15, sortOrder: 4 },
];

export const MonthlyAdminBatchesPage: React.FC = () => {
  const { activeRole } = useAuth();
  const [batches, setBatches] = useState<MonthlyBatchVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [summaryModalBatchId, setSummaryModalBatchId] = useState<number | null>(null);
  const [progressModalBatch, setProgressModalBatch] = useState<{ id: number; name: string } | null>(
    null
  );
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'IN_PROGRESS' | 'COMPLETED' | 'DRAFT'>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<'ALL' | 'DEPT_HEAD' | 'DEPT_DEPUTY'>('ALL');

  // Batch create modal state
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [targetCategory, setTargetCategory] = useState<'DEPT_HEAD' | 'DEPT_DEPUTY'>('DEPT_HEAD');
  const [relaunchBatchId, setRelaunchBatchId] = useState<number | null>(null);
  const [roleWeights, setRoleWeights] = useState<RoleWeightItem[]>(DEFAULT_HEAD_ROLE_WEIGHTS);
  const [examineeCandidates, setExamineeCandidates] = useState<UserVO[]>([]);
  const [metrics, setMetrics] = useState<MetricItem[]>([]);
  const [form] = Form.useForm();

  const loadBatches = async () => {
    setLoading(true);
    try {
      const data = await listMonthlyBatchesApi();
      setBatches(data);
    } finally {
      setLoading(false);
    }
  };

  const loadUsers = async () => {
    const allExaminees = await getCandidateExamineesApi();
    setExamineeCandidates(allExaminees);
  };

  useEffect(() => {
    loadBatches();
    loadUsers();
  }, []);

  const handleOpenCreateModal = async () => {
    form.resetFields();
    setRelaunchBatchId(null);
    const defaultMetrics = await getDefaultTemplatesApi('MONTHLY');
    setMetrics(defaultMetrics);

    const initialCat = 'DEPT_HEAD';
    setTargetCategory(initialCat);
    setRoleWeights(DEFAULT_HEAD_ROLE_WEIGHTS);

    form.setFieldsValue({
      appraisalPeriod: dayjs(),
      batchName: `${dayjs().format('YYYY年M月')}内设部门正职绩效考核`,
      examineeUserIds: examineeCandidates
        .filter((u) => u.positionCategory === initialCat)
        .map((u) => u.id),
      dateRange: [dayjs(), dayjs().add(7, 'day')],
    });

    setCreateModalVisible(true);
  };

  const handleOpenRelaunchModal = async (batch: MonthlyBatchVO) => {
    form.resetFields();
    setRelaunchBatchId(batch.id);

    // Load existing metrics & summary for this batch
    const [batchMetrics, batchSummary] = await Promise.all([
      getDefaultTemplatesApi('MONTHLY'),
      getMonthlyBatchSummaryApi(batch.id),
    ]);

    const activeMetrics = batchSummary.metrics && batchSummary.metrics.length > 0
      ? batchSummary.metrics
      : batchMetrics;
    setMetrics(activeMetrics);

    const batchCat = (batch.category as any) || 'DEPT_HEAD';
    setTargetCategory(batchCat);
    if (batch.roleWeights && batch.roleWeights.length > 0) {
      setRoleWeights(batch.roleWeights);
    } else {
      setRoleWeights(batchCat === 'DEPT_DEPUTY' ? DEFAULT_DEPUTY_ROLE_WEIGHTS : DEFAULT_HEAD_ROLE_WEIGHTS);
    }

    const examineeIds = batchSummary.rows.map((r: any) => r.examineeUserId);

    form.setFieldsValue({
      batchName: `${batch.batchName} (重新发起)`,
      appraisalPeriod: dayjs(batch.appraisalPeriod),
      examineeUserIds: examineeIds.length > 0 ? examineeIds : examineeCandidates.map((u) => u.id),
      dateRange: [dayjs(), dayjs().add(7, 'day')],
    });

    setCreateModalVisible(true);
  };

  const handleRoleWeightChange = (category: string, weight: number | null) => {
    setRoleWeights((prev) =>
      prev.map((rw) => (rw.roleCategory === category ? { ...rw, weight: weight || 0 } : rw))
    );
  };

  const totalRoleWeight = roleWeights.reduce((acc, cur) => acc + (Number(cur.weight) || 0), 0);
  const isRoleWeightValid = Math.abs(totalRoleWeight - 100) < 0.0001;

  const handleCreateSubmit = async () => {
    try {
      const values = await form.validateFields();

      if (!isRoleWeightValid) {
        message.error(`考评主体角色权重合计必须等于 100.0000%（当前为 ${totalRoleWeight.toFixed(4)}%）`);
        return;
      }

      const totalMetricWeight = metrics.reduce(
        (acc, cur) => acc + (Number(cur.weight) || 0),
        0
      );
      if (Math.abs(totalMetricWeight - 100) > 0.0001) {
        message.error(`考核指标权重合计必须等于 100.0000%（当前为 ${totalMetricWeight.toFixed(4)}%）`);
        return;
      }

      const periodStr = dayjs.isDayjs(values.appraisalPeriod)
        ? values.appraisalPeriod.format('YYYY-MM')
        : typeof values.appraisalPeriod === 'string'
        ? values.appraisalPeriod.trim()
        : dayjs(values.appraisalPeriod).format('YYYY-MM');

      // 规则：只要考核批次名称不同即可，允许在同月分别建立正职和副职考评
      const exists = batches.some((b) => b.id !== relaunchBatchId && b.batchName === values.batchName.trim());
      if (exists) {
        message.error(`考核批次名称【${values.batchName.trim()}】已存在，请使用不同的批次名称！`);
        return;
      }

      const payload: MonthlyBatchSaveDTO = {
        id: relaunchBatchId || undefined,
        batchName: values.batchName,
        category: targetCategory,
        appraisalPeriod: periodStr,
        startTime: values.dateRange?.[0] ? dayjs(values.dateRange[0]).format('YYYY-MM-DD HH:mm:ss') : undefined,
        endTime: values.dateRange?.[1] ? dayjs(values.dateRange[1]).format('YYYY-MM-DD HH:mm:ss') : undefined,
        roleWeights: roleWeights,
        examineeUserIds: values.examineeUserIds,
        metrics: metrics,
      };

      await saveMonthlyBatchApi(payload);
      message.success(relaunchBatchId ? '月度考核批次已重新发起并进入评分状态' : '月度考核批次创建成功');
      setCreateModalVisible(false);
      setRelaunchBatchId(null);
      loadBatches();
    } catch (e) {
      // Form validation error
    }
  };

  const handlePublish = async (id: number) => {
    Modal.confirm({
      title: '发布考核任务',
      content: '发布后评分领导将可在其工作台中看到打分任务并开始评分，确认发布？',
      onOk: async () => {
        await publishMonthlyBatchApi(id);
        message.success('批次已正式发布');
        loadBatches();
      },
    });
  };

  const handleComplete = async (id: number) => {
    Modal.confirm({
      title: '归档完成考核',
      content: '归档后将锁定该批次所有打分数据，并对外生成最终排名结果，确认归档？',
      onOk: async () => {
        await completeMonthlyBatchApi(id);
        message.success('批次已归档完成');
        loadBatches();
      },
    });
  };

  const columns = [
    {
      title: '批次名称',
      dataIndex: 'batchName',
      key: 'batchName',
      width: 240,
    },
    {
      title: '考核对象',
      dataIndex: 'category',
      key: 'category',
      width: 110,
      render: (cat: string) => (
        <Tag color={cat === 'DEPT_DEPUTY' ? 'geekblue' : 'cyan'}>
          {cat === 'DEPT_DEPUTY' ? '部门副职' : '部门正职'}
        </Tag>
      ),
    },
    {
      title: '考核月份',
      dataIndex: 'appraisalPeriod',
      key: 'appraisalPeriod',
      width: 110,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const map: Record<string, { color: string; text: string }> = {
          DRAFT: { color: 'default', text: '草稿' },
          IN_PROGRESS: { color: 'processing', text: '评分中' },
          COMPLETED: { color: 'success', text: '已归档' },
        };
        return <Tag color={map[status]?.color}>{map[status]?.text || status}</Tag>;
      },
    },
    {
      title: '领导进度',
      key: 'progress',
      width: 160,
      render: (_: any, record: MonthlyBatchVO) => (
        <Button
          type="link"
          size="small"
          style={{ padding: 0 }}
          onClick={() => setProgressModalBatch({ id: record.id, name: record.batchName })}
        >
          {record.submittedLeaderCount} / {record.leaderCount} 人提交
          <Tag
            color={record.submittedLeaderCount === record.leaderCount ? 'success' : 'processing'}
            style={{ marginLeft: 6, cursor: 'pointer' }}
          >
            进度与催促
          </Tag>
        </Button>
      ),
    },
    {
      title: '被考核人数',
      dataIndex: 'examineeCount',
      key: 'examineeCount',
      width: 110,
      render: (cnt: number) => `${cnt} 人`,
    },
    {
      title: '评分截止时间',
      dataIndex: 'endTime',
      key: 'endTime',
      width: 170,
      render: (val: string, record: MonthlyBatchVO) => (
        <span>
          {val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-'}
          {record.isExpired && <Tag color="error" style={{ marginLeft: 6 }}>已截止</Tag>}
        </span>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 320,
      render: (_: any, record: MonthlyBatchVO) => (
        <Space size="middle">
          <Button
            type="link"
            size="small"
            icon={<BellOutlined />}
            onClick={() => setProgressModalBatch({ id: record.id, name: record.batchName })}
          >
            催促打分
          </Button>

          {record.status === 'DRAFT' && (
            <Button
              type="link"
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={() => handlePublish(record.id)}
            >
              发布
            </Button>
          )}

          {record.status === 'IN_PROGRESS' && (
            <Button
              type="link"
              size="small"
              icon={<CheckCircleOutlined />}
              onClick={() => handleComplete(record.id)}
            >
              归档
            </Button>
          )}

          <Button
            type="link"
            size="small"
            icon={<BarChartOutlined />}
            onClick={() => setSummaryModalBatchId(record.id)}
          >
            汇总排名
          </Button>

          <Button
            type="link"
            size="small"
            icon={<RedoOutlined />}
            onClick={() => handleOpenRelaunchModal(record)}
          >
            重新发起
          </Button>

          {activeRole === 'SYS_ADMIN' && (
            <Popconfirm
              title="确定删除该月度考核批次？"
              description="删除后该批次及其打分明细将彻底清除，不可恢复。"
              onConfirm={async () => {
                await deleteMonthlyBatchApi(record.id);
                message.success('批次已成功删除');
                loadBatches();
              }}
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button type="link" danger size="small" icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const filteredBatches = batches
    .filter((b) => statusFilter === 'ALL' || b.status === statusFilter)
    .filter((b) => categoryFilter === 'ALL' || (b.category || 'DEPT_HEAD') === categoryFilter);

  return (
    <div>
      <Card title="月度绩效考核批次管理（支持正职/副职独立考核）" style={{ marginBottom: 16 }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Space size="middle" wrap>
              <Radio.Group
                value={categoryFilter}
                onChange={(e) => setCategoryFilter(e.target.value)}
                buttonStyle="solid"
              >
                <Radio.Button value="ALL">全部考核对象</Radio.Button>
                <Radio.Button value="DEPT_HEAD">部门正职考核</Radio.Button>
                <Radio.Button value="DEPT_DEPUTY">部门副职考核</Radio.Button>
              </Radio.Group>

              <Radio.Group
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <Radio.Button value="ALL">全部状态 ({batches.length})</Radio.Button>
                <Radio.Button value="IN_PROGRESS">
                  评分中 ({batches.filter((b) => b.status === 'IN_PROGRESS').length})
                </Radio.Button>
                <Radio.Button value="COMPLETED">
                  已归档 ({batches.filter((b) => b.status === 'COMPLETED').length})
                </Radio.Button>
                <Radio.Button value="DRAFT">
                  草稿 ({batches.filter((b) => b.status === 'DRAFT').length})
                </Radio.Button>
              </Radio.Group>
              <Button icon={<ReloadOutlined />} onClick={loadBatches}>
                刷新
              </Button>
            </Space>
          </Col>
          <Col>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handleOpenCreateModal}
            >
              创建月度考核批次
            </Button>
          </Col>
        </Row>
      </Card>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={filteredBatches}
        loading={loading}
        pagination={{ pageSize: 10 }}
      />

      {/* Summary Matrix Modal */}
      <BatchSummaryModal
        batchId={summaryModalBatchId}
        open={!!summaryModalBatchId}
        onClose={() => setSummaryModalBatchId(null)}
      />

      {/* Create Batch Modal */}
      <Modal
        title="创建月度绩效考核批次"
        open={createModalVisible}
        width={960}
        onOk={handleCreateSubmit}
        onCancel={() => setCreateModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item label="考核对象类别（区分部门正职与部门副职）">
                <Radio.Group
                  value={targetCategory}
                  onChange={(e) => {
                    const newCat = e.target.value;
                    setTargetCategory(newCat);
                    setRoleWeights(newCat === 'DEPT_DEPUTY' ? DEFAULT_DEPUTY_ROLE_WEIGHTS : DEFAULT_HEAD_ROLE_WEIGHTS);
                    const currentPeriod = form.getFieldValue('appraisalPeriod');
                    const month = (currentPeriod && dayjs(currentPeriod).isValid())
                      ? dayjs(currentPeriod).format('YYYY年M月')
                      : dayjs().format('YYYY年M月');
                    form.setFieldsValue({
                      batchName: `${month}${newCat === 'DEPT_DEPUTY' ? '内设部门副职' : '内设部门正职'}绩效考核`,
                      examineeUserIds: examineeCandidates
                        .filter((u) => u.positionCategory === newCat)
                        .map((u) => u.id),
                    });
                  }}
                  buttonStyle="solid"
                >
                  <Radio.Button value="DEPT_HEAD">部门正职考核（11人）</Radio.Button>
                  <Radio.Button value="DEPT_DEPUTY">部门副职考核（12人）</Radio.Button>
                </Radio.Group>
              </Form.Item>
            </Col>
          </Row>

          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message={targetCategory === 'DEPT_HEAD' ? '内设部门正职月度考核权重规则' : '内设部门副职月度考核权重规则'}
            description={
              targetCategory === 'DEPT_HEAD'
                ? '中心主要领导 25% + 分管领导 35% + 其他中心领导均分 15% + 部门正职互评均分 25% = 100%。系统将自动根据上下级关系动态识别。'
                : '中心分管领导 35% + 其他中心领导均分 20% + 直属本部门正职 30% + 部门副职互评均分 15% = 100%。系统将自动根据上下级关系动态识别。'
            }
          />

          <Row gutter={16}>
            <Col span={14}>
              <Form.Item
                name="batchName"
                label="考核批次名称"
                rules={[{ required: true, message: '请输入批次名称' }]}
              >
                <Input placeholder="如：2026年10月内设部门正职绩效考核" />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item
                name="appraisalPeriod"
                label="考核月份"
                rules={[{ required: true, message: '请选择考核月份' }]}
              >
                <DatePicker
                  picker="month"
                  format="YYYY-MM"
                  style={{ width: '100%' }}
                  placeholder="选择月份（如 2026-10）"
                  onChange={(date) => {
                    if (date) {
                      form.setFieldsValue({
                        batchName: `${date.format('YYYY年M月')}${targetCategory === 'DEPT_DEPUTY' ? '内设部门副职' : '内设部门正职'}绩效考核`,
                      });
                    }
                  }}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={24}>
              <Form.Item name="dateRange" label="评分起止时间周期">
                <DatePicker.RangePicker
                  showTime
                  style={{ width: '100%' }}
                  format="YYYY-MM-DD HH:mm:ss"
                />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left" style={{ margin: '12px 0' }}>
            1. 考评主体角色及权重配置（满分 100% 校验）
          </Divider>

          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center' }}>
              <Text strong>主体角色权重分配：</Text>
              {isRoleWeightValid ? (
                <Tag color="success" style={{ marginLeft: 8 }}>
                  合计：{totalRoleWeight.toFixed(4)}%（已满足100%）
                </Tag>
              ) : (
                <Tag color="error" style={{ marginLeft: 8 }}>
                  合计：{totalRoleWeight.toFixed(4)}%（相差 {(100 - totalRoleWeight).toFixed(4)}%）
                </Tag>
              )}
            </div>
            <Row gutter={[16, 16]}>
              {roleWeights.map((rw) => (
                <Col key={rw.roleCategory} span={12}>
                  <Card size="small" bodyStyle={{ padding: '8px 12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Text strong style={{ fontSize: 13 }}>{rw.roleName}</Text>
                      <InputNumber
                        value={rw.weight}
                        min={0}
                        max={100}
                        precision={4}
                        addonAfter="%"
                        style={{ width: 140 }}
                        onChange={(v) => handleRoleWeightChange(rw.roleCategory, v)}
                      />
                    </div>
                  </Card>
                </Col>
              ))}
            </Row>
          </div>

          <Divider orientation="left" style={{ margin: '12px 0' }}>
            2. 被考核人员选择（{targetCategory === 'DEPT_DEPUTY' ? '部门副职' : '部门正职'}）
          </Divider>

          <Form.Item
            name="examineeUserIds"
            label={`选择参评${targetCategory === 'DEPT_DEPUTY' ? '副职' : '正职'}人员清单`}
            rules={[{ required: true, message: '请至少选择一位被考核人员' }]}
          >
            <Select mode="multiple" placeholder="选择参评人员">
              {examineeCandidates
                .filter((u) => !targetCategory || u.positionCategory === targetCategory)
                .map((u) => (
                  <Select.Option key={u.id} value={u.id}>
                    {u.realName} - {u.department} ({u.title || (u.positionCategory === 'DEPT_DEPUTY' ? '副职' : '正职')})
                  </Select.Option>
                ))}
            </Select>
          </Form.Item>

          <Divider orientation="left" style={{ margin: '12px 0' }}>
            3. 考核指标与权重配置（满分 100 分校验）
          </Divider>

          <MetricConfigTable
            metrics={metrics}
            onChange={setMetrics}
            batchType="MONTHLY"
          />
        </Form>
      </Modal>

      {/* Leader Progress & Urge Modal */}
      <LeaderProgressModal
        batchId={progressModalBatch?.id || null}
        batchName={progressModalBatch?.name}
        open={!!progressModalBatch}
        onClose={() => setProgressModalBatch(null)}
      />
    </div>
  );
};
