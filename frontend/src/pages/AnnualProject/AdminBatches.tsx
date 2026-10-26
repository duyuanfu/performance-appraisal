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
  Tooltip,
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
  CloseCircleOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  listProjectBatchesApi,
  publishProjectBatchApi,
  completeProjectBatchApi,
  deleteProjectBatchApi,
  saveProjectBatchApi,
  listProjectItemsApi,
  getProjectSummaryApi,
  ProjectBatchVO,
  ProjectBatchSaveDTO,
  ProjectItemVO,
  ProjectRoleWeightItem,
} from '../../api/project';
import { useAuth } from '../../context/AuthContext';
import { MetricItem, getDefaultTemplatesApi } from '../../api/metric';
import { MetricConfigTable } from '../../components/MetricConfig/MetricConfigTable';
import { ProjectManagementModal } from './ProjectManagementModal';
import { ProjectBatchSummaryModal } from './BatchSummaryModal';
import { ExpertProgressModal } from './ExpertProgressModal';
import { Popconfirm } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';

const { Text } = Typography;

const DEFAULT_MAIN_ROLE_WEIGHTS: ProjectRoleWeightItem[] = [
  { roleCategory: 'CENTER_LEADER_TOTAL', roleName: '中心领导综合评分小计', weight: 50, sortOrder: 1 },
  { roleCategory: 'DEPT_HEAD', roleName: '部门正职评分', weight: 40, sortOrder: 2 },
  { roleCategory: 'EXPERT', roleName: '代表评分 (评审专家)', weight: 10, sortOrder: 3 },
];

const DEFAULT_LEADER_SUB_WEIGHTS: ProjectRoleWeightItem[] = [
  { roleCategory: 'PRIMARY_LEADER', roleName: '中心主要领导 (陈剑主任)', weight: 20, sortOrder: 1 },
  { roleCategory: 'SUPERVISOR_LEADER', roleName: '中心分管领导', weight: 30, sortOrder: 2 },
  { roleCategory: 'OTHER_LEADER', roleName: '其他中心领导 (均分)', weight: 50, sortOrder: 3 },
];

interface Props {
  batchType?: 'TARGET_PROJECT' | 'SPECIAL_INNOVATION';
}

