import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import App from './App';
import 'antd/dist/reset.css';
import './index.css';

// 设置全局 Day.js 语言为中文（解决 DatePicker 月份显示英文问题）
dayjs.locale('zh-cn');

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#2563eb',
          colorInfo: '#2563eb',
          colorSuccess: '#16a34a',
          colorWarning: '#d97706',
          colorError: '#dc2626',
          colorBgLayout: '#f8fafc',
          colorBgContainer: '#ffffff',
          colorBorder: '#e2e8f0',
          colorBorderSecondary: '#f1f5f9',
          colorText: '#0f172a',
          colorTextSecondary: '#64748b',
          borderRadius: 10,
          borderRadiusLG: 14,
          controlHeight: 38,
          fontFamily:
            "'Segoe UI Variable', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif",
          boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
          boxShadowSecondary: '0 18px 40px rgba(15, 23, 42, 0.10)',
        },
        components: {
          Layout: {
            headerBg: '#0f172a',
            siderBg: '#ffffff',
            bodyBg: '#f8fafc',
            headerHeight: 64,
            headerPadding: '0 20px',
          },
          Menu: {
            itemBorderRadius: 9,
            itemSelectedBg: '#eff6ff',
            itemSelectedColor: '#1d4ed8',
            itemHoverBg: '#f1f5f9',
            subMenuItemBg: 'transparent',
            itemMarginInline: 10,
            itemHeight: 42,
            iconSize: 16,
            collapsedIconSize: 18,
          },
          Card: {
            borderRadiusLG: 14,
          },
          Table: {
            headerBg: '#f8fafc',
            headerColor: '#475569',
            borderColor: '#e2e8f0',
            rowHoverBg: '#f8fafc',
            headerBorderRadius: 12,
            cellPaddingBlock: 12,
          },
          Button: {
            borderRadius: 9,
            controlHeight: 38,
            fontWeight: 500,
          },
          Input: {
            borderRadius: 9,
            controlHeight: 38,
          },
          Select: {
            borderRadius: 9,
            controlHeight: 38,
          },
          DatePicker: {
            borderRadius: 9,
            controlHeight: 38,
          },
          Modal: {
            borderRadiusLG: 16,
          },
          Tag: {
            borderRadiusSM: 7,
          },
          Segmented: {
            borderRadius: 9,
            itemSelectedBg: '#ffffff',
            itemSelectedColor: '#1d4ed8',
            trackBg: '#f1f5f9',
          },
          Form: {
            labelColor: '#334155',
            labelFontSize: 13,
          },
        },
      }}
    >
      <App />
    </ConfigProvider>
  </React.StrictMode>
);
