package com.rawcenter.appraisal.controller;

import com.rawcenter.appraisal.common.Result;
import com.rawcenter.appraisal.dto.LoginRequest;
import com.rawcenter.appraisal.service.UserService;
import com.rawcenter.appraisal.vo.LoginResponse;
import com.rawcenter.appraisal.vo.RoleVO;
import com.rawcenter.appraisal.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        return Result.success(userService.getCurrentUser());
    }

    @GetMapping("/roles")
    public Result<List<RoleVO>> listAllRoles() {
        return Result.success(userService.listAllRoles());
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody com.rawcenter.appraisal.dto.ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return Result.success();
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }
}
