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
  Popconfirm,
} from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  KeyOutlined,
  DeleteOutlined,
  EditOutlined,
} from '@ant-design/icons';
import {
  listUsersApi,
  createUserApi,
  updateUserApi,
  updateUserStatusApi,
  resetPasswordByAdminApi,
  deleteUserApi,
  UserSaveDTO,
} from '../../api/user';
import { listAllRolesApi, RoleVO, UserVO } from '../../api/auth';

const ROLE_COLOR_MAP: Record<string, string> = {
  SYS_ADMIN: 'magenta',
  ADMIN: 'red',
  LEADER: 'blue',
  DEPT_HEAD: 'cyan',
  DEPT_DEPUTY: 'geekblue',
  EXPERT: 'purple',
  EMPLOYEE: 'green',
};

const POSITION_CATEGORY_MAP: Record<string, { label: string; color: string }> = {
  PRIMARY_LEADER: { label: '中心主要领导', color: 'volcano' },
  CENTER_LEADER: { label: '中心领导', color: 'blue' },
  DEPT_HEAD: { label: '部门正职', color: 'cyan' },
  DEPT_DEPUTY: { label: '部门副职', color: 'geekblue' },
  EXPERT: { label: '评审专家', color: 'purple' },
  EMPLOYEE: { label: '普通职工', color: 'default' },
};

