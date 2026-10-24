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

export const MonthlyAdminBatchesPage: React.FC = () => {
  const { activeRole } = useAuth();
  const [batches, setBatches] = useState<MonthlyBatchVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [summaryModalBatchId, setSummaryModalBatchId] = useState<number | null>(null);
  const [progressModalBatch, setProgressModalBatch] = useState<{ id: number; name: string } | null>(
    null
  );
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'IN_PROGRESS' | 'COMPLETED' | 'DRAFT'>('ALL');

  // Batch create modal state
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [relaunchBatchId, setRelaunchBatchId] = useState<number | null>(null);
  const [leaders, setLeaders] = useState<UserVO[]>([]);
  const [examineeCandidates, setExamineeCandidates] = useState<UserVO[]>([]);
  const [selectedLeaderWeights, setSelectedLeaderWeights] = useState<
    { leaderUserId: number; weight: number }[]
  >([]);
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
    const [allLeaders, allExaminees] = await Promise.all([
      listUsersApi({ roleCode: 'LEADER' }),
      getCandidateExamineesApi(),
    ]);
    setLeaders(allLeaders);
    setExamineeCandidates(allExaminees);
  };

  useEffect(() => {
    loadBatches();
    loadUsers();
  }, []);

  const handleOpenCreateModal = async () => {
    form.resetFields();
    setRelaunchBatchId(null);
    // Default default metrics for monthly
    const defaultMetrics = await getDefaultTemplatesApi('MONTHLY');
    setMetrics(defaultMetrics);

    // Default select all leaders with equal weights totaling 100
    if (leaders.length > 0) {
      const perWeight = Number((100 / leaders.length).toFixed(4));
      const weights = leaders.map((l, idx) => ({
        leaderUserId: l.id,
        weight: idx === leaders.length - 1 ? 100 - perWeight * (leaders.length - 1) : perWeight,
      }));
      setSelectedLeaderWeights(weights);
      form.setFieldsValue({
        leaderIds: leaders.map((l) => l.id),
      });
    } else {
      setSelectedLeaderWeights([]);
    }

    // Default select all examinee candidates
    form.setFieldsValue({
      appraisalPeriod: dayjs(),
      batchName: `${dayjs().format('YYYY年M月')}部门负责人月度绩效考核`,
      examineeUserIds: examineeCandidates.map((u) => u.id),
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

    const weights = batchSummary.leaders.map((l: any) => ({
      leaderUserId: l.leaderUserId,
      weight: l.weight,
    }));
    setSelectedLeaderWeights(weights);

    const examineeIds = batchSummary.rows.map((r: any) => r.examineeUserId);

    form.setFieldsValue({
      batchName: `${batch.batchName} (重新发起)`,
      appraisalPeriod: dayjs(batch.appraisalPeriod),
      leaderIds: weights.map((w: any) => w.leaderUserId),
      examineeUserIds: examineeIds.length > 0 ? examineeIds : examineeCandidates.map((u) => u.id),
      dateRange: [dayjs(), dayjs().add(7, 'day')],
    });

    setCreateModalVisible(true);
  };

  const handleLeaderSelectChange = (leaderIds: number[]) => {
    const existingMap = new Map(selectedLeaderWeights.map((w) => [w.leaderUserId, w.weight]));
    const count = leaderIds.length;
    const defaultWeight = count > 0 ? Number((100 / count).toFixed(4)) : 0;

    const newWeights = leaderIds.map((id, idx) => ({
      leaderUserId: id,
      weight: existingMap.has(id)
        ? existingMap.get(id)!
        : idx === count - 1
        ? 100 - defaultWeight * (count - 1)
        : defaultWeight,
    }));
    setSelectedLeaderWeights(newWeights);
  };

  const handleLeaderWeightChange = (leaderUserId: number, weight: number | null) => {
    setSelectedLeaderWeights((prev) =>
      prev.map((w) => (w.leaderUserId === leaderUserId ? { ...w, weight: weight || 0 } : w))
    );
  };

  const totalLeaderWeight = selectedLeaderWeights.reduce(
    (acc, cur) => acc + (Number(cur.weight) || 0),
    0
  );
  const isLeaderWeightValid = Math.abs(totalLeaderWeight - 100) < 0.0001;

  const handleCreateSubmit = async () => {
    try {
      const values = await form.validateFields();

      if (!isLeaderWeightValid) {
        message.error(`评分领导权重合计必须等于 100.0000%（当前为 ${totalLeaderWeight.toFixed(4)}%）`);
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

      // 规则：一个月只能建立一个月度绩效考核流程（忽略当前重新发起的批次）
      const exists = batches.some((b) => b.id !== relaunchBatchId && b.appraisalPeriod === periodStr);
      if (exists) {
        message.error(`该月份【${periodStr}】已存在月度绩效考核批次，系统规定一个月只能建立一个考核流程！`);
        return;
      }

      const payload: MonthlyBatchSaveDTO = {
        id: relaunchBatchId || undefined,
        batchName: values.batchName,
        appraisalPeriod: periodStr,
        startTime: values.dateRange?.[0] ? dayjs(values.dateRange[0]).format('YYYY-MM-DD HH:mm:ss') : undefined,
        endTime: values.dateRange?.[1] ? dayjs(values.dateRange[1]).format('YYYY-MM-DD HH:mm:ss') : undefined,
        leaders: selectedLeaderWeights,
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

  const filteredBatches =
    statusFilter === 'ALL' ? batches : batches.filter((b) => b.status === statusFilter);

  return (
    <div>
      <Card title="月度绩效考核批次管理" style={{ marginBottom: 16 }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Space size="middle">
              <Radio.Group
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                buttonStyle="solid"
              >
                <Radio.Button value="ALL">全部 ({batches.length})</Radio.Button>
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
            <Col span={14}>
              <Form.Item
                name="batchName"
                label="考核批次名称"
                rules={[{ required: true, message: '请输入批次名称' }]}
              >
                <Input placeholder="如：2026年10月部门负责人月度绩效考核" />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item
                name="appraisalPeriod"
                label="考核月份（一个月只能建一个流程）"
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
                        batchName: `${date.format('YYYY年M月')}部门负责人月度绩效考核`,
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
            1. 评分领导及权重配置
          </Divider>

          <Form.Item
            name="leaderIds"
            label="选择参加打分的中心领导"
            rules={[{ required: true, message: '请至少选择一位中心领导' }]}
          >
            <Select
              mode="multiple"
              placeholder="选择中心领导"
              onChange={handleLeaderSelectChange}
            >
              {leaders.map((l) => (
                <Select.Option key={l.id} value={l.id}>
                  {l.realName} ({l.department})
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          {selectedLeaderWeights.length > 0 && (
            <div style={{ marginBottom: 16 }}>
              <div style={{ marginBottom: 8 }}>
                <Text strong>领导权重分配：</Text>
                {isLeaderWeightValid ? (
                  <Tag color="success" style={{ marginLeft: 8 }}>
                    合计：{totalLeaderWeight.toFixed(4)}%（已满足100%）
                  </Tag>
                ) : (
                  <Tag color="error" style={{ marginLeft: 8 }}>
                    合计：{totalLeaderWeight.toFixed(4)}%（相差 {(100 - totalLeaderWeight).toFixed(4)}%）
                  </Tag>
                )}
              </div>
              <Row gutter={[12, 12]}>
                {selectedLeaderWeights.map((w) => {
                  const leaderUser = leaders.find((l) => l.id === w.leaderUserId);
                  return (
                    <Col key={w.leaderUserId} span={8}>
                      <Space>
                        <span style={{ width: 90 }}>{leaderUser?.realName}：</span>
                        <InputNumber
                          value={w.weight}
                          min={0.0001}
                          max={100}
                          precision={4}
                          addonAfter="%"
                          onChange={(v) => handleLeaderWeightChange(w.leaderUserId, v)}
                        />
                      </Space>
                    </Col>
                  );
                })}
              </Row>
            </div>
          )}

          <Divider orientation="left" style={{ margin: '12px 0' }}>
            2. 被考核人员选择
          </Divider>

          <Form.Item
            name="examineeUserIds"
            label="选择被考核部门负责人及人员"
            rules={[{ required: true, message: '请至少选择一位被考核人员' }]}
          >
            <Select mode="multiple" placeholder="选择参评被考核人员">
              {examineeCandidates.map((u) => (
                <Select.Option key={u.id} value={u.id}>
                  {u.realName} - {u.department} ({u.position || '员工'})
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
