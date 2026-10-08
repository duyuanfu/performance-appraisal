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

    @NotBlank(message = "项目负责人不能为空")
    private String ownerName;

    private Integer sortOrder;
}
