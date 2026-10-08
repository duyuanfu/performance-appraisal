import axios from 'axios';
import { message } from 'antd';

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    const activeRole = localStorage.getItem('activeRole');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    if (activeRole) {
      config.headers['X-Active-Role'] = activeRole;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

request.interceptors.response.use(
  (response) => {
    // Binary file download (Excel, PDF, etc.)
    if (response.config.responseType === 'blob' || response.data instanceof Blob) {
      return response.data;
    }

    const res = response.data;
    if (res.code === 200) {
      return res.data;
    }
    message.error(res.message || '请求处理失败');
    return Promise.reject(new Error(res.message || 'Error'));
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      message.error('登录状态已失效，请重新登录');
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      localStorage.removeItem('activeRole');
      window.location.href = '/login';
    } else {
      message.error(error.response?.data?.message || error.message || '网络连接异常');
    }
    return Promise.reject(error);
  }
);

export default request;
