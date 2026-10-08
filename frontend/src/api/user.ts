import request from './request';
import { UserVO } from './auth';

export interface UserSaveDTO {
  id?: number;
  username: string;
  password?: string;
  realName: string;
  department: string;
  position?: string;
  email?: string;
  phone?: string;
  status?: number;
  roleIds: number[];
}

export function listUsersApi(params?: { keyword?: string; roleCode?: string; department?: string }): Promise<UserVO[]> {
  return request.get('/users', { params });
}

export function getUserByIdApi(id: number): Promise<UserVO> {
  return request.get(`/users/${id}`);
}

export function createUserApi(data: UserSaveDTO): Promise<void> {
  return request.post('/users', data);
}

export function updateUserApi(id: number, data: UserSaveDTO): Promise<void> {
  return request.put(`/users/${id}`, data);
}

export function updateUserStatusApi(id: number, status: number): Promise<void> {
  return request.put(`/users/${id}/status`, null, { params: { status } });
}

export function resetPasswordByAdminApi(id: number): Promise<void> {
  return request.post(`/users/${id}/reset-password`);
}
