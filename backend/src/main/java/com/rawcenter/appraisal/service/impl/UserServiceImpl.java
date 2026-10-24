package com.rawcenter.appraisal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rawcenter.appraisal.common.BusinessException;
import com.rawcenter.appraisal.common.JwtUtils;
import com.rawcenter.appraisal.common.PasswordUtils;
import com.rawcenter.appraisal.common.UserContext;
import com.rawcenter.appraisal.dto.LoginRequest;
import com.rawcenter.appraisal.dto.UserSaveDTO;
import com.rawcenter.appraisal.entity.SysRole;
import com.rawcenter.appraisal.entity.SysUser;
import com.rawcenter.appraisal.entity.SysUserRole;
import com.rawcenter.appraisal.mapper.SysRoleMapper;
import com.rawcenter.appraisal.mapper.SysUserMapper;
import com.rawcenter.appraisal.mapper.SysUserRoleMapper;
import com.rawcenter.appraisal.service.UserService;
import com.rawcenter.appraisal.vo.LoginResponse;
import com.rawcenter.appraisal.vo.RoleVO;
import com.rawcenter.appraisal.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final JwtUtils jwtUtils;

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException(400, "用户名或密码错误");
        }

        if (user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }

        if (!PasswordUtils.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(400, "用户名或密码错误");
        }

        List<RoleVO> roleVOs = getUserRoles(user.getId());
        if (CollectionUtils.isEmpty(roleVOs)) {
            throw new BusinessException(403, "该账号未配置任何系统权限，请联系管理员");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRealName());

        // Default role priority: ADMIN -> LEADER -> EXPERT -> EMPLOYEE
        String defaultRole = roleVOs.stream()
                .map(RoleVO::getRoleCode)
                .sorted(Comparator.comparingInt(this::getRolePriority))
                .findFirst()
                .orElse(roleVOs.get(0).getRoleCode());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .department(user.getDepartment())
                .position(user.getPosition())
                .title(user.getTitle())
                .positionCategory(user.getPositionCategory())
                .activeRole(defaultRole)
                .roles(roleVOs)
                .build();
    }

    private int getRolePriority(String roleCode) {
        return switch (roleCode) {
            case "SYS_ADMIN" -> 0;
            case "ADMIN" -> 1;
            case "LEADER" -> 2;
            case "DEPT_HEAD" -> 3;
            case "DEPT_DEPUTY" -> 4;
            case "EXPERT" -> 5;
            case "EMPLOYEE" -> 6;
            default -> 7;
        };
    }

    @Override
    public UserVO getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return getUserById(userId);
    }

    @Override
    public List<UserVO> listUsers(String keyword, String roleCode, String department) {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .and(StringUtils.hasText(keyword), q -> q.like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getRealName, keyword))
                .eq(StringUtils.hasText(department), SysUser::getDepartment, department)
                .orderByAsc(SysUser::getId));

        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserVO> result = new ArrayList<>();
        for (SysUser u : users) {
            List<RoleVO> roles = getUserRoles(u.getId());
            if (StringUtils.hasText(roleCode)) {
                boolean match = roles.stream().anyMatch(r -> r.getRoleCode().equalsIgnoreCase(roleCode));
                if (!match) continue;
            }
            result.add(toUserVO(u, roles));
        }
        return result;
    }

    @Override
    public UserVO getUserById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        List<RoleVO> roles = getUserRoles(user.getId());
        return toUserVO(user, roles);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUser(UserSaveDTO dto) {
        if (dto.getId() == null) {
            // Check username uniqueness
            Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, dto.getUsername()));
            if (count > 0) {
                throw new BusinessException(400, "用户名已存在");
            }

            String supervisorName = dto.getSupervisorName();
            if (!StringUtils.hasText(supervisorName) && dto.getSupervisorId() != null) {
                SysUser sup = userMapper.selectById(dto.getSupervisorId());
                if (sup != null) supervisorName = sup.getRealName();
            }
            String deptHeadName = dto.getDeptHeadName();
            if (!StringUtils.hasText(deptHeadName) && dto.getDeptHeadId() != null) {
                SysUser dh = userMapper.selectById(dto.getDeptHeadId());
                if (dh != null) deptHeadName = dh.getRealName();
            }

            String password = StringUtils.hasText(dto.getPassword()) ? dto.getPassword() : "password123";
            SysUser user = SysUser.builder()
                    .username(dto.getUsername())
                    .passwordHash(PasswordUtils.sha256(password))
                    .realName(dto.getRealName())
                    .department(dto.getDepartment())
                    .position(dto.getPosition() != null ? dto.getPosition() : "")
                    .title(dto.getTitle() != null ? dto.getTitle() : "")
                    .positionCategory(dto.getPositionCategory() != null ? dto.getPositionCategory() : "EMPLOYEE")
                    .supervisorId(dto.getSupervisorId())
                    .supervisorName(supervisorName != null ? supervisorName : "")
                    .deptHeadId(dto.getDeptHeadId())
                    .deptHeadName(deptHeadName != null ? deptHeadName : "")
                    .email(dto.getEmail() != null ? dto.getEmail() : "")
                    .phone(dto.getPhone() != null ? dto.getPhone() : "")
                    .status(dto.getStatus() != null ? dto.getStatus() : 1)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
            updateUserRoles(user.getId(), dto.getRoleIds());
        } else {
            SysUser existing = userMapper.selectById(dto.getId());
            if (existing == null) {
                throw new BusinessException(404, "用户不存在");
            }

            String supervisorName = dto.getSupervisorName();
            if (!StringUtils.hasText(supervisorName) && dto.getSupervisorId() != null) {
                SysUser sup = userMapper.selectById(dto.getSupervisorId());
                if (sup != null) supervisorName = sup.getRealName();
            }
            String deptHeadName = dto.getDeptHeadName();
            if (!StringUtils.hasText(deptHeadName) && dto.getDeptHeadId() != null) {
                SysUser dh = userMapper.selectById(dto.getDeptHeadId());
                if (dh != null) deptHeadName = dh.getRealName();
            }

            existing.setRealName(dto.getRealName());
            existing.setDepartment(dto.getDepartment());
            existing.setPosition(dto.getPosition() != null ? dto.getPosition() : "");
            if (dto.getTitle() != null) {
                existing.setTitle(dto.getTitle());
            }
            if (dto.getPositionCategory() != null) {
                existing.setPositionCategory(dto.getPositionCategory());
            }
            existing.setSupervisorId(dto.getSupervisorId());
            existing.setSupervisorName(supervisorName != null ? supervisorName : "");
            existing.setDeptHeadId(dto.getDeptHeadId());
            existing.setDeptHeadName(deptHeadName != null ? deptHeadName : "");
            existing.setEmail(dto.getEmail() != null ? dto.getEmail() : "");
            existing.setPhone(dto.getPhone() != null ? dto.getPhone() : "");
            if (dto.getStatus() != null) {
                existing.setStatus(dto.getStatus());
            }
            if (StringUtils.hasText(dto.getPassword())) {
                existing.setPasswordHash(PasswordUtils.sha256(dto.getPassword()));
            }
            existing.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(existing);

            if (dto.getRoleIds() != null) {
                updateUserRoles(existing.getId(), dto.getRoleIds());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if ("sys_admin".equalsIgnoreCase(user.getUsername()) || "admin".equalsIgnoreCase(user.getUsername())) {
            throw new BusinessException(400, "系统预置核心管理员账号不可删除");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public List<RoleVO> listAllRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId))
                .stream()
                .map(r -> RoleVO.builder()
                        .id(r.getId())
                        .roleCode(r.getRoleCode())
                        .roleName(r.getRoleName())
                        .description(r.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<SysUser> listUsersByRoleCode(String roleCode) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode));
        if (role == null) {
            return Collections.emptyList();
        }
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, role.getId()));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = userRoles.stream().map(SysUserRole::getUserId).collect(Collectors.toList());
        return userMapper.selectBatchIds(userIds);
    }

    @Override
    public void changePassword(com.rawcenter.appraisal.dto.ChangePasswordDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(400, "两次输入的新密码不一致");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!PasswordUtils.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(400, "原密码输入错误");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }
        user.setPasswordHash(PasswordUtils.sha256(dto.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void resetPasswordByAdmin(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setPasswordHash(PasswordUtils.sha256("password123"));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private void updateUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (!CollectionUtils.isEmpty(roleIds)) {
            for (Long roleId : roleIds) {
                userRoleMapper.insert(SysUserRole.builder().userId(userId).roleId(roleId).build());
            }
        }
    }

    private List<RoleVO> getUserRoles(Long userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream()
                .map(r -> RoleVO.builder()
                        .id(r.getId())
                        .roleCode(r.getRoleCode())
                        .roleName(r.getRoleName())
                        .description(r.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    private UserVO toUserVO(SysUser user, List<RoleVO> roles) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .department(user.getDepartment())
                .position(user.getPosition())
                .title(user.getTitle())
                .positionCategory(user.getPositionCategory())
                .supervisorId(user.getSupervisorId())
                .supervisorName(user.getSupervisorName())
                .deptHeadId(user.getDeptHeadId())
                .deptHeadName(user.getDeptHeadName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
