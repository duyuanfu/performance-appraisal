import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Select,
  InputNumber,
  Typography,
  Alert,
  message,
  Modal,
  Row,
  Col,
  Radio,
  Empty,
} from 'antd';
import {
  SaveOutlined,
  CheckOutlined,
  UndoOutlined,
  ReloadOutlined,
  FilePdfOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  listProjectBatchesApi,
  getExpertEvaluationDataApi,
  saveExpertScoresApi,
  withdrawExpertScoresApi,
  ProjectBatchVO,
  ExpertEvaluationVO,
} from '../../api/project';
import { useAuth } from '../../context/AuthContext';

const { Text, Title } = Typography;

export const ExpertEvaluationPage: React.FC = () => {
  const { user } = useAuth();
  const [batches, setBatches] = useState<ProjectBatchVO[]>([]);
  const [selectedBatchId, setSelectedBatchId] = useState<number | null>(null);
  const [evaluationData, setEvaluationData] = useState<ExpertEvaluationVO | null>(null);
  const [scores, setScores] = useState<Record<number, Record<number, number>>>({}); // projectId -> metricId -> score
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [pdfType, setPdfType] = useState<'material' | 'attachment'>('material');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const loadBatches = async () => {
    try {
      // 查询分配给该专家的所有批次（包含评审中和已归档，归档后仍可查阅历史评分）
      const list = await listProjectBatchesApi();
      setBatches(list);
      if (list.length > 0) {
        if (!selectedBatchId || !list.some((b) => b.id === selectedBatchId)) {
          setSelectedBatchId(list[0].id);
        }
      } else {
        setSelectedBatchId(null);
        setEvaluationData(null);
      }
    } catch (e) {
      // Handled
    }
  };

  const loadEvaluationData = async (batchId: number) => {
    setLoading(true);
    try {
      const data = await getExpertEvaluationDataApi(batchId);
      setEvaluationData(data);

      const map: Record<number, Record<number, number>> = {};
      data.projects.forEach((p) => {
        map[p.projectId] = { ...(p.metricScores || {}) };
      });
      setScores(map);

      if (data.projects.length > 0 && !selectedProjectId) {
        setSelectedProjectId(data.projects[0].projectId);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBatches();
  }, []);

  useEffect(() => {
    if (selectedBatchId) {
      loadEvaluationData(selectedBatchId);
    }
  }, [selectedBatchId]);

  const handleScoreChange = (projectId: number, metricId: number, val: number | null) => {
    setScores((prev) => ({
      ...prev,
      [projectId]: {
        ...(prev[projectId] || {}),
        [metricId]: val || 0,
      },
    }));
  };

  const calculateRowTotal = (projectId: number): number => {
    if (!evaluationData) return 0;
    const rowScores = scores[projectId] || {};
    let total = 0;
    evaluationData.metrics.forEach((m) => {
      const s = rowScores[m.id!] || 0;
      total += s * (m.weight / 100);
    });
    return Number(total.toFixed(4));
  };

  const handleSave = async (isSubmit: boolean) => {
    if (!selectedBatchId || !evaluationData) return;

    if (isSubmit) {
      for (const p of evaluationData.projects) {
        const rowScores = scores[p.projectId] || {};
        for (const m of evaluationData.metrics) {
          const s = rowScores[m.id!];
          if (s === undefined || s === null) {
            message.error(`【${p.projectName}】的指标【${m.metricName}】尚未打分，请补全后提交`);
            return;
          }
          if (s < 0 || s > m.maxScore) {
            message.error(`【${p.projectName}】的指标【${m.metricName}】分值必须在 0 ~ ${m.maxScore} 之间`);
            return;
          }
        }
      }
    }

    setSaving(true);
    try {
      const items = evaluationData.projects.map((p) => ({
        projectId: p.projectId,
        metricScores: scores[p.projectId] || {},
      }));

      await saveExpertScoresApi({
        batchId: selectedBatchId,
        items,
        isSubmit,
      });

      message.success(isSubmit ? '评审打分已正式提交！' : '打分数据已成功暂存');
      loadEvaluationData(selectedBatchId);
      loadBatches();
    } finally {
      setSaving(false);
    }
  };

  const handleWithdraw = () => {
    if (!selectedBatchId) return;
    Modal.confirm({
      title: '撤回评审打分',
      content: '撤回后将重新转为草稿编辑状态，您可重新修改项目评分并在截止前再次提交。确认撤回？',
      onOk: async () => {
        await withdrawExpertScoresApi(selectedBatchId);
        message.success('已撤回至草稿状态，现在可以修改打分');
        loadEvaluationData(selectedBatchId);
        loadBatches();
      },
    });
  };

  const isSubmitted = evaluationData?.mySubmissionStatus === 'SUBMITTED';
  const isExpired = !!evaluationData?.isExpired;
  const isArchived = evaluationData?.batchStatus === 'COMPLETED';
  const isReadOnly = isSubmitted || isExpired || isArchived;

  const currentProject = evaluationData?.projects.find((p) => p.projectId === selectedProjectId);

  const baseRoleName = (() => {
    switch (user?.positionCategory) {
      case 'PRIMARY_LEADER':
        return '中心主要领导';
      case 'CENTER_LEADER':
        return '其他中心领导';
      case 'DEPT_HEAD':
        return '部门正职';
      case 'EXPERT':
        return '评审专家';
      default:
        return '考评人员';
    }
  })();

  const totalProjectCount = evaluationData?.projects.length || 0;
  const supervisorProjectCount =
    evaluationData?.projects.filter((p) => p.isSupervisor).length || 0;
  const roleSummary = (() => {
    if (supervisorProjectCount === 0) return baseRoleName;
    if (supervisorProjectCount === totalProjectCount) {
      return `分管领导（全部 ${totalProjectCount} 个项目）`;
    }
    return `分管领导（${supervisorProjectCount} 个项目） / ${baseRoleName}（其余 ${
      totalProjectCount - supervisorProjectCount
    } 个项目）`;
  })();

  const dynamicColumns = [
    {
      title: '项目序号',
      dataIndex: 'projectCode',
      key: 'projectCode',
      width: 90,
      render: (val: string, record: any) => (
        <span
          style={{
            cursor: 'pointer',
            color: record.projectId === selectedProjectId ? '#1890ff' : 'inherit',
            fontWeight: record.projectId === selectedProjectId ? 'bold' : 'normal',
          }}
          onClick={() => setSelectedProjectId(record.projectId)}
        >
          {val}
        </span>
      ),
    },
    {
      title: '项目名称',
      dataIndex: 'projectName',
      key: 'projectName',
      width: 180,
      render: (val: string, record: any) => (
        <Button
          type="link"
          style={{
            padding: 0,
            fontWeight: record.projectId === selectedProjectId ? 'bold' : 'normal',
          }}
          onClick={() => setSelectedProjectId(record.projectId)}
        >
          {val}
        </Button>
      ),
    },
    {
      title: '分管领导',
      dataIndex: 'supervisorName',
      key: 'supervisorName',
      width: 110,
      render: (name: string) => name || '-',
    },
    {
      title: '我在本项目的角色',
      key: 'myRole',
      width: 150,
      render: (_: any, record: any) =>
        record.isSupervisor ? (
          <Tag color="gold">分管领导</Tag>
        ) : (
          <Tag color="blue">{record.myRoleName || baseRoleName}</Tag>
        ),
    },
    ...(evaluationData?.metrics || []).map((m) => ({
      title: `${m.metricName}（${Number(m.maxScore).toFixed(0)}分）`,
      key: `metric_${m.id}`,
      width: 140,
      render: (_: any, record: any) => {
        const val = scores[record.projectId]?.[m.id!];
        return (
          <InputNumber
            value={val}
            disabled={isReadOnly}
            min={0}
            max={m.maxScore}
            precision={4}
            step={0.5}
            style={{ width: '100%' }}
            placeholder={`0 ~ ${m.maxScore}`}
            onChange={(v) => handleScoreChange(record.projectId, m.id!, v)}
          />
        );
      },
    })),
    {
      title: '总分（100）',
      key: 'totalScore',
      width: 130,
      render: (_: any, record: any) => {
        const total = calculateRowTotal(record.projectId);
        return (
          <Text strong style={{ color: '#1890ff', fontSize: 15 }}>
            {total.toFixed(4)}
          </Text>
        );
      },
    },
  ];

  const token = localStorage.getItem('token') || '';
  const currentPdfUrl = currentProject
    ? pdfType === 'material' && currentProject.hasMaterial
      ? `/api/projects/items/${currentProject.projectId}/pdf/material?token=${encodeURIComponent(token)}`
      : pdfType === 'attachment' && currentProject.hasAttachment
      ? `/api/projects/items/${currentProject.projectId}/pdf/attachment?token=${encodeURIComponent(token)}`
      : null
    : null;

  if (!loading && batches.length === 0) {
    return (
      <Card style={{ padding: 40, textAlign: 'center' }}>
        <Empty description="当前暂无分配给您的年度项目在线评审任务" />
      </Card>
    );
  }

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Row justify="space-between" align="middle" gutter={[16, 16]}>
          <Col>
            <Space size="middle">
              <Text strong style={{ fontSize: 16 }}>选择项目评审批次：</Text>
              <Select
                value={selectedBatchId}
                onChange={setSelectedBatchId}
                style={{ width: 440 }}
                placeholder="请选择年度项目评审批次"
              >
                {batches.map((b) => (
                  <Select.Option key={b.id} value={b.id}>
                    <span>
                      {b.batchName} ({b.appraisalPeriod})
                    </span>
                    <Tag
                      color={
                        b.status === 'COMPLETED'
                          ? 'success'
                          : b.status === 'IN_PROGRESS'
                          ? 'processing'
                          : 'default'
                      }
                      style={{ marginLeft: 8 }}
                    >
                      {b.status === 'COMPLETED'
                        ? '已归档 (查阅)'
                        : b.status === 'IN_PROGRESS'
                        ? '评审中'
                        : '草稿'}
                    </Tag>
                  </Select.Option>
                ))}
              </Select>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => selectedBatchId && loadEvaluationData(selectedBatchId)}
              >
                刷新
              </Button>
            </Space>
          </Col>

          <Col>
            <Space>
              {isSubmitted && !isExpired && !isArchived && (
                <Button icon={<UndoOutlined />} onClick={handleWithdraw}>
                  撤回修改
                </Button>
              )}

              {!isReadOnly && (
                <>
                  <Button icon={<SaveOutlined />} loading={saving} onClick={() => handleSave(false)}>
                    暂存评分
                  </Button>
                  <Button
                    type="primary"
                    icon={<CheckOutlined />}
                    loading={saving}
                    onClick={() => handleSave(true)}
                  >
                    正式提交评审
                  </Button>
                </>
              )}
            </Space>
          </Col>
        </Row>

        {evaluationData && (
          <>
            <div style={{ marginTop: 14, display: 'flex', gap: 24, alignItems: 'center' }}>
              <Space>
                <Text type="secondary">评审截止时间：</Text>
                <Text strong>
                  {evaluationData.endTime
                    ? dayjs(evaluationData.endTime).format('YYYY-MM-DD HH:mm:ss')
                    : '无限制'}
                </Text>
                {isExpired && <Tag color="error">已截止锁定</Tag>}
              </Space>

              <Space>
                <Text type="secondary">我的评审状态：</Text>
                {isSubmitted ? (
                  <Tag color="success" icon={<CheckCircleOutlined />}>已正式提交</Tag>
                ) : (
                  <Tag color="warning" icon={<ClockCircleOutlined />}>草稿 (未提交)</Tag>
                )}
              </Space>
            </div>

            <Alert
              type="info"
              showIcon
              style={{ marginTop: 12 }}
              message={
                <span>
                  当前考评主体：<strong style={{ color: '#1890ff' }}>{user?.realName}</strong>（
                  {roleSummary} - {user?.department || '原料中心'}）
                </span>
              }
              description="系统按“项目”自动识别您的考评主体角色：若某项目选择您为分管领导，您的评分将计入该项目的“分管领导评分”；其余项目则按其他中心领导/正职/专家角色计分。"
            />
          </>
        )}
      </Card>

      {isArchived && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="该项目评审批次已正式归档完成，评审数据已封存入库。您可完整查阅历史评分明细，不可修改或撤回。"
        />
      )}

      {isSubmitted && !isExpired && !isArchived && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 16 }}
          message="您已提交项目评审评分。在截止时间前，如需修改，可点击上方【撤回修改】重新编辑后提交。"
        />
      )}

      {/* 上下分布布局：上方【项目评审界面（专家）】占满整栏，下方【材料查阅】占满整栏 */}
      {/* 1. 上方：项目评审打分表格（全宽占满整栏） */}
      <Card
        title="项目评审界面（专家）"
        style={{ marginBottom: 16 }}
        bodyStyle={{ padding: 16 }}
      >
        <Table
          rowKey="projectId"
          columns={dynamicColumns}
          dataSource={evaluationData?.projects || []}
          loading={loading}
          pagination={false}
          bordered
          size="middle"
          rowClassName={(record) =>
            record.projectId === selectedProjectId ? 'ant-table-row-selected' : ''
          }
          onRow={(record) => ({
            onClick: () => setSelectedProjectId(record.projectId),
          })}
        />

        <div
          style={{
            marginTop: 16,
            padding: '12px 16px',
            background: '#fafafa',
            borderRadius: 6,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            border: '1px solid #f0f0f0',
          }}
        >
          <Space size="large">
            <Space>
              <Text strong>评审日期：</Text>
              <Text>{dayjs().format('YYYY-MM-DD')}</Text>
            </Space>
            <Space>
              <Text strong>考评主体：</Text>
              <Tag color="purple" style={{ fontSize: 13, padding: '2px 8px' }}>
                {user?.realName}（{roleSummary}）
              </Tag>
            </Space>
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            💡 提示：点击上方表格中的任意项目行，可在下方整栏区域直接查阅该项目的申报材料及附件 PDF
          </Text>
        </div>
      </Card>

      {/* 2. 下方：材料查阅（全宽内嵌 PDF 阅读器） */}
      <Card
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>材料查阅: {currentProject?.projectName || '请选择项目'}</span>
            <Radio.Group
              size="small"
              value={pdfType}
              onChange={(e) => setPdfType(e.target.value)}
            >
              <Radio.Button value="material">
                <FilePdfOutlined /> 申报材料
              </Radio.Button>
              <Radio.Button value="attachment">
                <FilePdfOutlined /> 附件
              </Radio.Button>
            </Radio.Group>
          </div>
        }
        bodyStyle={{ padding: 8, height: '720px' }}
      >
        {currentPdfUrl ? (
          <iframe
            src={currentPdfUrl}
            title="PDF Document"
            style={{ width: '100%', height: '100%', border: 'none', borderRadius: 4 }}
          />
        ) : (
          <div
            style={{
              height: '100%',
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
            }}
          >
            <Empty
              description={
                currentProject
                  ? `该项目暂未上传${pdfType === 'material' ? '申报材料' : '附件'} PDF`
                  : '请在上方表格中选择项目进行查阅'
              }
            />
          </div>
        )}
      </Card>
    </div>
  );
};
