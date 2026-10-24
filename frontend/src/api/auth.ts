import request from './request';

export interface RoleVO {
  id: number;
  roleCode: string;
  roleName: string;
  description: string;
}

export interface UserVO {
  id: number;
  username: string;
  realName: string;
  department: string;
  position?: string;
  title?: string;
  positionCategory?: string;
  supervisorId?: number;
  supervisorName?: string;
  deptHeadId?: number;
  deptHeadName?: string;
  email?: string;
  phone?: string;
  status: number;
  roles: RoleVO[];
}

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  realName: string;
  department: string;
  position?: string;
  title?: string;
  positionCategory?: string;
  activeRole: string;
  roles: RoleVO[];
}

export function loginApi(data: { username: string; password: string }): Promise<LoginResponse> {
  return request.post('/auth/login', data);
}

export function getMeApi(): Promise<UserVO> {
  return request.get('/auth/me');
}

export function listAllRolesApi(): Promise<RoleVO[]> {
  return request.get('/auth/roles');
}

export function logoutApi(): Promise<void> {
  return request.post('/auth/logout');
}

export function changePasswordApi(data: {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}): Promise<void> {
  return request.post('/auth/change-password', data);
}
