package com.yami.shop.evaluation;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EvaluationCandidate {
    private Long prodId;
    private String prodName;
    private Double price;
    private Integer soldNum;
    private Integer totalStocks;
    private String putawayTime;
    private Long categoryId;
    private Long shopId;
}