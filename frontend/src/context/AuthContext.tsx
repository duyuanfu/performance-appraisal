import React, { createContext, useContext, useState, useEffect } from 'react';
import { UserVO, RoleVO, loginApi, getMeApi, logoutApi } from '../api/auth';
import { message } from 'antd';

interface AuthContextType {
  user: UserVO | null;
  token: string | null;
  activeRole: string;
  roles: RoleVO[];
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  switchRole: (roleCode: string) => void;
  hasRole: (roleCode: string) => boolean;
  refreshUser: () => Promise<void>;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(localStorage.getItem('token'));
  const [user, setUser] = useState<UserVO | null>(() => {
    const cached = localStorage.getItem('user');
    return cached ? JSON.parse(cached) : null;
  });
  const [activeRole, setActiveRole] = useState<string>(() => {
    return localStorage.getItem('activeRole') || 'EMPLOYEE';
  });
  const [loading, setLoading] = useState<boolean>(true);

  const refreshUser = async () => {
    try {
      if (!token) {
        setLoading(false);
        return;
      }
      const userData = await getMeApi();
      setUser(userData);
      localStorage.setItem('user', JSON.stringify(userData));

      // Validate or set active role
      const userRoles = userData.roles.map((r) => r.roleCode);
      const savedRole = localStorage.getItem('activeRole');
      if (savedRole && userRoles.includes(savedRole)) {
        setActiveRole(savedRole);
      } else if (userRoles.length > 0) {
        setActiveRole(userRoles[0]);
        localStorage.setItem('activeRole', userRoles[0]);
      }
    } catch (err) {
      console.error('Failed to load user info', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (token) {
      refreshUser();
    } else {
      setLoading(false);
    }
  }, [token]);

  const login = async (username: string, password: string): Promise<boolean> => {
    try {
      const resp = await loginApi({ username, password });
      localStorage.setItem('token', resp.token);
      localStorage.setItem('activeRole', resp.activeRole);
      setToken(resp.token);
      setActiveRole(resp.activeRole);
      message.success(`欢迎回来，${resp.realName}！`);
      await refreshUser();
      return true;
    } catch (err) {
      return false;
    }
  };

  const logout = async () => {
    try {
      await logoutApi();
    } catch (e) {
      // ignore
    }
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    localStorage.removeItem('activeRole');
    setToken(null);
    setUser(null);
    setActiveRole('EMPLOYEE');
    message.info('已退出登录');
  };

  const switchRole = (roleCode: string) => {
    if (!user) return;
    const isAllowed = user.roles.some((r) => r.roleCode === roleCode);
    if (!isAllowed) {
      message.error('您没有该角色权限');
      return;
    }
    setActiveRole(roleCode);
    localStorage.setItem('activeRole', roleCode);
    const roleName = user.roles.find((r) => r.roleCode === roleCode)?.roleName || roleCode;
    message.success(`已切换至【${roleName}】工作台`);
  };

  const hasRole = (roleCode: string): boolean => {
    if (!user) return false;
    return user.roles.some((r) => r.roleCode === roleCode);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        activeRole,
        roles: user?.roles || [],
        login,
        logout,
        switchRole,
        hasRole,
        refreshUser,
        loading,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
