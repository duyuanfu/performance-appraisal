import React from 'react';
import { Table, Button, Input, InputNumber, Space, Alert, Typography } from 'antd';
import { PlusOutlined, DeleteOutlined, ReloadOutlined, CheckCircleOutlined, WarningOutlined } from '@ant-design/icons';
import { MetricItem, getDefaultTemplatesApi } from '../../api/metric';

const { Text } = Typography;

interface Props {
  metrics: MetricItem[];
  onChange: (metrics: MetricItem[]) => void;
  batchType: 'MONTHLY' | 'ANNUAL_PROJECT';
}

export const MetricConfigTable: React.FC<Props> = ({ metrics, onChange, batchType }) => {
  const handleLoadDefaults = async () => {
    const defaults = await getDefaultTemplatesApi(batchType);
    onChange(defaults);
  };

  const handleAddMetric = () => {
    const newMetric: MetricItem = {
      metricName: `自定义指标${metrics.length + 1}`,
      metricDesc: '',
      maxScore: 100,
      weight: 20,
      sortOrder: metrics.length + 1,
    };
    onChange([...metrics, newMetric]);
  };

  const handleDelete = (index: number) => {
    const updated = metrics.filter((_, idx) => idx !== index);
    onChange(updated);
  };

  const handleChange = (index: number, field: keyof MetricItem, val: any) => {
    const updated = [...metrics];
    updated[index] = {
      ...updated[index],
      [field]: val,
    };
    onChange(updated);
  };

  const totalWeight = metrics.reduce((acc, cur) => acc + (Number(cur.weight) || 0), 0);
  const isWeightValid = Math.abs(totalWeight - 100) < 0.0001;

  const columns = [
    {
      title: '序号',
      key: 'index',
      width: 60,
      render: (_: any, __: any, index: number) => index + 1,
    },
    {
      title: '指标名称',
      dataIndex: 'metricName',
      key: 'metricName',
      width: 180,
      render: (val: string, _: any, index: number) => (
        <Input
          value={val}
          placeholder="如：工作能力、完成度"
          onChange={(e) => handleChange(index, 'metricName', e.target.value)}
        />
      ),
    },
    {
      title: '指标描述 / 评分指引',
      dataIndex: 'metricDesc',
      key: 'metricDesc',
      render: (val: string, _: any, index: number) => (
        <Input
          value={val}
          placeholder="说明该指标的具体考核要求与考量要点"
          onChange={(e) => handleChange(index, 'metricDesc', e.target.value)}
        />
      ),
    },
    {
      title: '满分',
      dataIndex: 'maxScore',
      key: 'maxScore',
      width: 110,
      render: (val: number, _: any, index: number) => (
        <InputNumber
          value={val}
          min={1}
          max={1000}
          precision={0}
          style={{ width: '100%' }}
          onChange={(v) => handleChange(index, 'maxScore', v || 100)}
        />
      ),
    },
    {
      title: '指标权重 (%)',
      dataIndex: 'weight',
      key: 'weight',
      width: 140,
      render: (val: number, _: any, index: number) => (
        <InputNumber
          value={val}
          min={0.0001}
          max={100}
          precision={4}
          step={1}
          style={{ width: '100%' }}
          onChange={(v) => handleChange(index, 'weight', v || 0)}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: any, __: any, index: number) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleDelete(index)}
        />
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={handleLoadDefaults}
          >
            加载默认指标模板
          </Button>
          <Button
            type="dashed"
            icon={<PlusOutlined />}
            onClick={handleAddMetric}
          >
            添加自定义指标
          </Button>
        </Space>

        <div style={{ display: 'flex', alignItems: 'center' }}>
          {isWeightValid ? (
            <Text type="success" strong>
              <CheckCircleOutlined style={{ marginRight: 6 }} />
              权重合计：{totalWeight.toFixed(4)}% (已满足 100% 满分校验)
            </Text>
          ) : (
            <Text type="danger" strong>
              <WarningOutlined style={{ marginRight: 6 }} />
              权重合计：{totalWeight.toFixed(4)}% (必须等于 100.0000%，相差 {(100 - totalWeight).toFixed(4)}%)
            </Text>
          )}
        </div>
      </div>

      <Table
        rowKey={(_, idx) => String(idx)}
        columns={columns}
        dataSource={metrics}
        pagination={false}
        size="small"
        bordered
      />

      {!isWeightValid && (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 12 }}
          message={`当前指标权重之和为 ${totalWeight.toFixed(4)}%，系统强制要求满分总权重必须为 100.0000%，请调整后再保存`}
        />
      )}
    </div>
  );
};
