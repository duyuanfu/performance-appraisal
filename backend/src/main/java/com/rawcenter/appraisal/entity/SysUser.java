package com.rawcenter.appraisal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private String department;
    private String position;
    private String title; // 职务（中心主任、中心副主任、部长、副部长等）
    private String positionCategory; // PRIMARY_LEADER, CENTER_LEADER, DEPT_HEAD, DEPT_DEPUTY, EXPERT, EMPLOYEE
    private Long supervisorId; // 分管领导用户ID
    private String supervisorName; // 分管领导姓名
    private Long deptHeadId; // 直属部门正职领导ID
    private String deptHeadName; // 直属部门正职姓名
    private String email;
    private String phone;
    private Integer status; // 1: active, 0: disabled
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