const UsersPage: React.FC = () => {
  const [users, setUsers] = useState<UserVO[]>([]);
  const [roles, setRoles] = useState<RoleVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [selectedRole, setSelectedRole] = useState<string | undefined>();
  const [selectedDept, setSelectedDept] = useState<string | undefined>();
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<UserVO | null>(null);
  const [form] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const [userList, roleList] = await Promise.all([
        listUsersApi({ keyword, roleCode: selectedRole, department: selectedDept }),
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

  const centerLeaders = users.filter(
    (u) => u.positionCategory === 'PRIMARY_LEADER' || u.positionCategory === 'CENTER_LEADER'
  );
  const deptHeads = users.filter((u) => u.positionCategory === 'DEPT_HEAD');
  const departments = Array.from(new Set(users.map((u) => u.department).filter(Boolean)));

  const handleOpenModal = (user?: UserVO) => {
    form.resetFields();
    if (user) {
      setEditingUser(user);
      form.setFieldsValue({
        username: user.username,
        realName: user.realName,
        department: user.department,
        title: user.title || user.position,
        positionCategory: user.positionCategory || 'EMPLOYEE',
        supervisorId: user.supervisorId || undefined,
        deptHeadId: user.deptHeadId || undefined,
        email: user.email,
        phone: user.phone,
        status: user.status === 1,
        roleIds: user.roles.map((r) => r.id),
      });
    } else {
      setEditingUser(null);
      form.setFieldsValue({
        status: true,
        positionCategory: 'EMPLOYEE',
        roleIds: [7], // Default EMPLOYEE
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
        position: values.title,
        title: values.title,
        positionCategory: values.positionCategory,
        supervisorId: values.supervisorId,
        deptHeadId: values.deptHeadId,
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
      // Error handled
    }
  };

  const handleDeleteUser = async (id: number) => {
    try {
      await deleteUserApi(id);
      message.success('用户账号已成功删除');
      loadData();
    } catch (e) {
      // Error handled
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
      title: '账号/手机号',
      dataIndex: 'username',
      key: 'username',
      width: 130,
    },
    {
      title: '姓名',
      dataIndex: 'realName',
      key: 'realName',
      width: 100,
    },
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
      width: 150,
    },
    {
      title: '职务',
      dataIndex: 'title',
      key: 'title',
      width: 110,
      render: (t: string, r: UserVO) => t || r.position || '-',
    },
    {
      title: '职级类别',
      dataIndex: 'positionCategory',
      key: 'positionCategory',
      width: 120,
      render: (cat: string) => {
        const item = POSITION_CATEGORY_MAP[cat] || { label: cat || '普通职工', color: 'default' };
        return <Tag color={item.color}>{item.label}</Tag>;
      },
    },
    {
      title: '分管领导',
      dataIndex: 'supervisorName',
      key: 'supervisorName',
      width: 100,
      render: (name: string) => (name ? <Tag color="blue">{name}</Tag> : <span style={{ color: '#ccc' }}>-</span>),
    },
    {
      title: '直属正职',
      dataIndex: 'deptHeadName',
      key: 'deptHeadName',
      width: 100,
      render: (name: string) => (name ? <Tag color="cyan">{name}</Tag> : <span style={{ color: '#ccc' }}>-</span>),
    },
    {
      title: '系统角色',
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
      width: 90,
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
      width: 190,
      render: (_: any, record: UserVO) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleOpenModal(record)}>
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
          <Popconfirm
            title="确认删除该用户？"
            description="删除后该用户的考评关系将解除，不可恢复。"
            onConfirm={() => handleDeleteUser(record.id)}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card title="账号与组织架构管理（系统管理员专有权限）" style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} justify="space-between" align="middle">
          <Col>
            <Space wrap>
              <Input
                placeholder="搜索账号 / 姓名"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                style={{ width: 180 }}
                allowClear
                onPressEnter={loadData}
              />
              <Select
                placeholder="按部门筛选"
                style={{ width: 170 }}
                value={selectedDept}
                onChange={setSelectedDept}
                allowClear
              >
                {departments.map((d) => (
                  <Select.Option key={d} value={d}>
                    {d}
                  </Select.Option>
                ))}
              </Select>
              <Select
                placeholder="按角色筛选"
                style={{ width: 150 }}
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
                  setSelectedDept(undefined);
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
              新建人员账号
            </Button>
          </Col>
        </Row>
      </Card>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={users}
        loading={loading}
        pagination={{ pageSize: 15 }}
        bordered
      />

      {/* Create / Edit User Modal */}
      <Modal
        title={editingUser ? `编辑人员资料 - ${editingUser.realName}` : '新建人员账号'}
        open={modalVisible}
        width={720}
        onOk={handleFormSubmit}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="username"
                label="账号 / 手机号"
                rules={[{ required: true, message: '请输入登录账号/手机号' }]}
              >
                <Input disabled={!!editingUser} placeholder="如：13908866003" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="realName"
                label="真实姓名"
                rules={[{ required: true, message: '请输入真实姓名' }]}
              >
                <Input placeholder="员工真实姓名" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="department"
                label="所属部门"
                rules={[{ required: true, message: '请输入或选择部门' }]}
              >
                <Select placeholder="选择或输入部门" showSearch allowClear>
                  {departments.map((d) => (
                    <Select.Option key={d} value={d}>
                      {d}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="title" label="职务">
                <Input placeholder="如：中心主任、部长、副主任、副部长、职工" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="positionCategory"
                label="职级分类"
                rules={[{ required: true, message: '请选择职级分类' }]}
              >
                <Select placeholder="选择职级类别">
                  <Select.Option value="PRIMARY_LEADER">中心主要领导</Select.Option>
                  <Select.Option value="CENTER_LEADER">中心领导（副职/纪委）</Select.Option>
                  <Select.Option value="DEPT_HEAD">部门正职</Select.Option>
                  <Select.Option value="DEPT_DEPUTY">部门副职</Select.Option>
                  <Select.Option value="EXPERT">评审专家</Select.Option>
                  <Select.Option value="EMPLOYEE">普通职工</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="supervisorId" label="分管领导（仅正/副职需配置）">
                <Select placeholder="选择分管领导" allowClear showSearch optionFilterProp="children">
                  {centerLeaders.map((l) => (
                    <Select.Option key={l.id} value={l.id}>
                      {l.realName} ({l.title || '中心领导'})
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="deptHeadId" label="直属正职（仅部门副职需配置）">
                <Select placeholder="选择直属正职领导" allowClear showSearch optionFilterProp="children">
                  {deptHeads.map((h) => (
                    <Select.Option key={h.id} value={h.id}>
                      {h.realName} ({h.department})
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={16}>
              <Form.Item
                name="roleIds"
                label="分配系统角色（支持多选）"
                rules={[{ required: true, message: '请至少分配一个角色' }]}
              >
                <Select mode="multiple" placeholder="选择分配系统角色">
                  {roles.map((r) => (
                    <Select.Option key={r.id} value={r.id}>
                      {r.roleName} ({r.roleCode})
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="phone" label="联系电话">
                <Input placeholder="手机号码" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="password"
                label={editingUser ? '重置密码（留空则不修改）' : '登录密码'}
              >
                <Input.Password placeholder={editingUser ? '留空保持原密码' : '默认: password123'} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="email" label="电子邮箱">
                <Input placeholder="邮箱地址" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
};

export default UsersPage;
