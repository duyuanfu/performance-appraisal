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
  Alert,
  Radio,
} from 'antd';
import {
  PlusOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  BarChartOutlined,
  ReloadOutlined,
  FolderOpenOutlined,
  BellOutlined,
  RedoOutlined,
  FilePdfOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  listProjectBatchesApi,
  publishProjectBatchApi,
  completeProjectBatchApi,
  saveProjectBatchApi,
  listProjectItemsApi,
  getProjectSummaryApi,
  ProjectBatchVO,
  ProjectBatchSaveDTO,
  ProjectItemVO,
} from '../../api/project';
import { listUsersApi } from '../../api/user';
import { UserVO } from '../../api/auth';
import { MetricItem, getDefaultTemplatesApi } from '../../api/metric';
import { MetricConfigTable } from '../../components/MetricConfig/MetricConfigTable';
import { ProjectManagementModal } from './ProjectManagementModal';
import { ProjectBatchSummaryModal } from './BatchSummaryModal';
import { ExpertProgressModal } from './ExpertProgressModal';

const { Text } = Typography;

export const AnnualProjectAdminBatchesPage: React.FC = () => {
  const [batches, setBatches] = useState<ProjectBatchVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [summaryModalBatchId, setSummaryModalBatchId] = useState<number | null>(null);
  const [progressModalBatch, setProgressModalBatch] = useState<{ id: number; name: string } | null>(
    null
  );
  const [projectManageBatch, setProjectManageBatch] = useState<{ id: number; name: string } | null>(
    null
  );
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'IN_PROGRESS' | 'COMPLETED' | 'DRAFT'>('ALL');

  // Batch create / relaunch modal state
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [relaunchBatchId, setRelaunchBatchId] = useState<number | null>(null);
  const [experts, setExperts] = useState<UserVO[]>([]);
  const [selectedExpertWeights, setSelectedExpertWeights] = useState<
    { expertUserId: number; weight: number }[]
  >([]);
  const [metrics, setMetrics] = useState<MetricItem[]>([]);
  const [form] = Form.useForm();

  // Publish overview modal state
  const [publishOverviewBatch, setPublishOverviewBatch] = useState<ProjectBatchVO | null>(null);
  const [publishOverviewProjects, setPublishOverviewProjects] = useState<ProjectItemVO[]>([]);
  const [publishLoading, setPublishLoading] = useState(false);

  const loadBatches = async () => {
    setLoading(true);
    try {
      const data = await listProjectBatchesApi();
      setBatches(data);
    } finally {
      setLoading(false);
    }
  };

  const loadExperts = async () => {
    const list = await listUsersApi({ roleCode: 'EXPERT' });
    setExperts(list);
  };

  useEffect(() => {
    loadBatches();
    loadExperts();
  }, []);

  const handleOpenCreateModal = async () => {
    form.resetFields();
    setRelaunchBatchId(null);
    // Default metrics for annual project
    const defaultMetrics = await getDefaultTemplatesApi('ANNUAL_PROJECT');
    setMetrics(defaultMetrics);

    // Auto distribute expert weights summing to 100% (matches monthly logic)
    if (experts.length > 0) {
      const count = experts.length;
      const perWeight = Number((100 / count).toFixed(4));
      const weights = experts.map((e, idx) => ({
        expertUserId: e.id,
        weight: idx === count - 1 ? 100 - perWeight * (count - 1) : perWeight,
      }));
      setSelectedExpertWeights(weights);
      form.setFieldsValue({
        expertIds: experts.map((e) => e.id),
      });
    } else {
      setSelectedExpertWeights([]);
    }

    form.setFieldsValue({
      appraisalPeriod: dayjs(),
      batchName: `${dayjs().year()}年度原料中心目标管理绩效评审`,
      dateRange: [dayjs(), dayjs().add(14, 'day')],
    });

    setCreateModalVisible(true);
  };

  const handleOpenRelaunchModal = async (batch: ProjectBatchVO) => {
    form.resetFields();
    setRelaunchBatchId(batch.id);

    const [defaultMetrics, summary] = await Promise.all([
      getDefaultTemplatesApi('ANNUAL_PROJECT'),
      getProjectSummaryApi(batch.id),
    ]);

    const activeMetrics = summary.metrics && summary.metrics.length > 0
      ? summary.metrics
      : defaultMetrics;
    setMetrics(activeMetrics);

    const weights = summary.experts.map((e) => ({
      expertUserId: e.expertUserId,
      weight: e.weight,
    }));
    setSelectedExpertWeights(weights);

    form.setFieldsValue({
      batchName: `${batch.batchName} (重新发起)`,
      appraisalPeriod: dayjs(batch.appraisalPeriod),
      expertIds: weights.map((w) => w.expertUserId),
      dateRange: [dayjs(), dayjs().add(14, 'day')],
    });

    setCreateModalVisible(true);
  };

  const handleExpertSelectChange = (expertIds: number[]) => {
    const existingMap = new Map(selectedExpertWeights.map((w) => [w.expertUserId, w.weight]));
    const count = expertIds.length;
    const defaultWeight = count > 0 ? Number((100 / count).toFixed(4)) : 0;

    const newWeights = expertIds.map((id, idx) => ({
      expertUserId: id,
      weight: existingMap.has(id)
        ? existingMap.get(id)!
        : idx === count - 1
        ? 100 - defaultWeight * (count - 1)
        : defaultWeight,
    }));
    setSelectedExpertWeights(newWeights);
  };

  const handleExpertWeightChange = (expertUserId: number, weight: number | null) => {
    setSelectedExpertWeights((prev) =>
      prev.map((w) => (w.expertUserId === expertUserId ? { ...w, weight: weight || 0 } : w))
    );
  };

  const totalExpertWeight = selectedExpertWeights.reduce(
    (acc, cur) => acc + (Number(cur.weight) || 0),
    0
  );
  const isExpertWeightValid = Math.abs(totalExpertWeight - 100) < 0.0001;

  const handleCreateSubmit = async () => {
    try {
      const values = await form.validateFields();

      if (selectedExpertWeights.length === 0) {
        message.error('请至少配置一位评审专家');
        return;
      }

      if (!isExpertWeightValid) {
        message.error(`评审专家权重合计必须等于 100.0000%（当前为 ${totalExpertWeight.toFixed(4)}%）`);
        return;
      }

      const totalMetricWeight = metrics.reduce(
        (acc, cur) => acc + (Number(cur.weight) || 0),
        0
      );
      if (Math.abs(totalMetricWeight - 100) > 0.0001) {
        message.error(`评审指标权重合计必须等于 100.0000%（当前为 ${totalMetricWeight.toFixed(4)}%）`);
        return;
      }

      const periodStr =
        dayjs.isDayjs(values.appraisalPeriod)
          ? values.appraisalPeriod.format('YYYY')
          : typeof values.appraisalPeriod === 'string'
          ? values.appraisalPeriod.trim()
          : dayjs(values.appraisalPeriod).format('YYYY');

      // 规则：只要评审批次名称不同即可（取消一年一个的限制）
      const exists = batches.some((b) => b.id !== relaunchBatchId && b.batchName === values.batchName.trim());
      if (exists) {
        message.error(`评审批次名称【${values.batchName.trim()}】已存在，请使用不同的批次名称！`);
        return;
      }

      const payload: ProjectBatchSaveDTO = {
        id: relaunchBatchId || undefined,
        batchName: values.batchName,
        appraisalPeriod: periodStr,
        startTime: values.dateRange?.[0]
          ? dayjs(values.dateRange[0]).format('YYYY-MM-DD HH:mm:ss')
          : undefined,
        endTime: values.dateRange?.[1]
          ? dayjs(values.dateRange[1]).format('YYYY-MM-DD HH:mm:ss')
          : undefined,
        experts: selectedExpertWeights,
        metrics: metrics,
      };

      await saveProjectBatchApi(payload);
      message.success(relaunchBatchId ? '项目评审批次已重新发起并进入评审状态' : '年度项目评审批次创建成功');
      setCreateModalVisible(false);
      setRelaunchBatchId(null);
      loadBatches();
    } catch (e) {
      // Form validation error
    }
  };

  const handleOpenPublishOverview = async (batch: ProjectBatchVO) => {
    try {
      const projs = await listProjectItemsApi(batch.id);
      setPublishOverviewBatch(batch);
      setPublishOverviewProjects(projs);
    } catch (e) {
      // Error handled
    }
  };

  const handleConfirmPublish = async () => {
    if (!publishOverviewBatch) return;
    if (publishOverviewProjects.length === 0) {
      message.error('该批次尚未录入参评项目，无法发布。请先录入参评项目！');
      return;
    }
    setPublishLoading(true);
    try {
      await publishProjectBatchApi(publishOverviewBatch.id);
      message.success('评审任务已正式发布！专家可在工作台中在线查阅材料并开展打分');
      setPublishOverviewBatch(null);
      loadBatches();
    } finally {
      setPublishLoading(false);
    }
  };

  const handleComplete = async (id: number) => {
    Modal.confirm({
      title: '归档完成评审',
      content: '归档后将锁定该批次所有专家打分数据，并生成最终项目排名汇总，确认归档？',
      onOk: async () => {
        await completeProjectBatchApi(id);
        message.success('项目评审批次已归档');
        loadBatches();
      },
    });
  };

  const columns = [
    {
      title: '批次名称',
      dataIndex: 'batchName',
      key: 'batchName',
      width: 260,
    },
    {
      title: '评审年度',
      dataIndex: 'appraisalPeriod',
      key: 'appraisalPeriod',
      width: 100,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const map: Record<string, { color: string; text: string }> = {
          DRAFT: { color: 'default', text: '草稿' },
          IN_PROGRESS: { color: 'processing', text: '评审中' },
          COMPLETED: { color: 'success', text: '已归档' },
        };
        return <Tag color={map[status]?.color}>{map[status]?.text || status}</Tag>;
      },
    },
    {
      title: '评审截止时间',
      dataIndex: 'endTime',
      key: 'endTime',
      width: 170,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 380,
      render: (_: any, record: ProjectBatchVO) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<FolderOpenOutlined />}
            onClick={() => setProjectManageBatch({ id: record.id, name: record.batchName })}
          >
            参评项目管理
          </Button>

          {record.status === 'DRAFT' && (
            <Button
              type="link"
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={() => handleOpenPublishOverview(record)}
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
            icon={<BellOutlined />}
            onClick={() => setProgressModalBatch({ id: record.id, name: record.batchName })}
          >
            专家进度
          </Button>

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
        </Space>
      ),
    },
  ];

  const publishProjectColumns = [
    {
      title: '项目编号',
      dataIndex: 'projectCode',
      key: 'projectCode',
      width: 100,
    },
    {
      title: '项目名称',
      dataIndex: 'projectName',
      key: 'projectName',
    },
    {
      title: '项目负责人',
      dataIndex: 'ownerName',
      key: 'ownerName',
      width: 120,
    },
    {
      title: '申报材料 (PDF)',
      key: 'material',
      width: 150,
      render: (_: any, r: ProjectItemVO) =>
        r.hasMaterial ? (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            已上传 ({r.materialPdfName})
          </Tag>
        ) : (
          <Tag color="error" icon={<CloseCircleOutlined />}>
            未上传
          </Tag>
        ),
    },
    {
      title: '附件材料 (PDF)',
      key: 'attachment',
      width: 150,
      render: (_: any, r: ProjectItemVO) =>
        r.hasAttachment ? (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            已上传 ({r.attachmentPdfName})
          </Tag>
        ) : (
          <Tag color="warning" icon={<CloseCircleOutlined />}>
            未上传
          </Tag>
        ),
    },
  ];

  const filteredBatches =
    statusFilter === 'ALL' ? batches : batches.filter((b) => b.status === statusFilter);

  return (
    <div>
      <Card title="年度目标管理绩效评审批次管理" style={{ marginBottom: 16 }}>
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
                  评审中 ({batches.filter((b) => b.status === 'IN_PROGRESS').length})
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
              创建年度项目评审批次
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

      {/* Project Candidates Management Modal */}
      <ProjectManagementModal
        batchId={projectManageBatch?.id || null}
        batchName={projectManageBatch?.name}
        open={!!projectManageBatch}
        onClose={() => setProjectManageBatch(null)}
      />

      {/* Summary Matrix Modal (Admin exclusive) */}
      <ProjectBatchSummaryModal
        batchId={summaryModalBatchId}
        open={!!summaryModalBatchId}
        onClose={() => setSummaryModalBatchId(null)}
      />

      {/* Expert Progress & Urge Modal */}
      <ExpertProgressModal
        batchId={progressModalBatch?.id || null}
        batchName={progressModalBatch?.name}
        open={!!progressModalBatch}
        onClose={() => setProgressModalBatch(null)}
      />

      {/* Publishing Overview Modal */}
      <Modal
        title={`发布确认与参评项目总览 - ${publishOverviewBatch?.batchName}`}
        open={!!publishOverviewBatch}
        width={900}
        onCancel={() => setPublishOverviewBatch(null)}
        footer={[
          <Button key="cancel" onClick={() => setPublishOverviewBatch(null)}>
            取消
          </Button>,
          <Button
            key="submit"
            type="primary"
            icon={<PlayCircleOutlined />}
            loading={publishLoading}
            disabled={publishOverviewProjects.length === 0}
            onClick={handleConfirmPublish}
          >
            确认发布评审任务
          </Button>,
        ]}
        destroyOnClose
      >
        <div style={{ marginBottom: 16 }}>
          {publishOverviewProjects.length === 0 ? (
            <Alert
              type="error"
              showIcon
              message="该批次尚未录入参评项目，无法发布！"
              description="系统禁止发布空项目的评审任务。请先关闭此弹窗，点击表格中的【参评项目管理】录入项目并上传 PDF 材料后再进行发布。"
            />
          ) : (
            <Alert
              type="info"
              showIcon
              message={`当前共 ${publishOverviewProjects.length} 个参评项目已就绪`}
              description="请仔细核对参评项目清单及申报材料完整度。发布后评审专家即可在线查阅材料并开展评分。"
            />
          )}
        </div>

        <Table
          rowKey="id"
          columns={publishProjectColumns}
          dataSource={publishOverviewProjects}
          pagination={false}
          size="small"
          bordered
        />
      </Modal>

      {/* Create / Relaunch Batch Modal */}
      <Modal
        title={relaunchBatchId ? '重新发起年度项目评审批次' : '创建年度目标管理项目评审批次'}
        open={createModalVisible}
        width={960}
        onOk={handleCreateSubmit}
        onCancel={() => setCreateModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={16}>
              <Form.Item
                name="batchName"
                label="评审批次名称"
                rules={[{ required: true, message: '请输入批次名称' }]}
              >
                <Input placeholder="如：2026年度原料中心目标管理绩效评审" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="appraisalPeriod"
                label="评审年度"
                rules={[{ required: true, message: '请选择评审年度' }]}
              >
                <DatePicker
                  picker="year"
                  format="YYYY"
                  style={{ width: '100%' }}
                  placeholder="选择年度（如 2026）"
                  onChange={(date) => {
                    if (date) {
                      form.setFieldsValue({
                        batchName: `${date.format('YYYY')}年度原料中心目标管理绩效评审`,
                      });
                    }
                  }}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={24}>
              <Form.Item name="dateRange" label="评审起止时间周期">
                <DatePicker.RangePicker
                  showTime
                  style={{ width: '100%' }}
                  format="YYYY-MM-DD HH:mm:ss"
                />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left" style={{ margin: '12px 0' }}>
            1. 评审专家及权重配置（保持与月度绩效一致，满分100%校验）
          </Divider>

          <Form.Item
            name="expertIds"
            label="选择参加评审的专家"
            rules={[{ required: true, message: '请至少选择一位评审专家' }]}
          >
            <Select
              mode="multiple"
              placeholder="选择评审专家"
              onChange={handleExpertSelectChange}
            >
              {experts.map((e) => (
                <Select.Option key={e.id} value={e.id}>
                  {e.realName} ({e.department} - {e.position || '专家'})
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          {selectedExpertWeights.length > 0 && (
            <div style={{ marginBottom: 16 }}>
              <div style={{ marginBottom: 8 }}>
                <Text strong>专家权重分配：</Text>
                {isExpertWeightValid ? (
                  <Tag color="success" style={{ marginLeft: 8 }}>
                    合计：{totalExpertWeight.toFixed(4)}%（已满足100%）
                  </Tag>
                ) : (
                  <Tag color="error" style={{ marginLeft: 8 }}>
                    合计：{totalExpertWeight.toFixed(4)}%（相差 {(100 - totalExpertWeight).toFixed(4)}%）
                  </Tag>
                )}
              </div>
              <Row gutter={[12, 12]}>
                {selectedExpertWeights.map((w) => {
                  const expertUser = experts.find((e) => e.id === w.expertUserId);
                  return (
                    <Col key={w.expertUserId} span={8}>
                      <Space>
                        <span style={{ width: 80 }}>{expertUser?.realName}：</span>
                        <InputNumber
                          value={w.weight}
                          min={0.0001}
                          max={100}
                          precision={4}
                          addonAfter="%"
                          onChange={(v) => handleExpertWeightChange(w.expertUserId, v)}
                        />
                      </Space>
                    </Col>
                  );
                })}
              </Row>
            </div>
          )}

          <Divider orientation="left" style={{ margin: '12px 0' }}>
            2. 评审指标与权重配置（满分 100 分校验）
          </Divider>

          <MetricConfigTable
            metrics={metrics}
            onChange={setMetrics}
            batchType="ANNUAL_PROJECT"
          />
        </Form>
      </Modal>
    </div>
  );
};
