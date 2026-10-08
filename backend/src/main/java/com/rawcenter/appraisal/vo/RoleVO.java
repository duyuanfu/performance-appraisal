package com.rawcenter.appraisal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleVO {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
}
