import React, { useState } from 'react';
import { Card, Form, Input, Button, Typography, Space, Divider, Alert } from 'antd';
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const { Title, Text } = Typography;

const LoginPage: React.FC = () => {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (values: { username: string; password: string }) => {
    setSubmitting(true);
    try {
      const ok = await login(values.username, values.password);
      if (ok) {
        navigate('/');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleQuickLogin = (username: string) => {
    form.setFieldsValue({ username, password: 'password123' });
    form.submit();
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        background: 'linear-gradient(135deg, #1890ff 0%, #002766 100%)',
        padding: 20,
      }}
    >
      <Card
        style={{
          width: 440,
          boxShadow: '0 8px 24px rgba(0, 0, 0, 0.15)',
          borderRadius: 12,
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <SafetyCertificateOutlined style={{ fontSize: 44, color: '#1890ff' }} />
          <Title level={3} style={{ marginTop: 12, marginBottom: 4 }}>
            原料中心评审管理系统
          </Title>
          <Text type="secondary">月度绩效考核与年度目标管理评审平台</Text>
        </div>

        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item
            name="username"
            label="账号"
            rules={[{ required: true, message: '请输入登录账号' }]}
          >
            <Input
              prefix={<UserOutlined style={{ color: '#bfbfbf' }} />}
              placeholder="请输入用户名（如 admin）"
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: '#bfbfbf' }} />}
              placeholder="请输入密码（默认 password123）"
              size="large"
            />
          </Form.Item>

          <Form.Item style={{ marginTop: 24 }}>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={submitting}
            >
              登录系统
            </Button>
          </Form.Item>
        </Form>

        <Divider style={{ margin: '16px 0' }}>快速演示登录</Divider>
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="点击下方预设账号可快捷体验不同角色"
        />
        <Space wrap style={{ width: '100%', justifyContent: 'center' }}>
          <Button size="small" onClick={() => handleQuickLogin('admin')}>
            管理员 (admin)
          </Button>
          <Button size="small" onClick={() => handleQuickLogin('leader_zhang')}>
            中心领导 (张主任)
          </Button>
          <Button size="small" onClick={() => handleQuickLogin('expert_wang')}>
            评审专家 (王专家)
          </Button>
          <Button size="small" onClick={() => handleQuickLogin('dept_qian')}>
            员工/专家 (钱部长)
          </Button>
        </Space>
      </Card>
    </div>
  );
};

export default LoginPage;
