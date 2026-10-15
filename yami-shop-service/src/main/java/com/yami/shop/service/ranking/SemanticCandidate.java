/*
 * Copyright (c) 2018-2999 广州市蓝海创新科技有限公司 All rights reserved.
 *
 * https://www.mall4j.com/
 *
 * 未经允许，不可做商业用途！
 *
 * 版权所有，侵权必究！
 */

package com.yami.shop.service.ranking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 语义搜索候选商品及其确定性重排所需的业务字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanticCandidate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long prodId;

    private String prodName;

    private Double price;

    private Integer soldNum;

    private Integer totalStocks;

    private Date putawayTime;

    private Date createTime;

    private Long categoryId;

    private Long shopId;
}