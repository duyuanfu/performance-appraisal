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
        userService.saveUser(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserSaveDTO dto) {
        dto.setId(id);
        userService.saveUser(dto);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateUserStatus(id, status);
        return Result.success();
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPasswordByAdmin(id);
        return Result.success();
    }
}