export const AnnualProjectAdminBatchesPage: React.FC<Props> = ({ batchType = 'TARGET_PROJECT' }) => {
  const { activeRole } = useAuth();
  const isSpecialInnovation = batchType === 'SPECIAL_INNOVATION';
  const pageTitle = isSpecialInnovation ? '专项与创新任务评审管理' : '目标绩效任务评审管理';

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
  const [mainRoleWeights, setMainRoleWeights] = useState<ProjectRoleWeightItem[]>(DEFAULT_MAIN_ROLE_WEIGHTS);
  const [leaderSubWeights, setLeaderSubWeights] = useState<ProjectRoleWeightItem[]>(DEFAULT_LEADER_SUB_WEIGHTS);
  const [metrics, setMetrics] = useState<MetricItem[]>([]);
  const [form] = Form.useForm();

  // Publish overview modal state
  const [publishOverviewBatch, setPublishOverviewBatch] = useState<ProjectBatchVO | null>(null);
  const [publishOverviewProjects, setPublishOverviewProjects] = useState<ProjectItemVO[]>([]);
  const [publishLoading, setPublishLoading] = useState(false);

  const loadBatches = async () => {
    setLoading(true);
    try {
      const data = await listProjectBatchesApi({ batchType });
      setBatches(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBatches();
  }, [batchType]);

  const handleOpenCreateModal = async () => {
    form.resetFields();
    setRelaunchBatchId(null);
    const templateType = isSpecialInnovation ? 'SPECIAL_INNOVATION' : 'TARGET_PROJECT';
    const defaultMetrics = await getDefaultTemplatesApi(templateType);
    setMetrics(defaultMetrics);

    setMainRoleWeights(DEFAULT_MAIN_ROLE_WEIGHTS);
    setLeaderSubWeights(DEFAULT_LEADER_SUB_WEIGHTS);

    form.setFieldsValue({
      appraisalPeriod: dayjs(),
      batchName: `${dayjs().year()}年度原料中心${isSpecialInnovation ? '专项与创新任务' : '目标绩效任务'}评审`,
      dateRange: [dayjs(), dayjs().add(14, 'day')],
    });

    setCreateModalVisible(true);
  };

  const handleOpenRelaunchModal = async (batch: ProjectBatchVO) => {
    form.resetFields();
    setRelaunchBatchId(batch.id);

    const [defaultMetrics, summary] = await Promise.all([
      getDefaultTemplatesApi(isSpecialInnovation ? 'SPECIAL_INNOVATION' : 'TARGET_PROJECT'),
      getProjectSummaryApi(batch.id),
    ]);

    const activeMetrics = summary.metrics && summary.metrics.length > 0
      ? summary.metrics
      : defaultMetrics;
    setMetrics(activeMetrics);

    if (summary.roleWeights && summary.roleWeights.length > 0) {
      const main = summary.roleWeights.filter((rw) =>
        ['CENTER_LEADER_TOTAL', 'DEPT_HEAD', 'EXPERT'].includes(rw.roleCategory)
      );
      const sub = summary.roleWeights.filter((rw) =>
        ['PRIMARY_LEADER', 'SUPERVISOR_LEADER', 'OTHER_LEADER'].includes(rw.roleCategory)
      );
      if (main.length === 3) setMainRoleWeights(main);
      if (sub.length === 3) setLeaderSubWeights(sub);
    } else {
      setMainRoleWeights(DEFAULT_MAIN_ROLE_WEIGHTS);
      setLeaderSubWeights(DEFAULT_LEADER_SUB_WEIGHTS);
    }

    form.setFieldsValue({
      batchName: `${batch.batchName} (重新发起)`,
      appraisalPeriod: dayjs(batch.appraisalPeriod),
      dateRange: [dayjs(), dayjs().add(14, 'day')],
    });

    setCreateModalVisible(true);
  };

  const handleMainRoleWeightChange = (category: string, weight: number | null) => {
    setMainRoleWeights((prev) =>
      prev.map((rw) => (rw.roleCategory === category ? { ...rw, weight: weight || 0 } : rw))
    );
  };

  const handleLeaderSubWeightChange = (category: string, weight: number | null) => {
    setLeaderSubWeights((prev) =>
      prev.map((rw) => (rw.roleCategory === category ? { ...rw, weight: weight || 0 } : rw))
    );
  };

  const totalMainWeight = mainRoleWeights.reduce((acc, cur) => acc + (Number(cur.weight) || 0), 0);
  const isMainWeightValid = Math.abs(totalMainWeight - 100) < 0.0001;

  const totalLeaderSubWeight = leaderSubWeights.reduce((acc, cur) => acc + (Number(cur.weight) || 0), 0);
  const isLeaderSubWeightValid = Math.abs(totalLeaderSubWeight - 100) < 0.0001;

  const handleCreateSubmit = async () => {
    try {
      const values = await form.validateFields();

      if (!isMainWeightValid) {
        message.error(`三大考评主体权重（中心领导 + 正职评分 + 代表评分）合计必须等于 100.0000%（当前为 ${totalMainWeight.toFixed(4)}%）`);
        return;
      }

      if (!isLeaderSubWeightValid) {
        message.error(`中心领导内部细分权重（主要领导 + 分管领导 + 其他领导）合计必须等于 100.0000%（当前为 ${totalLeaderSubWeight.toFixed(4)}%）`);
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
        batchType: batchType,
        category: values.category,
        appraisalPeriod: periodStr,
        startTime: values.dateRange?.[0]
          ? dayjs(values.dateRange[0]).format('YYYY-MM-DD HH:mm:ss')
          : undefined,
        endTime: values.dateRange?.[1]
          ? dayjs(values.dateRange[1]).format('YYYY-MM-DD HH:mm:ss')
          : undefined,
        roleWeights: [...mainRoleWeights, ...leaderSubWeights],
        metrics: metrics,
      };

      await saveProjectBatchApi(payload);
      message.success(relaunchBatchId ? '项目评审批次已重新发起并进入评审状态' : '评审批次创建成功');
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
      title: '考评人员分类打分进度',
      key: 'progress',
      width: 460,
      render: (_: any, record: ProjectBatchVO) => {
        const total = record.totalEvaluatorCount || 0;
        const submitted = record.submittedEvaluatorCount || 0;
        const isComplete = total > 0 && submitted === total;
        const isDraft = record.status === 'DRAFT';
        const tagStyle = { margin: 0, fontSize: 11, lineHeight: '18px', padding: '0 5px', whiteSpace: 'nowrap' as const };
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
            <span style={{ fontWeight: 600, fontSize: 12 }}>
              总计: {submitted} / {total} 人
            </span>
            <Button
              type="link"
              size="small"
              disabled={isDraft}
              title={isDraft ? '发布后可查看进度' : '查看进度'}
              style={{ padding: 0, height: 'auto', fontSize: 12, flexShrink: 0 }}
              onClick={() => setProgressModalBatch({ id: record.id, name: record.batchName })}
            >
              <Tag
                color={isDraft ? 'default' : isComplete ? 'success' : 'processing'}
                style={{ ...tagStyle, cursor: isDraft ? 'not-allowed' : 'pointer' }}
              >
                进度
              </Tag>
            </Button>
            <Tag color="blue" style={tagStyle}>
              中心领导: {record.submittedCenterLeaderCount ?? 0}/{record.centerLeaderCount ?? 0}
            </Tag>
            <Tag color="purple" style={tagStyle}>
              部门正职: {record.submittedDeptHeadCount ?? 0}/{record.deptHeadCount ?? 0}
            </Tag>
            <Tag color="orange" style={tagStyle}>
              评审专家: {record.submittedExpertCount ?? 0}/{record.expertCount ?? 0}
            </Tag>
          </div>
        );
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
            disabled={record.status === 'DRAFT'}
            title={record.status === 'DRAFT' ? '发布后可查看进度' : '查看进度'}
            onClick={() => setProgressModalBatch({ id: record.id, name: record.batchName })}
          >
            进度
          </Button>

          <Button
            type="link"
            size="small"
            icon={<BarChartOutlined />}
            disabled={record.status === 'DRAFT'}
            title={record.status === 'DRAFT' ? '发布后可查看汇总排名' : '查看汇总排名'}
            onClick={() => setSummaryModalBatchId(record.id)}
          >
            汇总排名
          </Button>

          <Button
            type="link"
            size="small"
            icon={<RedoOutlined />}
            disabled={record.status === 'DRAFT'}
            title={record.status === 'DRAFT' ? '发布后可重新发起' : '重新发起'}
            onClick={() => handleOpenRelaunchModal(record)}
          >
            重新发起
          </Button>

          {activeRole === 'SYS_ADMIN' && (
            <Popconfirm
              title="确定删除该项目评审批次？"
              description="删除后该批次及其所有参评项目、材料和评分数据将彻底清除，不可恢复。"
              onConfirm={async () => {
                await deleteProjectBatchApi(record.id);
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
      width: 170,
      render: (_: any, r: ProjectItemVO) =>
        r.hasMaterial ? (
          <Tooltip title={r.materialPdfName} placement="topLeft">
            <Tag
              color="success"
              icon={<CheckCircleOutlined />}
              style={{
                maxWidth: 140,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                verticalAlign: 'middle',
              }}
            >
              已上传 ({r.materialPdfName})
            </Tag>
          </Tooltip>
        ) : (
          <Tag color="error" icon={<CloseCircleOutlined />}>
            未上传
          </Tag>
        ),
    },
    {
      title: '附件材料 (PDF)',
      key: 'attachment',
      width: 170,
      render: (_: any, r: ProjectItemVO) =>
        r.hasAttachment ? (
          <Tooltip title={r.attachmentPdfName} placement="topLeft">
            <Tag
              color="success"
              icon={<CheckCircleOutlined />}
              style={{
                maxWidth: 140,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                verticalAlign: 'middle',
              }}
            >
              已上传 ({r.attachmentPdfName})
            </Tag>
          </Tooltip>
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
      <Card title={pageTitle} style={{ marginBottom: 16 }}>
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
              创建{isSpecialInnovation ? '专项与创新任务' : '目标绩效任务'}评审批次
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
          scroll={{ x: 750 }}
          style={{ tableLayout: 'fixed' }}
        />
      </Modal>

      {/* Create / Relaunch Batch Modal */}
      <Modal
        title={
          relaunchBatchId
            ? '重新发起年度项目评审批次'
            : isSpecialInnovation
            ? '创建专项与创新任务评审批次'
            : '创建目标绩效任务评审批次'
        }
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
                <Input
                  placeholder={`如：2026年度原料中心${
                    isSpecialInnovation ? '专项与创新任务' : '目标绩效任务'
                  }评审`}
                />
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
                      const suffix = relaunchBatchId ? ' (重新发起)' : '';
                      form.setFieldsValue({
                        batchName: `${date.format('YYYY')}年度原料中心${
                          isSpecialInnovation ? '专项与创新任务' : '目标绩效任务'
                        }评审${suffix}`,
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
            1. 考评主体角色及权重配置
          </Divider>

          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center' }}>
              <Text strong>三大考评主体权重分配（满分 100% 校验）：</Text>
              {isMainWeightValid ? (
                <Tag color="success" style={{ marginLeft: 8 }}>
                  合计：{totalMainWeight.toFixed(4)}%（已满足100%）
                </Tag>
              ) : (
                <Tag color="error" style={{ marginLeft: 8 }}>
                  合计：{totalMainWeight.toFixed(4)}%（相差 {(100 - totalMainWeight).toFixed(4)}%）
                </Tag>
              )}
            </div>
            <Row gutter={[16, 16]}>
              {mainRoleWeights.map((rw) => (
                <Col key={rw.roleCategory} span={8}>
                  <Card size="small" bodyStyle={{ padding: '8px 12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Text strong style={{ fontSize: 13 }}>{rw.roleName}</Text>
                      <InputNumber
                        value={rw.weight}
                        min={0}
                        max={100}
                        precision={4}
                        addonAfter="%"
                        style={{ width: 120 }}
                        onChange={(v) => handleMainRoleWeightChange(rw.roleCategory, v)}
                      />
                    </div>
                  </Card>
                </Col>
              ))}
            </Row>
          </div>

          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center' }}>
              <Text strong>中心领导内部细分权重（占中心领导 50% 小计内部 100% 校验）：</Text>
              {isLeaderSubWeightValid ? (
                <Tag color="success" style={{ marginLeft: 8 }}>
                  内部合计：{totalLeaderSubWeight.toFixed(4)}%（已满足100%）
                </Tag>
              ) : (
                <Tag color="error" style={{ marginLeft: 8 }}>
                  内部合计：{totalLeaderSubWeight.toFixed(4)}%（相差 {(100 - totalLeaderSubWeight).toFixed(4)}%）
                </Tag>
              )}
            </div>
            <Row gutter={[16, 16]}>
              {leaderSubWeights.map((rw) => (
                <Col key={rw.roleCategory} span={8}>
                  <Card size="small" bodyStyle={{ padding: '8px 12px', background: '#fafafa' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Text style={{ fontSize: 13 }}>{rw.roleName}</Text>
                      <InputNumber
                        value={rw.weight}
                        min={0}
                        max={100}
                        precision={4}
                        addonAfter="%"
                        style={{ width: 120 }}
                        onChange={(v) => handleLeaderSubWeightChange(rw.roleCategory, v)}
                      />
                    </div>
                  </Card>
                </Col>
              ))}
            </Row>
          </div>

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
