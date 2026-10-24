package com.rawcenter.appraisal.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    private Long id;
    private String username;
    private String realName;
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
    private Integer status;
    private List<RoleVO> roles;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
