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
} from 'antd';
import {
  UserOutlined,
  LogoutOutlined,
  SwapOutlined,
  AuditOutlined,
  ProjectOutlined,
  TeamOutlined,
  CheckCircleOutlined,
  LockOutlined,
  MenuOutlined,
  FlagOutlined,
  BulbOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { changePasswordApi } from '../../api/auth';

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
  const { user, activeRole, roles, switchRole, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [isMobile, setIsMobile] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(max-width: 991px)').matches
  );
  const [collapsed, setCollapsed] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(max-width: 991px)').matches
  );
  const [openKeys, setOpenKeys] = useState<string[]>(['monthly-group', 'annual-group']);

  useEffect(() => {
    const media = window.matchMedia('(max-width: 991px)');
    const handleChange = (event: MediaQueryListEvent) => {
      setIsMobile(event.matches);
      setCollapsed(event.matches);
    };
    media.addEventListener('change', handleChange);
    return () => media.removeEventListener('change', handleChange);
  }, []);

  useEffect(() => {
    const shouldLockBody = isMobile && !collapsed;
    document.body.style.overflow = shouldLockBody ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [isMobile, collapsed]);

  useEffect(() => {
    if (isMobile) {
      setCollapsed(true);
    }
  }, [location.pathname, isMobile]);

  useEffect(() => {
    const group = location.pathname.startsWith('/monthly')
      ? 'monthly-group'
      : location.pathname.startsWith('/annual') ||
        location.pathname.startsWith('/target-project') ||
        location.pathname.startsWith('/special-innovation')
      ? 'annual-group'
      : null;
    if (group) {
      setOpenKeys((prev) => (prev.includes(group) ? prev : [...prev, group]));
    }
  }, [location.pathname]);

  // Change password modal state
  const [changePwdVisible, setChangePwdVisible] = useState(false);
  const [submittingPwd, setSubmittingPwd] = useState(false);
  const [pwdForm] = Form.useForm();

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
                icon: <FlagOutlined />,
                label: '目标绩效任务评审',
              },
              {
                key: '/special-innovation-admin',
                icon: <BulbOutlined />,
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
                icon: <FlagOutlined />,
                label: '目标绩效任务评审',
              },
              {
                key: '/special-innovation-admin',
                icon: <BulbOutlined />,
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
            label: '月度绩效打分工作台',
          },
          {
            key: '/expert-evaluation',
            icon: <ProjectOutlined />,
            label: '项目评审打分工作台',
          },
        ];
      case 'DEPT_HEAD':
        return [
          {
            key: '/leader-scoring',
            icon: <CheckCircleOutlined />,
            label: '部门正职月度考评互评',
          },
          {
            key: '/expert-evaluation',
            icon: <ProjectOutlined />,
            label: '项目评审打分工作台',
          },
          {
            key: '/my-performance',
            icon: <BarChartOutlined />,
            label: '我的月度考核结果',
          },
        ];
      case 'DEPT_DEPUTY':
        return [
          {
            key: '/leader-scoring',
            icon: <CheckCircleOutlined />,
            label: '部门副职月度考评互评',
          },
          {
            key: '/my-performance',
            icon: <BarChartOutlined />,
            label: '我的月度考核结果',
          },
        ];
      case 'EXPERT':
        return [
          {
            key: '/expert-evaluation',
            icon: <ProjectOutlined />,
            label: '项目在线评审工作台',
          },
        ];
      case 'EMPLOYEE':
        return [
          {
            key: '/my-performance',
            icon: <BarChartOutlined />,
            label: '我的月度考核与排名',
          },
        ];
      default:
        return [];
    }
  };

  const menuItems = getMenuItems();
  const selectedMenuKey =
    location.pathname === '/monthly-admin'
      ? 'monthly-group'
      : location.pathname === '/annual-admin'
      ? 'annual-group'
      : location.pathname;

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
      else if (r.roleCode === 'EMPLOYEE') navigate('/my-performance');
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
    <Layout className="app-shell">
      <Header className="app-header">
        <div className="app-header-left">
          <Button
            type="text"
            className="app-menu-toggle"
            icon={<MenuOutlined />}
            onClick={() => setCollapsed((value) => !value)}
            aria-label="切换导航菜单"
            aria-expanded={!collapsed}
            aria-controls="app-navigation"
          />
          <div className="app-brand">
            <div className="app-brand-mark">
              <AuditOutlined />
            </div>
            <div className="app-brand-copy">
              <div className="app-brand-title">原料中心评审管理系统</div>
              <div className="app-brand-subtitle">Performance Appraisal</div>
            </div>
          </div>
        </div>

        <Space size="middle" className="app-header-right">
          {/* Multi-role switcher */}
          <Space className="app-role-switch">
            <Text className="app-role-label">当前身份</Text>
            {roles.length > 1 ? (
              <Dropdown menu={{ items: roleMenuItems }} placement="bottomRight">
                <Button
                  type="primary"
                  ghost
                  icon={<SwapOutlined />}
                  className="app-role-switch-btn"
                >
                  <Tag
                    color={ROLE_COLOR_MAP[activeRole] || 'default'}
                    className="app-role-tag"
                  >
                    {ROLE_NAME_MAP[activeRole] || activeRole}
                  </Tag>
                  <span className="app-role-switch-text">切换身份</span>
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
            <Button
              type="text"
              className="app-user-trigger"
              aria-label={`用户菜单：${user?.realName || ''}`}
            >
              <Avatar icon={<UserOutlined />} className="app-user-avatar" />
              <div className="app-user-meta">
                <div className="app-user-name">{user?.realName}</div>
                <div className="app-user-dept">{user?.department}</div>
              </div>
            </Button>
          </Dropdown>
        </Space>
      </Header>

      <Layout className="app-body">
        {isMobile && !collapsed && (
          <div
            className="app-sider-mask"
            onClick={() => setCollapsed(true)}
            aria-hidden="true"
          />
        )}
        <Sider
          id="app-navigation"
          width={240}
          className={`app-sider${isMobile && !collapsed ? ' app-sider-mobile-open' : ''}`}
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          trigger={null}
          collapsedWidth={0}
        >
          <div className="app-sider-inner">
            <div className="app-sider-label">功能导航</div>
            <Menu
              mode="inline"
              selectedKeys={[selectedMenuKey]}
              openKeys={openKeys}
              onOpenChange={setOpenKeys}
              className="app-menu"
              items={menuItems}
              onClick={({ key }) => {
                if (key.startsWith('/')) {
                  navigate(key);
                  if (isMobile) {
                    setCollapsed(true);
                  }
                }
              }}
            />
            <div className="app-sider-footer">V2.0 · 评审管理</div>
          </div>
        </Sider>

        <Layout className="app-main">
          <Content className="app-content">
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
