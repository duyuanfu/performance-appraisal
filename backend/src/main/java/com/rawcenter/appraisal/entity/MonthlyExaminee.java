package com.rawcenter.appraisal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("monthly_examinee")
public class MonthlyExaminee {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long examineeUserId;
    private String department;
    private String position;
}
