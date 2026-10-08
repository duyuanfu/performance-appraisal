package com.rawcenter.appraisal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamineePersonalResultVO {
    private Long batchId;
    private String batchName;
    private String appraisalPeriod;
    private BigDecimal myCompositeScore;
    private Integer myRank;
    private Integer totalExaminees;
    private List<RankItemVO> rankList;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankItemVO {
        private Integer rank;
        private Long examineeUserId;
        private String realName;
        private String department;
        private BigDecimal compositeScore;
        private Boolean isMe;
    }
}
