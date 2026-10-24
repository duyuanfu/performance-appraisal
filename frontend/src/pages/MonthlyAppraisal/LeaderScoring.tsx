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
  Empty,
} from 'antd';
import {
  SaveOutlined,
  CheckOutlined,
  UndoOutlined,
  ReloadOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  listMonthlyBatchesApi,
  getLeaderScoringDataApi,
  saveLeaderScoresApi,
  withdrawLeaderScoresApi,
  MonthlyBatchVO,
  MonthlyLeaderScoringVO,
} from '../../api/monthly';

const { Title, Text } = Typography;

export const LeaderScoringPage: React.FC = () => {
  const [batches, setBatches] = useState<MonthlyBatchVO[]>([]);
  const [selectedBatchId, setSelectedBatchId] = useState<number | null>(null);
  const [scoringData, setScoringData] = useState<MonthlyLeaderScoringVO | null>(null);
  const [scores, setScores] = useState<Record<number, Record<number, number>>>({}); // examineeId -> metricId -> score
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const loadBatches = async () => {
    try {
      // 查询分配给该领导的所有批次（包含评分中和已归档，归档后仍可查阅历史打分）
      const list = await listMonthlyBatchesApi();
      setBatches(list);
      if (list.length > 0) {
        if (!selectedBatchId || !list.some((b) => b.id === selectedBatchId)) {
          setSelectedBatchId(list[0].id);
        }
      } else {
        setSelectedBatchId(null);
        setScoringData(null);
      }
    } catch (e) {
      // Handled by interceptor
    }
  };

  const loadScoringData = async (batchId: number) => {
    setLoading(true);
    try {
      const data = await getLeaderScoringDataApi(batchId);
      setScoringData(data);

      // Initialize scores map from server data
      const map: Record<number, Record<number, number>> = {};
      data.rows.forEach((row) => {
        map[row.examineeUserId] = { ...(row.metricScores || {}) };
      });
      setScores(map);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBatches();
  }, []);

  useEffect(() => {
    if (selectedBatchId) {
      loadScoringData(selectedBatchId);
    }
  }, [selectedBatchId]);

  const handleScoreChange = (examineeId: number, metricId: number, val: number | null) => {
    setScores((prev) => ({
      ...prev,
      [examineeId]: {
        ...(prev[examineeId] || {}),
        [metricId]: val || 0,
      },
    }));
  };

  const calculateRowTotal = (examineeId: number): number => {
    if (!scoringData) return 0;
    const rowScores = scores[examineeId] || {};
    let total = 0;
    scoringData.metrics.forEach((m) => {
      const s = rowScores[m.id!] || 0;
      total += s * (m.weight / 100);
    });
    return Number(total.toFixed(4));
  };

  const handleSave = async (isSubmit: boolean) => {
    if (!selectedBatchId || !scoringData) return;

    if (isSubmit) {
      // Validate all scores are filled
      for (const row of scoringData.rows) {
        const rowScores = scores[row.examineeUserId] || {};
        for (const m of scoringData.metrics) {
          const s = rowScores[m.id!];
          if (s === undefined || s === null) {
            message.error(`【${row.realName}】的指标【${m.metricName}】尚未评分，请补全后提交`);
            return;
          }
          if (s < 0 || s > m.maxScore) {
            message.error(`【${row.realName}】的指标【${m.metricName}】分值必须在 0 ~ ${m.maxScore} 之间`);
            return;
          }
        }
      }
    }

    setSaving(true);
    try {
      const items = scoringData.rows.map((row) => ({
        examineeUserId: row.examineeUserId,
        metricScores: scores[row.examineeUserId] || {},
      }));

      await saveLeaderScoresApi({
        batchId: selectedBatchId,
        items,
        isSubmit,
      });

      message.success(isSubmit ? '打分已正式提交！' : '打分数据已成功暂存');
      loadScoringData(selectedBatchId);
      loadBatches();
    } finally {
      setSaving(false);
    }
  };

  const handleWithdraw = () => {
    if (!selectedBatchId) return;
    Modal.confirm({
      title: '撤回评分修改',
      content: '撤回后将重新转为草稿编辑状态，您可重新修改打分并在截止前再次提交。确认撤回？',
      onOk: async () => {
        await withdrawLeaderScoresApi(selectedBatchId);
        message.success('已撤回至草稿状态，现在可以修改打分');
        loadScoringData(selectedBatchId);
        loadBatches();
      },
    });
  };

  const isSubmitted = scoringData?.mySubmissionStatus === 'SUBMITTED';
  const isExpired = !!scoringData?.isExpired;
  const isArchived = scoringData?.batchStatus === 'COMPLETED';
  const isReadOnly = isSubmitted || isExpired || isArchived;

  const dynamicColumns = [
    {
      title: '序号',
      key: 'idx',
      width: 60,
      render: (_: any, __: any, index: number) => index + 1,
    },
    {
      title: '被考核人员',
      dataIndex: 'realName',
      key: 'realName',
      width: 120,
    },
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
      width: 140,
    },
    {
      title: '职务',
      dataIndex: 'position',
      key: 'position',
      width: 120,
    },
    {
      title: '考评关系',
      dataIndex: 'evaluatorRelationship',
      key: 'evaluatorRelationship',
      width: 130,
      render: (rel: string) => {
        let color = 'blue';
        if (rel === '中心主要领导') color = 'volcano';
        else if (rel === '分管领导') color = 'green';
        else if (rel === '本部门正职') color = 'purple';
        else if (rel && rel.includes('互评')) color = 'cyan';
        return <Tag color={color}>{rel || '考核人'}</Tag>;
      },
    },
    ...(scoringData?.metrics || []).map((m) => ({
      title: (
        <div>
          <div>{m.metricName}</div>
          <Text type="secondary" style={{ fontSize: 11 }}>
            满分{m.maxScore} / 权重{Number(m.weight).toFixed(2)}%
          </Text>
        </div>
      ),
      key: `metric_${m.id}`,
      width: 150,
      render: (_: any, record: any) => {
        const val = scores[record.examineeUserId]?.[m.id!];
        return (
          <InputNumber
            value={val}
            disabled={isReadOnly}
            min={0}
            max={m.maxScore}
            precision={4}
            step={1}
            style={{ width: '100%' }}
            placeholder={`0 ~ ${m.maxScore}`}
            onChange={(v) => handleScoreChange(record.examineeUserId, m.id!, v)}
          />
        );
      },
    })),
    {
      title: '个人加权总分',
      key: 'total',
      width: 140,
      fixed: 'right' as const,
      render: (_: any, record: any) => {
        const total = calculateRowTotal(record.examineeUserId);
        return (
          <Text strong style={{ color: '#1890ff', fontSize: 15 }}>
            {total.toFixed(4)}
          </Text>
        );
      },
    },
  ];

  if (!loading && batches.length === 0) {
    return (
      <Card style={{ padding: 40, textAlign: 'center' }}>
        <Empty description="当前暂无分配给您的月度绩效考核打分任务" />
      </Card>
    );
  }

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Row justify="space-between" align="middle" gutter={[16, 16]}>
          <Col>
            <Space size="middle">
              <Text strong style={{ fontSize: 16 }}>选择考核批次：</Text>
              <Select
                value={selectedBatchId}
                onChange={setSelectedBatchId}
                style={{ width: 440 }}
                placeholder="请选择月度考核批次"
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
                        ? '评分中'
                        : '草稿'}
                    </Tag>
                  </Select.Option>
                ))}
              </Select>
              <Button icon={<ReloadOutlined />} onClick={() => selectedBatchId && loadScoringData(selectedBatchId)}>
                刷新
              </Button>
            </Space>
          </Col>

          <Col>
            <Space>
              {isSubmitted && !isExpired && !isArchived && (
                <Button
                  icon={<UndoOutlined />}
                  onClick={handleWithdraw}
                >
                  撤回修改
                </Button>
              )}

              {!isReadOnly && (
                <>
                  <Button
                    icon={<SaveOutlined />}
                    loading={saving}
                    onClick={() => handleSave(false)}
                  >
                    暂存打分
                  </Button>
                  <Button
                    type="primary"
                    icon={<CheckOutlined />}
                    loading={saving}
                    onClick={() => handleSave(true)}
                  >
                    正式提交评分
                  </Button>
                </>
              )}
            </Space>
          </Col>
        </Row>

        {scoringData && (
          <div style={{ marginTop: 16, display: 'flex', gap: 16, alignItems: 'center' }}>
            <Space>
              <Text type="secondary">打分截止时间：</Text>
              <Text strong>
                {scoringData.endTime ? dayjs(scoringData.endTime).format('YYYY-MM-DD HH:mm:ss') : '无限制'}
              </Text>
              {isExpired && <Tag color="error">已截止锁定</Tag>}
            </Space>

            <Space>
              <Text type="secondary">我的提交状态：</Text>
              {isSubmitted ? (
                <Tag color="success" icon={<CheckCircleOutlined />}>已正式提交</Tag>
              ) : (
                <Tag color="warning" icon={<ClockCircleOutlined />}>草稿 (未提交)</Tag>
              )}
            </Space>
          </div>
        )}
      </Card>

      {isArchived && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="该月度考核批次已正式归档完成，打分已封存入库。您可完整查阅历史评分明细，不可修改或撤回。"
        />
      )}

      {isSubmitted && !isExpired && !isArchived && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 16 }}
          message="您已提交打分。在考核截止时间前，如需修改，可点击上方【撤回修改】重新编辑后提交。"
        />
      )}

      {isExpired && !isArchived && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="本批次月度考核已到达截止时间，评分已全部锁定，不支持修改或撤回。"
        />
      )}

      <Table
        rowKey="examineeUserId"
        columns={dynamicColumns}
        dataSource={scoringData?.rows || []}
        loading={loading}
        pagination={false}
        scroll={{ x: 'max-content' }}
        bordered
      />
    </div>
  );
};
