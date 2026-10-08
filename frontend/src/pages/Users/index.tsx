import React, { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  Switch,
  message,
  Card,
  Row,
  Col,
} from 'antd';
import { PlusOutlined, SearchOutlined, ReloadOutlined, KeyOutlined } from '@ant-design/icons';
import {
  listUsersApi,
  createUserApi,
  updateUserApi,
  updateUserStatusApi,
  resetPasswordByAdminApi,
  UserSaveDTO,
} from '../../api/user';
import { listAllRolesApi, RoleVO, UserVO } from '../../api/auth';

const ROLE_COLOR_MAP: Record<string, string> = {
  ADMIN: 'red',
  LEADER: 'blue',
  EXPERT: 'purple',
  EMPLOYEE: 'green',
};

const UsersPage: React.FC = () => {
  const [users, setUsers] = useState<UserVO[]>([]);
  const [roles, setRoles] = useState<RoleVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [selectedRole, setSelectedRole] = useState<string | undefined>();
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<UserVO | null>(null);
  const [form] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const [userList, roleList] = await Promise.all([
        listUsersApi({ keyword, roleCode: selectedRole }),
        listAllRolesApi(),
      ]);
      setUsers(userList);
      setRoles(roleList);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleOpenModal = (user?: UserVO) => {
    form.resetFields();
    if (user) {
      setEditingUser(user);
      form.setFieldsValue({
        username: user.username,
        realName: user.realName,
        department: user.department,
        position: user.position,
        email: user.email,
        phone: user.phone,
        status: user.status === 1,
        roleIds: user.roles.map((r) => r.id),
      });
    } else {
      setEditingUser(null);
      form.setFieldsValue({
        status: true,
        roleIds: [4], // Default EMPLOYEE
      });
    }
    setModalVisible(true);
  };

  const handleFormSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload: UserSaveDTO = {
        username: values.username,
        realName: values.realName,
        department: values.department,
        position: values.position,
        email: values.email,
        phone: values.phone,
        status: values.status ? 1 : 0,
        roleIds: values.roleIds,
      };

      if (values.password) {
        payload.password = values.password;
      }

      if (editingUser) {
        await updateUserApi(editingUser.id, payload);
        message.success('用户信息已更新');
      } else {
        await createUserApi(payload);
        message.success('用户已创建（初始密码: password123）');
      }
      setModalVisible(false);
      loadData();
    } catch (e) {
      // Form validation error
    }
  };

  const handleStatusToggle = async (user: UserVO, checked: boolean) => {
    try {
      await updateUserStatusApi(user.id, checked ? 1 : 0);
      message.success(`用户状态已切换为【${checked ? '启用' : '禁用'}】`);
      loadData();
    } catch (e) {
      // Error handled by interceptor
    }
  };

  const handleResetPassword = (user: UserVO) => {
    Modal.confirm({
      title: '重置用户密码',
      icon: <KeyOutlined style={{ color: '#faad14' }} />,
      content: `确认将用户【${user.realName} (${user.username})】的登录密码重置为默认初始密码 (password123) 吗？`,
      okText: '确认重置',
      cancelText: '取消',
      onOk: async () => {
        try {
          await resetPasswordByAdminApi(user.id);
          message.success(`用户【${user.realName}】的登录密码已成功重置为 password123`);
        } catch (e) {
          // Error handled
        }
      },
    });
  };

  const columns = [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 140,
    },
    {
      title: '姓名',
      dataIndex: 'realName',
      key: 'realName',
      width: 130,
    },
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
      width: 160,
    },
    {
      title: '职务',
      dataIndex: 'position',
      key: 'position',
      width: 130,
    },
    {
      title: '角色身份',
      dataIndex: 'roles',
      key: 'roles',
      render: (roleList: RoleVO[]) => (
        <Space wrap>
          {roleList.map((r) => (
            <Tag key={r.id} color={ROLE_COLOR_MAP[r.roleCode] || 'default'}>
              {r.roleName}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: number, record: UserVO) => (
        <Switch
          checked={status === 1}
          checkedChildren="启用"
          unCheckedChildren="禁用"
          onChange={(checked) => handleStatusToggle(record, checked)}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      render: (_: any, record: UserVO) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleOpenModal(record)}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            icon={<KeyOutlined />}
            onClick={() => handleResetPassword(record)}
          >
            重置密码
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card title="用户账号管理" style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} justify="space-between" align="middle">
          <Col>
            <Space>
              <Input
                placeholder="搜索用户名 / 姓名"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                style={{ width: 220 }}
                allowClear
                onPressEnter={loadData}
              />
              <Select
                placeholder="按角色筛选"
                style={{ width: 160 }}
                value={selectedRole}
                onChange={setSelectedRole}
                allowClear
              >
                {roles.map((r) => (
                  <Select.Option key={r.roleCode} value={r.roleCode}>
                    {r.roleName}
                  </Select.Option>
                ))}
              </Select>
              <Button type="primary" icon={<SearchOutlined />} onClick={loadData}>
                查询
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => {
                  setKeyword('');
                  setSelectedRole(undefined);
                  loadData();
                }}
              >
                重置
              </Button>
            </Space>
          </Col>
          <Col>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => handleOpenModal()}
            >
              新建用户
            </Button>
          </Col>
        </Row>
      </Card>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={users}
        loading={loading}
        pagination={{ pageSize: 10 }}
      />

      <Modal
        title={editingUser ? '编辑用户' : '新建用户'}
        open={modalVisible}
        onOk={handleFormSubmit}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input disabled={!!editingUser} placeholder="英文账号，如 zhangsan" />
          </Form.Item>

          <Form.Item
            name="realName"
            label="姓名"
            rules={[{ required: true, message: '请输入真实姓名' }]}
          >
            <Input placeholder="员工真实姓名" />
          </Form.Item>

          <Form.Item
            name="password"
            label={editingUser ? '重置密码（留空则不修改）' : '登录密码'}
            rules={!editingUser ? [{ required: false }] : []}
          >
            <Input.Password placeholder={editingUser ? '留空保持原密码' : '默认: password123'} />
          </Form.Item>

          <Form.Item
            name="department"
            label="所属部门"
            rules={[{ required: true, message: '请输入部门名称' }]}
          >
            <Input placeholder="如：采购一处、仓储物流处" />
          </Form.Item>

          <Form.Item name="position" label="职务">
            <Input placeholder="如：部门负责人、专员" />
          </Form.Item>

          <Form.Item
            name="roleIds"
            label="分配角色（支持多选一人多角）"
            rules={[{ required: true, message: '请至少分配一个角色' }]}
          >
            <Select mode="multiple" placeholder="选择系统角色">
              {roles.map((r) => (
                <Select.Option key={r.id} value={r.id}>
                  {r.roleName} ({r.roleCode})
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item name="email" label="邮箱">
            <Input placeholder="邮箱地址" />
          </Form.Item>

          <Form.Item name="phone" label="联系电话">
            <Input placeholder="手机号码" />
          </Form.Item>

          <Form.Item name="status" label="账号状态" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UsersPage;
