package com.rawcenter.appraisal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectItemSaveDTO {
    private Long id;

    @NotNull(message = "批次ID不能为空")
    private Long batchId;

    @NotBlank(message = "项目序号/编号不能为空")
    private String projectCode;

    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    @NotBlank(message = "部门名称不能为空")
    private String department; // 部门名称：仅用于汇总排名展示，与系统用户部门无关

    private String category; // BUSINESS, MANAGEMENT
    private Integer sortOrder;
}
