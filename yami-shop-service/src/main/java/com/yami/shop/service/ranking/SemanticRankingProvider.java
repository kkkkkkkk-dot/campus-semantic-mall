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

import java.util.List;

/**
 * 商品语义排序提供方。
 */
public interface SemanticRankingProvider {

    /**
     * 根据搜索词对候选商品进行语义排序。
     *
     * @param query 搜索词
     * @param candidates 候选商品
     * @return 按语义相关度排序的商品 ID
     */
    List<Long> rank(String query, List<SemanticCandidate> candidates);
}