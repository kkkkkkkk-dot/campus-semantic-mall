/*
 * Copyright (c) 2018-2999 广州市蓝海创新科技有限公司 All rights reserved.
 *
 * https://www.mall4j.com/
 *
 * 未经允许，不可做商业用途！
 *
 * 版权所有，侵权必究！
 */

package com.yami.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yami.shop.bean.dto.SearchProdDto;

/**
 * AI 语义排序搜索服务。
 */
public interface SemanticSearchService {

    /**
     * 使用 MySQL 召回候选商品，再通过 AI 对候选商品进行语义排序。
     * AI 不可用时返回原始 MySQL 查询结果。
     *
     * @param prodName 搜索词
     * @param sort MySQL 候选召回排序方式
     * @param orderBy MySQL 候选召回升降序
     * @return 语义排序后的分页结果
     */
    IPage<SearchProdDto> semanticSearch(String prodName, Integer sort, Integer orderBy);
}