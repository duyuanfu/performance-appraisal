package com.rawcenter.appraisal.controller;

import com.rawcenter.appraisal.common.Result;
import com.rawcenter.appraisal.dto.UserSaveDTO;
import com.rawcenter.appraisal.service.UserService;
import com.rawcenter.appraisal.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<List<UserVO>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String department) {
        return Result.success(userService.listUsers(keyword, roleCode, department));
    }

    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @PostMapping
    public Result<Void> createUser(@Valid @RequestBody UserSaveDTO dto) {
        checkSysAdmin();
        userService.saveUser(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserSaveDTO dto) {
        checkSysAdmin();
        dto.setId(id);
        userService.saveUser(dto);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        checkSysAdmin();
        userService.updateUserStatus(id, status);
        return Result.success();
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        checkSysAdmin();
        userService.resetPasswordByAdmin(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        checkSysAdmin();
        userService.deleteUser(id);
        return Result.success();
    }

    private void checkSysAdmin() {
        com.rawcenter.appraisal.common.UserContext.CurrentUser u = com.rawcenter.appraisal.common.UserContext.get();
        if (u != null && !"SYS_ADMIN".equalsIgnoreCase(u.getActiveRole()) && !"sys_admin".equalsIgnoreCase(u.getUsername())) {
            // Also check if user holds SYS_ADMIN role
            com.rawcenter.appraisal.vo.UserVO userVO = userService.getUserById(u.getUserId());
            boolean isSysAdmin = userVO.getRoles() != null && userVO.getRoles().stream()
                    .anyMatch(r -> "SYS_ADMIN".equalsIgnoreCase(r.getRoleCode()));
            if (!isSysAdmin) {
                throw new com.rawcenter.appraisal.common.BusinessException(403, "权限不足：账号管理与删除功能仅限【系统管理员】操作");
            }
        }
    }
}
