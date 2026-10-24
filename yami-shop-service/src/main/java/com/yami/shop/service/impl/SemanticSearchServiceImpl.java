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

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yami.shop.bean.dto.SearchProdDto;
import com.yami.shop.bean.model.Product;
import com.yami.shop.common.util.Json;
import com.yami.shop.service.ProductService;
import com.yami.shop.service.SemanticSearchService;
import com.yami.shop.service.ranking.DeterministicFusionRanker;
import com.yami.shop.service.ranking.SemanticCandidate;
import com.yami.shop.service.ranking.SemanticRankingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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

    private static final String CACHE_KEY_PREFIX = "semantic:search:";

    private static final String LOCK_KEY_PREFIX = "semantic:search:lock:";

    private static final String EMPTY_CACHE_VALUE = "[]";

    private static final long CACHE_TTL_SECONDS = 3600L;

    private static final int CACHE_TTL_JITTER_SECONDS = 300;

    private static final long EMPTY_CACHE_TTL_SECONDS = 300L;

    private static final long LOCK_WAIT_SECONDS = 1L;

    private static final long LOCK_LEASE_SECONDS = 60L;

    private final ProductService productService;

    private final SemanticRankingProvider semanticRankingProvider;

    private final DeterministicFusionRanker deterministicFusionRanker;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    private final LongAdder aiCallTotal = new LongAdder();

    private final LongAdder aiSuccessTotal = new LongAdder();

    private final LongAdder aiFallbackTotal = new LongAdder();

    private final LongAdder cacheHitTotal = new LongAdder();

    private final LongAdder cacheMissTotal = new LongAdder();

    private final LongAdder cacheRebuildTotal = new LongAdder();

    private final LongAdder redisErrorTotal = new LongAdder();

    @Override
    public IPage<SearchProdDto> semanticSearch(String prodName, Integer sort, Integer orderBy) {
        Page<SearchProdDto> candidatePage = new Page<>(1, CANDIDATE_LIMIT);
        IPage<SearchProdDto> originalPage = productService.getSearchProdDtoPageByProdName(
                candidatePage, prodName, sort, orderBy
        );
        List<SearchProdDto> originalRecords = originalPage.getRecords();
        List<Long> candidateProdIds = extractProdIds(originalRecords);
        String cacheKey = buildCacheKey(prodName, sort, orderBy, candidateProdIds);

        long cacheStartNanos = System.nanoTime();
        CacheReadResult firstRead = readCache(cacheKey);
        if (firstRead.unavailable()) {
            logCacheMetrics("redis-unavailable", cacheStartNanos);
            return rebuildWithAi(originalPage, prodName, originalRecords, cacheKey, false);
        }

        if (firstRead.hit()) {
            cacheHitTotal.increment();
            logCacheMetrics("hit", cacheStartNanos);
            if (firstRead.prodIds().isEmpty()) {
                return originalPage;
            }
            List<SearchProdDto> cachedRecords = reorderRecords(originalRecords, firstRead.prodIds());
            if (!cachedRecords.isEmpty()) {
                originalPage.setRecords(cachedRecords);
            }
            return originalPage;
        }

        cacheMissTotal.increment();
        logCacheMetrics("miss", cacheStartNanos);
        if (originalRecords == null || originalRecords.isEmpty()) {
            writeCacheQuietly(cacheKey, List.of());
            return originalPage;
        }

        String lockKey = LOCK_KEY_PREFIX + DigestUtil.md5Hex(cacheKey);
        RLock lock = null;
        try {
            lock = redissonClient.getLock(lockKey);
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("Semantic search cache lock wait timeout, fallback to original MySQL result: key={}", cacheKey);
                return originalPage;
            }

            CacheReadResult secondRead = readCache(cacheKey);
            if (!secondRead.unavailable() && secondRead.hit()) {
                cacheHitTotal.increment();
                logCacheMetrics("hit-after-lock", cacheStartNanos);
                if (!secondRead.prodIds().isEmpty()) {
                    originalPage.setRecords(reorderRecords(originalRecords, secondRead.prodIds()));
                }
                return originalPage;
            }

            return rebuildWithAi(originalPage, prodName, originalRecords, cacheKey, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Semantic search cache lock interrupted, fallback to original MySQL result: key={}", cacheKey, e);
            return originalPage;
        } catch (Exception e) {
            redisErrorTotal.increment();
            log.warn("Semantic search cache lock failed, continue without cache: key={}", cacheKey, e);
            return rebuildWithAi(originalPage, prodName, originalRecords, cacheKey, false);
        } finally {
            unlockQuietly(lock, cacheKey);
        }
    }    private IPage<SearchProdDto> rebuildWithAi(
            IPage<SearchProdDto> originalPage,
            String prodName,
            List<SearchProdDto> originalRecords,
            String cacheKey,
            boolean writeCache) {
        cacheRebuildTotal.increment();
        aiCallTotal.increment();
        long startNanos = System.nanoTime();
        try {
            List<Long> prodIds = extractProdIds(originalRecords);
            Map<Long, Product> productMap = loadProductMap(prodIds);
            List<SemanticCandidate> candidates = originalRecords.stream()
                    .map(searchProd -> toSemanticCandidate(searchProd, productMap.get(searchProd.getProdId())))
                    .toList();

            List<Long> semanticOrder = semanticRankingProvider.rank(prodName, candidates);
            List<SemanticCandidate> fusedCandidates = deterministicFusionRanker.rank(candidates, semanticOrder);
            List<Long> fusedProdIds = fusedCandidates.stream()
                    .map(SemanticCandidate::getProdId)
                    .filter(Objects::nonNull)
                    .toList();
            List<SearchProdDto> reorderedRecords = reorderRecords(originalRecords, fusedProdIds);
            if (reorderedRecords.isEmpty()) {
                throw new IllegalStateException("融合排序结果为空");
            }

            originalPage.setRecords(reorderedRecords);
            aiSuccessTotal.increment();
            if (writeCache) {
                writeCacheQuietly(cacheKey, fusedProdIds);
            }
            logAiMetrics("success", startNanos, null);
            logCacheMetrics("rebuild", startNanos);
            return originalPage;
        } catch (Exception e) {
            aiFallbackTotal.increment();
            logAiMetrics("fallback", startNanos, e);
            return originalPage;
        }
    }

    private String buildCacheKey(String prodName, Integer sort, Integer orderBy, List<Long> prodIds) {
        String normalizedName = prodName == null ? "" : prodName.trim().toLowerCase(Locale.ROOT);
        String joinedProdIds = prodIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return CACHE_KEY_PREFIX + normalizedName + ":" + sort + ":" + orderBy + ":"
                + DigestUtil.md5Hex(joinedProdIds);
    }

    private CacheReadResult readCache(String cacheKey) {
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedValue == null) {
                return CacheReadResult.miss();
            }
            if (EMPTY_CACHE_VALUE.equals(cachedValue.trim())) {
                return CacheReadResult.hit(List.of());
            }
            Long[] prodIds = Json.getObjectMapper().readValue(cachedValue, Long[].class);
            if (prodIds == null) {
                return CacheReadResult.miss();
            }
            return CacheReadResult.hit(Arrays.asList(prodIds));
        } catch (Exception e) {
            redisErrorTotal.increment();
            log.warn("Semantic search cache read failed, skip cache: key={}", cacheKey, e);
            return CacheReadResult.redisUnavailable();
        }
    }

    private void writeCacheQuietly(String cacheKey, List<Long> prodIds) {
        try {
            String value = Json.toJsonString(prodIds);
            long ttl = prodIds.isEmpty()
                    ? EMPTY_CACHE_TTL_SECONDS
                    : CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextInt(CACHE_TTL_JITTER_SECONDS + 1);
            stringRedisTemplate.opsForValue().set(cacheKey, value, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            redisErrorTotal.increment();
            log.warn("Semantic search cache write failed, ignore cache: key={}", cacheKey, e);
        }
    }

    private void unlockQuietly(RLock lock, String cacheKey) {
        if (lock == null) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            redisErrorTotal.increment();
            log.warn("Semantic search cache unlock failed: key={}", cacheKey, e);
        }
    }    private List<Long> extractProdIds(List<SearchProdDto> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(SearchProdDto::getProdId)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<Long, Product> loadProductMap(List<Long> prodIds) {
        if (prodIds.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productService.list(
                Wrappers.<Product>query()
                        .select(
                                "prod_id", "price", "sold_num", "total_stocks",
                                "putaway_time", "create_time", "category_id", "shop_id"
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

    private List<SearchProdDto> reorderRecords(List<SearchProdDto> originalRecords, List<Long> orderedProdIds) {
        if (originalRecords == null || originalRecords.isEmpty()) {
            return List.of();
        }
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
        if (orderedProdIds != null) {
            for (Long prodId : orderedProdIds) {
                SearchProdDto record = recordMap.get(prodId);
                if (record != null && addedProdIds.add(prodId)) {
                    reorderedRecords.add(record);
                }
            }
        }
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

    private void logCacheMetrics(String status, long startNanos) {
        long hits = cacheHitTotal.sum();
        long misses = cacheMissTotal.sum();
        long rebuilds = cacheRebuildTotal.sum();
        long redisErrors = redisErrorTotal.sum();
        double total = hits + misses;
        double hitRate = total == 0 ? 0D : hits / total;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        log.info("Semantic search cache metrics: status={}, hits={}, misses={}, rebuilds={}, "
                        + "redisErrors={}, hitRate={}, elapsedMs={}",
                status, hits, misses, rebuilds, redisErrors, hitRate, elapsedMs);
    }

    private record CacheReadResult(boolean unavailable, boolean hit, List<Long> prodIds) {
        private static CacheReadResult miss() {
            return new CacheReadResult(false, false, List.of());
        }

        private static CacheReadResult redisUnavailable() {
            return new CacheReadResult(true, false, List.of());
        }

        private static CacheReadResult hit(List<Long> prodIds) {
            return new CacheReadResult(false, true, prodIds);
        }
    }
}