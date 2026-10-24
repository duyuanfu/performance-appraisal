package com.rawcenter.appraisal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserSaveDTO {
    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password; // optional on update, defaults to password123 on create

    @NotBlank(message = "姓名不能为空")
    private String realName;

    @NotBlank(message = "部门不能为空")
    private String department;

    private String position;
    private String title;
    private String positionCategory;
    private Long supervisorId;
    private String supervisorName;
    private Long deptHeadId;
    private String deptHeadName;
    private String email;
    private String phone;
    private Integer status; // 1: active, 0: disabled
    private List<Long> roleIds;
}
