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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 确定性融合排序配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mall4j.ai.semantic-fusion")
public class SemanticFusionProperties {

    private double alpha = 0.60;

    private double beta = 0.15;

    private double gamma = 0.15;

    private double delta = 0.10;

    private double priceAnomalyRatio = 0.50;

    private int minCategorySamples = 3;
}