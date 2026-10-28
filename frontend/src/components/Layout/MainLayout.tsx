import React, { useEffect, useState } from 'react';
import {
  Layout,
  Menu,
  Button,
  Space,
  Tag,
  Dropdown,
  Typography,
  Avatar,
  Modal,
  Form,
  Input,
  message,
  Badge,
} from 'antd';
import {
  UserOutlined,
  LogoutOutlined,
  SwapOutlined,
  AuditOutlined,
  ProjectOutlined,
  TeamOutlined,
  TrophyOutlined,
  CheckCircleOutlined,
  LockOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import dayjs from 'dayjs';
import { useAuth } from '../../context/AuthContext';
import { changePasswordApi } from '../../api/auth';
import { listMonthlyBatchesApi, MonthlyBatchVO } from '../../api/monthly';
import { listProjectBatchesApi, ProjectBatchVO } from '../../api/project';

const { Header, Content, Sider } = Layout;
const { Text } = Typography;

const ROLE_NAME_MAP: Record<string, string> = {
  SYS_ADMIN: '系统管理员',
  ADMIN: '业务管理员',
  LEADER: '中心领导',
  DEPT_HEAD: '部门正职',
  DEPT_DEPUTY: '部门副职',
  EXPERT: '评审专家',
  EMPLOYEE: '普通职工',
};

const ROLE_COLOR_MAP: Record<string, string> = {
  SYS_ADMIN: 'magenta',
  ADMIN: 'red',
  LEADER: 'blue',
  DEPT_HEAD: 'cyan',
  DEPT_DEPUTY: 'geekblue',
  EXPERT: 'purple',
  EMPLOYEE: 'green',
};

export const MainLayout: React.FC = () => {
  const { user, token, activeRole, roles, switchRole, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // Change password modal state
  const [changePwdVisible, setChangePwdVisible] = useState(false);
  const [submittingPwd, setSubmittingPwd] = useState(false);
  const [pwdForm] = Form.useForm();

  // 工作台菜单未提交任务提示
  const [pendingReviewCounts, setPendingReviewCounts] = useState({ monthly: 0, project: 0 });

  useEffect(() => {
    if (!token) {
      setPendingReviewCounts({ monthly: 0, project: 0 });
      return;
    }

    const showMonthly = ['LEADER', 'DEPT_HEAD', 'DEPT_DEPUTY'].includes(activeRole);
    const showProject = ['LEADER', 'DEPT_HEAD', 'EXPERT'].includes(activeRole);
    let cancelled = false;

    const loadPendingCounts = async () => {
      const [monthlyResult, projectResult] = await Promise.allSettled([
        showMonthly ? listMonthlyBatchesApi() : Promise.resolve<MonthlyBatchVO[]>([]),
        showProject ? listProjectBatchesApi() : Promise.resolve<ProjectBatchVO[]>([]),
      ]);
      if (cancelled) return;

      const monthly = monthlyResult.status === 'fulfilled'
        ? monthlyResult.value.filter(
            (b) =>
              b.status === 'IN_PROGRESS' &&
              !b.isExpired &&
              b.myTaskAssigned === true &&
              b.mySubmissionStatus !== 'SUBMITTED'
          ).length
        : 0;

      const project = projectResult.status === 'fulfilled'
        ? projectResult.value.filter(
            (b) =>
              b.status === 'IN_PROGRESS' &&
              b.myTaskAssigned === true &&
              b.mySubmissionStatus !== 'SUBMITTED' &&
              (!b.endTime || dayjs(b.endTime).isAfter(dayjs()))
          ).length
        : 0;

      setPendingReviewCounts({ monthly, project });
    };

    loadPendingCounts();
    const timer = window.setInterval(loadPendingCounts, 60000);
    const refresh = () => loadPendingCounts();
    window.addEventListener('review-tasks-updated', refresh);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
      window.removeEventListener('review-tasks-updated', refresh);
    };
  }, [activeRole, token]);

  const reviewMenuLabel = (label: string, count: number) => (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <span>{label}</span>
      {count > 0 && <Badge count={count} size="small" />}
    </span>
  );

  // Dynamic menu items based on active role
  const getMenuItems = () => {
    switch (activeRole) {
      case 'SYS_ADMIN':
        return [
          {
            key: '/users',
            icon: <TeamOutlined />,
            label: '账号与组织架构管理',
          },
          {
            key: 'monthly-group',
            icon: <AuditOutlined />,
            label: '月度绩效评审管理',
            children: [
              { key: '/monthly-head-admin', icon: <UserOutlined />, label: '正职评审' },
              { key: '/monthly-deputy-admin', icon: <TeamOutlined />, label: '副职评审' },
            ],
          },
          {
            key: 'annual-group',
            icon: <ProjectOutlined />,
            label: '年度项目评审管理',
            children: [
              {
                key: '/target-project-admin',
                icon: <ProjectOutlined />,
                label: '目标绩效任务评审',
              },
              {
                key: '/special-innovation-admin',
                icon: <TrophyOutlined />,
                label: '专项与创新任务评审',
              },
            ],
          },
        ];
      case 'ADMIN':
        return [
          {
            key: 'monthly-group',
            icon: <AuditOutlined />,
            label: '月度绩效评审管理',
            children: [
              { key: '/monthly-head-admin', icon: <UserOutlined />, label: '正职评审' },
              { key: '/monthly-deputy-admin', icon: <TeamOutlined />, label: '副职评审' },
            ],
          },
          {
            key: 'annual-group',
            icon: <ProjectOutlined />,
            label: '年度项目评审管理',
            children: [
              {
                key: '/target-project-admin',
                icon: <ProjectOutlined />,
                label: '目标绩效任务评审',
              },
              {
                key: '/special-innovation-admin',
                icon: <TrophyOutlined />,
                label: '专项与创新任务评审',
              },
            ],
          },
        ];
      case 'LEADER':
        return [
          {
            key: '/leader-scoring',
            icon: <CheckCircleOutlined />,
            label: reviewMenuLabel('月度绩效打分工作台', pendingReviewCounts.monthly),
          },
          {
            key: '/expert-evaluation',
            icon: <ProjectOutlined />,
            label: reviewMenuLabel('项目评审打分工作台', pendingReviewCounts.project),
          },
        ];
      case 'DEPT_HEAD':
        return [
          {
            key: '/leader-scoring',
            icon: <CheckCircleOutlined />,
            label: reviewMenuLabel('部门正职月度考评互评', pendingReviewCounts.monthly),
          },
          {
            key: '/expert-evaluation',
            icon: <ProjectOutlined />,
            label: reviewMenuLabel('项目评审打分工作台', pendingReviewCounts.project),
          },
        ];
      case 'DEPT_DEPUTY':
        return [
          {
            key: '/leader-scoring',
            icon: <CheckCircleOutlined />,
            label: reviewMenuLabel('部门副职月度考评互评', pendingReviewCounts.monthly),
          },
        ];
      case 'EXPERT':
        return [
          {
            key: '/expert-evaluation',
            icon: <ProjectOutlined />,
            label: reviewMenuLabel('项目在线评审工作台', pendingReviewCounts.project),
          },
        ];
      default:
        return [];
    }
  };

  const menuItems = getMenuItems();

  const roleMenuItems = roles.map((r) => ({
    key: r.roleCode,
    label: (
      <Space>
        <span>{r.roleName}</span>
        {r.roleCode === activeRole && <Tag color="blue">当前身份</Tag>}
      </Space>
    ),
    onClick: () => {
      switchRole(r.roleCode);
      if (r.roleCode === 'SYS_ADMIN') navigate('/users');
      else if (r.roleCode === 'ADMIN') navigate('/monthly-head-admin');
      else if (r.roleCode === 'LEADER') navigate('/leader-scoring');
      else if (r.roleCode === 'DEPT_HEAD') navigate('/leader-scoring');
      else if (r.roleCode === 'DEPT_DEPUTY') navigate('/leader-scoring');
      else if (r.roleCode === 'EXPERT') navigate('/expert-evaluation');
      else if (r.roleCode === 'EMPLOYEE') navigate('/');
    },
  }));

  const userActionMenuItems = [
    {
      key: 'change-password',
      icon: <LockOutlined />,
      label: '修改我的密码',
      onClick: () => {
        pwdForm.resetFields();
        setChangePwdVisible(true);
      },
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      danger: true,
      label: '退出登录',
      onClick: () => {
        logout();
        navigate('/login');
      },
    },
  ];

  const handleChangePasswordSubmit = async () => {
    try {
      const values = await pwdForm.validateFields();
      setSubmittingPwd(true);
      await changePasswordApi({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword,
      });
      message.success('密码修改成功，请使用新密码重新登录！');
      setChangePwdVisible(false);
      logout();
      navigate('/login');
    } catch (e) {
      // Handled
    } finally {
      setSubmittingPwd(false);
    }
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          backgroundColor: '#001529',
          padding: '0 24px',
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.15)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <AuditOutlined style={{ fontSize: 24, color: '#1890ff', marginRight: 12 }} />
          <Text strong style={{ color: '#ffffff', fontSize: 18, letterSpacing: 1 }}>
            原料中心评审管理系统
          </Text>
        </div>

        <Space size="large">
          {/* Multi-role switcher */}
          <Space>
            <Text style={{ color: '#8c8c8c' }}>当前身份:</Text>
            {roles.length > 1 ? (
              <Dropdown menu={{ items: roleMenuItems }} placement="bottomRight">
                <Button
                  type="primary"
                  ghost
                  icon={<SwapOutlined />}
                  style={{ borderRadius: 4 }}
                >
                  <Tag
                    color={ROLE_COLOR_MAP[activeRole] || 'default'}
                    style={{ marginRight: 4 }}
                  >
                    {ROLE_NAME_MAP[activeRole] || activeRole}
                  </Tag>
                  切换身份
                </Button>
              </Dropdown>
            ) : (
              <Tag color={ROLE_COLOR_MAP[activeRole] || 'default'}>
                {ROLE_NAME_MAP[activeRole] || activeRole}
              </Tag>
            )}
          </Space>

          {/* User profile dropdown */}
          <Dropdown menu={{ items: userActionMenuItems }} placement="bottomRight">
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                cursor: 'pointer',
                padding: '4px 8px',
                borderRadius: 4,
              }}
            >
              <Avatar icon={<UserOutlined />} style={{ backgroundColor: '#1890ff' }} />
              <div style={{ lineHeight: '1.2', textAlign: 'left' }}>
                <div style={{ color: '#fff', fontWeight: 500 }}>{user?.realName}</div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{user?.department}</div>
              </div>
            </div>
          </Dropdown>
        </Space>
      </Header>

      <Layout>
        <Sider width={220} style={{ background: '#fff' }}>
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            defaultOpenKeys={['monthly-group', 'annual-group']}
            style={{ height: '100%', borderRight: 0 }}
            items={menuItems}
            onClick={({ key }) => {
              if (key.startsWith('/')) {
                navigate(key);
              }
            }}
          />
        </Sider>

        <Layout style={{ padding: '20px', minHeight: 'calc(100vh - 64px)', minWidth: 0 }}>
          <Content
            style={{
              padding: 24,
              margin: 0,
              minHeight: 280,
              minWidth: 0,
              background: '#fff',
              borderRadius: 8,
              boxShadow: '0 1px 4px rgba(0, 0, 0, 0.05)',
            }}
          >
            <Outlet />
          </Content>
        </Layout>
      </Layout>

      {/* Change Password Modal */}
      <Modal
        title="修改登录密码"
        open={changePwdVisible}
        onOk={handleChangePasswordSubmit}
        confirmLoading={submittingPwd}
        onCancel={() => setChangePwdVisible(false)}
        destroyOnClose
      >
        <Form form={pwdForm} layout="vertical">
          <Form.Item
            name="oldPassword"
            label="当前原密码"
            rules={[{ required: true, message: '请输入当前使用的原密码' }]}
          >
            <Input.Password placeholder="输入原密码" />
          </Form.Item>

          <Form.Item
            name="newPassword"
            label="设置新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '新密码长度不能少于 6 位' },
            ]}
          >
            <Input.Password placeholder="输入新密码（不少于6位）" />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码确认' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的新密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password placeholder="再次输入新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  );
};
