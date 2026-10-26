import React, { useState } from 'react';
import { Card, Form, Input, Button } from 'antd';
import {
  UserOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

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

  return (
    <div className="login-page">
      <div className="login-brand-panel">
        <div className="login-brand-content">
          <div className="login-brand-icon">
            <SafetyCertificateOutlined />
          </div>
          <h1 className="login-brand-title">原料中心评审管理系统</h1>
          <div className="login-brand-desc">
            面向中心领导、部门正副职与评审专家的月度绩效及年度项目评审平台，
            统一管理评审任务、打分进度与汇总排名。
          </div>
          <div className="login-feature-list">
            <div className="login-feature-item">
              <CheckCircleOutlined /> 正职 / 副职月度绩效独立评审
            </div>
            <div className="login-feature-item">
              <CheckCircleOutlined /> 目标绩效任务与专项创新任务评审
            </div>
            <div className="login-feature-item">
              <CheckCircleOutlined /> 全流程进度跟踪与汇总排名
            </div>
          </div>
        </div>
      </div>

      <div className="login-form-panel">
        <Card className="login-card" bordered={false}>
          <div className="login-card-header">
            <h2 className="login-card-title">欢迎登录</h2>
            <div className="login-card-subtitle">请输入您的账号与密码进入系统</div>
          </div>

          <Form form={form} layout="vertical" onFinish={handleSubmit}>
            <Form.Item
              name="username"
              label="账号"
              rules={[{ required: true, message: '请输入登录账号' }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: '#94a3b8' }} />}
                placeholder="请输入用户名"
                size="large"
                autoComplete="username"
                aria-label="登录账号"
              />
            </Form.Item>

            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#94a3b8' }} />}
                placeholder="请输入密码"
                size="large"
                autoComplete="current-password"
                aria-label="登录密码"
              />
            </Form.Item>

            <Form.Item style={{ marginTop: 24, marginBottom: 8 }}>
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

          <div className="login-hint">如忘记密码，请联系系统管理员重置。</div>
        </Card>
      </div>
    </div>
  );
};

export default LoginPage;
