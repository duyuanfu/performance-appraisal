package com.rawcenter.appraisal.service;

import com.rawcenter.appraisal.dto.ChangePasswordDTO;
import com.rawcenter.appraisal.dto.LoginRequest;
import com.rawcenter.appraisal.dto.UserSaveDTO;
import com.rawcenter.appraisal.entity.SysRole;
import com.rawcenter.appraisal.entity.SysUser;
import com.rawcenter.appraisal.vo.LoginResponse;
import com.rawcenter.appraisal.vo.RoleVO;
import com.rawcenter.appraisal.vo.UserVO;

import java.util.List;

public interface UserService {
    LoginResponse login(LoginRequest request);
    UserVO getCurrentUser();
    List<UserVO> listUsers(String keyword, String roleCode, String department);
    UserVO getUserById(Long id);
    void saveUser(UserSaveDTO dto);
    void updateUserStatus(Long id, Integer status);
    List<RoleVO> listAllRoles();
    List<SysUser> listUsersByRoleCode(String roleCode);
    void changePassword(ChangePasswordDTO dto);
    void resetPasswordByAdmin(Long userId);
}
