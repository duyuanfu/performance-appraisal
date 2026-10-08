package com.rawcenter.appraisal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("monthly_score_record")
public class MonthlyScoreRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long leaderUserId;
    private Long examineeUserId;
    private BigDecimal totalScore;
    private String status; // DRAFT, SUBMITTED
    private LocalDateTime submittedAt;
}
