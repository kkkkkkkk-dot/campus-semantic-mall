/*
 * Copyright (c) 2018-2999 广州市蓝海创新科技有限公司 All rights reserved.
 *
 * https://www.mall4j.com/
 *
 * 未经允许，不可做商业用途！
 *
 * 版权所有，侵权必究！
 */

package com.yami.shop.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yami.shop.bean.dto.SearchProdDto;
import com.yami.shop.bean.model.Product;
import com.yami.shop.service.ProductService;
import com.yami.shop.service.SemanticSearchService;
import com.yami.shop.service.ranking.DeterministicFusionRanker;
import com.yami.shop.service.ranking.SemanticCandidate;
import com.yami.shop.service.ranking.SemanticRankingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 语义排序搜索服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchServiceImpl implements SemanticSearchService {

    private static final int CANDIDATE_LIMIT = 50;

    private final ProductService productService;

    private final SemanticRankingProvider semanticRankingProvider;

    private final DeterministicFusionRanker deterministicFusionRanker;

    private final LongAdder aiCallTotal = new LongAdder();

    private final LongAdder aiSuccessTotal = new LongAdder();

    private final LongAdder aiFallbackTotal = new LongAdder();

    @Override
    public IPage<SearchProdDto> semanticSearch(String prodName, Integer sort, Integer orderBy) {
        Page<SearchProdDto> candidatePage = new Page<>(1, CANDIDATE_LIMIT);
        IPage<SearchProdDto> originalPage = productService.getSearchProdDtoPageByProdName(
                candidatePage, prodName, sort, orderBy
        );
        List<SearchProdDto> originalRecords = originalPage.getRecords();
        if (originalRecords == null || originalRecords.isEmpty()) {
            return originalPage;
        }

        Map<Long, Product> productMap;
        List<SemanticCandidate> candidates;
        try {
            List<Long> prodIds = originalRecords.stream()
                    .map(SearchProdDto::getProdId)
                    .filter(prodId -> prodId != null)
                    .distinct()
                    .toList();
            productMap = loadProductMap(prodIds);
            candidates = originalRecords.stream()
                    .map(searchProd -> toSemanticCandidate(searchProd, productMap.get(searchProd.getProdId())))
                    .toList();
        } catch (Exception e) {
            log.warn("语义搜索候选业务字段加载失败，降级返回原始 MySQL 查询结果", e);
            return originalPage;
        }

        aiCallTotal.increment();
        long startNanos = System.nanoTime();
        try {
            List<Long> semanticOrder = semanticRankingProvider.rank(prodName, candidates);
            List<SemanticCandidate> fusedCandidates = deterministicFusionRanker.rank(candidates, semanticOrder);
            List<SearchProdDto> reorderedRecords = reorderRecords(originalRecords, fusedCandidates);
            if (reorderedRecords.isEmpty()) {
                throw new IllegalStateException("融合排序结果为空");
            }

            originalPage.setRecords(reorderedRecords);
            aiSuccessTotal.increment();
            logAiMetrics("success", startNanos, null);
            return originalPage;
        } catch (Exception e) {
            aiFallbackTotal.increment();
            logAiMetrics("fallback", startNanos, e);
            return originalPage;
        }
    }    private Map<Long, Product> loadProductMap(List<Long> prodIds) {
        if (prodIds.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productService.list(
                Wrappers.<Product>query()
                        .select(
                                "prod_id",
                                "price",
                                "sold_num",
                                "total_stocks",
                                "putaway_time",
                                "create_time",
                                "category_id",
                                "shop_id"
                        )
                        .in("prod_id", prodIds)
        );
        return products.stream().collect(Collectors.toMap(
                Product::getProdId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private SemanticCandidate toSemanticCandidate(SearchProdDto searchProd, Product product) {
        SemanticCandidate candidate = new SemanticCandidate();
        candidate.setProdId(searchProd.getProdId());
        candidate.setProdName(searchProd.getProdName());
        candidate.setPrice(product != null && product.getPrice() != null
                ? product.getPrice() : searchProd.getPrice());
        if (product != null) {
            candidate.setSoldNum(product.getSoldNum());
            candidate.setTotalStocks(product.getTotalStocks());
            candidate.setPutawayTime(product.getPutawayTime());
            candidate.setCreateTime(product.getCreateTime());
            candidate.setCategoryId(product.getCategoryId());
            candidate.setShopId(product.getShopId());
        }
        return candidate;
    }

    private List<SearchProdDto> reorderRecords(
            List<SearchProdDto> originalRecords,
            List<SemanticCandidate> fusedCandidates) {
        Map<Long, SearchProdDto> recordMap = originalRecords.stream()
                .filter(record -> record.getProdId() != null)
                .collect(Collectors.toMap(
                        SearchProdDto::getProdId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<Long> addedProdIds = new HashSet<>();
        List<SearchProdDto> reorderedRecords = new ArrayList<>(originalRecords.size());
        for (SemanticCandidate candidate : fusedCandidates) {
            SearchProdDto record = recordMap.get(candidate.getProdId());
            if (record != null && addedProdIds.add(candidate.getProdId())) {
                reorderedRecords.add(record);
            }
        }

        // 防御性补回，确保融合排序异常时也不会在成功分支中静默丢商品。
        for (SearchProdDto record : originalRecords) {
            if (record.getProdId() == null || addedProdIds.add(record.getProdId())) {
                reorderedRecords.add(record);
            }
        }
        return reorderedRecords;
    }

    private void logAiMetrics(String status, long startNanos, Exception exception) {
        long total = aiCallTotal.sum();
        long success = aiSuccessTotal.sum();
        long fallback = aiFallbackTotal.sum();
        double successRate = total == 0 ? 0D : (double) success / total;
        double fallbackRate = total == 0 ? 0D : (double) fallback / total;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        if (exception == null) {
            log.info("AI semantic search metrics: status={}, total={}, success={}, fallback={}, "
                            + "successRate={}, fallbackRate={}, elapsedMs={}",
                    status, total, success, fallback, successRate, fallbackRate, elapsedMs);
        } else {
            log.warn("AI semantic search metrics: status={}, total={}, success={}, fallback={}, "
                            + "successRate={}, fallbackRate={}, elapsedMs={}",
                    status, total, success, fallback, successRate, fallbackRate, elapsedMs, exception);
        }
    }
}