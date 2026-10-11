import React, { useState, useEffect } from 'react';
import {
  Modal,
  Table,
  Button,
  Space,
  Tag,
  Upload,
  message,
  Form,
  Input,
  InputNumber,
  Typography,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  UploadOutlined,
  FilePdfOutlined,
  EyeOutlined,
  DeleteOutlined,
  EditOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  listProjectItemsApi,
  saveProjectItemApi,
  deleteProjectItemApi,
  uploadProjectPdfApi,
  ProjectItemVO,
  ProjectItemSaveDTO,
} from '../../api/project';

const { Text } = Typography;

interface Props {
  batchId: number | null;
  batchName?: string;
  open: boolean;
  onClose: () => void;
}

export const ProjectManagementModal: React.FC<Props> = ({
  batchId,
  batchName,
  open,
  onClose,
}) => {
  const [projects, setProjects] = useState<ProjectItemVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<ProjectItemVO | null>(null);
  const [previewPdfUrl, setPreviewPdfUrl] = useState<string | null>(null);
  const [form] = Form.useForm();

  const loadProjects = async () => {
    if (!batchId) return;
    setLoading(true);
    try {
      const data = await listProjectItemsApi(batchId);
      setProjects(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open && batchId) {
      loadProjects();
    }
  }, [open, batchId]);

  const handleOpenEdit = (item?: ProjectItemVO) => {
    form.resetFields();
    if (item) {
      setEditingItem(item);
      form.setFieldsValue({
        projectCode: item.projectCode,
        projectName: item.projectName,
        ownerName: item.ownerName,
        sortOrder: item.sortOrder,
      });
    } else {
      setEditingItem(null);
      form.setFieldsValue({
        projectCode: String(projects.length + 1),
        projectName: '',
        ownerName: '',
        sortOrder: projects.length + 1,
      });
    }
    setEditModalVisible(true);
  };

  const handleFormSubmit = async () => {
    if (!batchId) return;
    try {
      const values = await form.validateFields();
      const payload: ProjectItemSaveDTO = {
        id: editingItem?.id,
        batchId,
        projectCode: values.projectCode,
        projectName: values.projectName,
        ownerName: values.ownerName,
        sortOrder: values.sortOrder,
      };

      await saveProjectItemApi(payload);
      message.success(editingItem ? '项目信息已更新' : '项目已添加');
      setEditModalVisible(false);
      loadProjects();
    } catch (e) {
      // Form validation error
    }
  };

  const handleDelete = (id: number) => {
    Modal.confirm({
      title: '删除项目',
      content: '确认删除该参评项目及其材料？',
      onOk: async () => {
        await deleteProjectItemApi(id);
        message.success('项目已删除');
        loadProjects();
      },
    });
  };

  const handleUploadPdf = async (
    projectId: number,
    type: 'material' | 'attachment',
    file: File
  ) => {
    try {
      await uploadProjectPdfApi(projectId, type, file);
      message.success('PDF 材料上传成功');
      loadProjects();
    } catch (e) {
      // Error handled
    }
    return false; // Prevent auto upload by antd
  };

  const columns = [
    {
      title: '项目序号/编号',
      dataIndex: 'projectCode',
      key: 'projectCode',
      width: 130,
    },
    {
      title: '项目名称',
      dataIndex: 'projectName',
      key: 'projectName',
      width: 220,
    },
    {
      title: '项目负责人',
      dataIndex: 'ownerName',
      key: 'ownerName',
      width: 130,
    },
    {
      title: '申报材料 (PDF)',
      key: 'material',
      width: 220,
      render: (_: any, record: ProjectItemVO) => (
        <Space direction="vertical" size="small" style={{ maxWidth: '100%' }}>
          {record.hasMaterial ? (
            <Space style={{ maxWidth: '100%', overflow: 'hidden' }}>
              <Tooltip title={record.materialPdfName} placement="topLeft">
                <Tag
                  color="blue"
                  icon={<FilePdfOutlined />}
                  style={{
                    maxWidth: 130,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    verticalAlign: 'middle',
                  }}
                >
                  {record.materialPdfName || '已上传'}
                </Tag>
              </Tooltip>
              <Button
                type="link"
                size="small"
                icon={<EyeOutlined />}
                onClick={() => {
                  const token = localStorage.getItem('token') || '';
                  setPreviewPdfUrl(`/api/projects/items/${record.id}/pdf/material?token=${encodeURIComponent(token)}`);
                }}
              >
                预览
              </Button>
            </Space>
          ) : (
            <Text type="secondary">未上传</Text>
          )}
          <Upload
            accept=".pdf"
            showUploadList={false}
            beforeUpload={(file) => handleUploadPdf(record.id, 'material', file)}
          >
            <Button size="small" icon={<UploadOutlined />}>
              {record.hasMaterial ? '重新上传' : '上传申报材料'}
            </Button>
          </Upload>
        </Space>
      ),
    },
    {
      title: '附件材料 (PDF)',
      key: 'attachment',
      width: 220,
      render: (_: any, record: ProjectItemVO) => (
        <Space direction="vertical" size="small" style={{ maxWidth: '100%' }}>
          {record.hasAttachment ? (
            <Space style={{ maxWidth: '100%', overflow: 'hidden' }}>
              <Tooltip title={record.attachmentPdfName} placement="topLeft">
                <Tag
                  color="cyan"
                  icon={<FilePdfOutlined />}
                  style={{
                    maxWidth: 130,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    verticalAlign: 'middle',
                  }}
                >
                  {record.attachmentPdfName || '已上传'}
                </Tag>
              </Tooltip>
              <Button
                type="link"
                size="small"
                icon={<EyeOutlined />}
                onClick={() => {
                  const token = localStorage.getItem('token') || '';
                  setPreviewPdfUrl(`/api/projects/items/${record.id}/pdf/attachment?token=${encodeURIComponent(token)}`);
                }}
              >
                预览
              </Button>
            </Space>
          ) : (
            <Text type="secondary">未上传</Text>
          )}
          <Upload
            accept=".pdf"
            showUploadList={false}
            beforeUpload={(file) => handleUploadPdf(record.id, 'attachment', file)}
          >
            <Button size="small" icon={<UploadOutlined />}>
              {record.hasAttachment ? '重新上传' : '上传附件材料'}
            </Button>
          </Upload>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: any, record: ProjectItemVO) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleOpenEdit(record)}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            size="small"
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.id)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Modal
      title={
        <div style={{ display: 'flex', justifyContent: 'space-between', paddingRight: 32 }}>
          <span>参评项目管理 - {batchName}</span>
          <Space>
            <Button icon={<ReloadOutlined />} size="small" onClick={loadProjects}>
              刷新
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              size="small"
              onClick={() => handleOpenEdit()}
            >
              录入参评项目
            </Button>
          </Space>
        </div>
      }
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="close" type="primary" onClick={onClose}>
          完成
        </Button>,
      ]}
      width={1000}
      destroyOnClose
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={projects}
        loading={loading}
        pagination={false}
        bordered
        scroll={{ x: 960 }}
        style={{ tableLayout: 'fixed' }}
      />

      {/* Edit/Create Project Modal */}
      <Modal
        title={editingItem ? '编辑参评项目' : '录入参评项目'}
        open={editModalVisible}
        onOk={handleFormSubmit}
        onCancel={() => setEditModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="projectCode"
            label="项目序号 / 编号"
            rules={[{ required: true, message: '请输入项目序号或编号' }]}
          >
            <Input placeholder="如：1 或 PROJ-2026-01" />
          </Form.Item>

          <Form.Item
            name="projectName"
            label="项目名称"
            rules={[{ required: true, message: '请输入项目名称' }]}
          >
            <Input placeholder="如：原料智能集采供应链升级项目" />
          </Form.Item>

          <Form.Item
            name="ownerName"
            label="项目负责人姓名"
            rules={[{ required: true, message: '请输入项目负责人' }]}
          >
            <Input placeholder="负责人姓名（纯文本展示，不设系统角色）" />
          </Form.Item>

          <Form.Item name="sortOrder" label="排序序号">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Embedded PDF Viewer Modal */}
      <Modal
        title="PDF 材料在线查阅"
        open={!!previewPdfUrl}
        onCancel={() => setPreviewPdfUrl(null)}
        footer={null}
        width={900}
        destroyOnClose
      >
        {previewPdfUrl && (
          <iframe
            src={previewPdfUrl}
            title="PDF Preview"
            style={{ width: '100%', height: '70vh', border: 'none' }}
          />
        )}
      </Modal>
    </Modal>
  );
};
