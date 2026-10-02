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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yami.shop.bean.dto.SearchProdDto;
import com.yami.shop.common.util.Json;
import com.yami.shop.service.ProductService;
import com.yami.shop.service.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 语义排序搜索服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchServiceImpl implements SemanticSearchService {

    private static final int CANDIDATE_LIMIT = 50;

    private static final String RANKING_SYSTEM_PROMPT = """
            你是电商商品搜索排序助手。请根据用户搜索词，对候选商品按照语义相关性从高到低排序。
            只返回一个 JSON 数字数组，数组元素是候选商品的 prodId，按相关性从高到低排列。
            必须包含全部候选商品 ID，每个 ID 只能出现一次。
            不要返回 Markdown、代码块、解释或任何其他文字。
            """;

    private final ProductService productService;

    private final ObjectProvider<ChatModel> chatModelProvider;

    @Override
    public IPage<SearchProdDto> semanticSearch(String prodName, Integer sort, Integer orderBy) {
        Page<SearchProdDto> candidatePage = new Page<>(1, CANDIDATE_LIMIT);
        IPage<SearchProdDto> originalPage = productService.getSearchProdDtoPageByProdName(
                candidatePage, prodName, sort, orderBy
        );

        List<SearchProdDto> candidates = originalPage.getRecords();
        if (candidates == null || candidates.isEmpty()) {
            return originalPage;
        }

        try {
            ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                log.warn("AI 语义排序跳过：未配置可用的 ChatModel，返回原始 MySQL 查询结果");
                return originalPage;
            }

            String aiResult = callRankingModel(chatModel, prodName, candidates);
            List<Long> orderedProdIds = parseOrderedProdIds(aiResult);
            originalPage.setRecords(reorderProducts(candidates, orderedProdIds));
            return originalPage;
        } catch (Exception e) {
            log.warn("AI 语义排序失败，降级返回原始 MySQL 查询结果", e);
            return originalPage;
        }
    }

    private String callRankingModel(ChatModel chatModel, String prodName, List<SearchProdDto> candidates) {
        Prompt prompt = buildRankingPrompt(prodName, candidates);
        ChatResponse response = chatModel.call(prompt);
        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null
                || !StringUtils.hasText(response.getResult().getOutput().getText())) {
            throw new IllegalStateException("DeepSeek 未返回有效的语义排序结果");
        }
        return response.getResult().getOutput().getText();
    }

    private Prompt buildRankingPrompt(String prodName, List<SearchProdDto> candidates) {
        List<Map<String, Object>> candidateData = new ArrayList<>(candidates.size());
        for (SearchProdDto candidate : candidates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("prodId", candidate.getProdId());
            item.put("prodName", candidate.getProdName());
            item.put("price", candidate.getPrice());
            item.put("prodCommNumber", candidate.getProdCommNumber());
            item.put("positiveRating", candidate.getPositiveRating());
            candidateData.add(item);
        }

        String userPrompt = """
                用户搜索词：%s
                候选商品：%s
                请返回按语义相关性排序后的 prodId JSON 数组。
                """.formatted(prodName, Json.toJsonString(candidateData));

        return new Prompt(List.of(
                new SystemMessage(RANKING_SYSTEM_PROMPT),
                new UserMessage(userPrompt)
        ));
    }

    private List<Long> parseOrderedProdIds(String aiResult) throws Exception {
        int start = aiResult.indexOf('[');
        int end = aiResult.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("DeepSeek 返回内容中不存在 JSON 数组");
        }

        Long[] prodIds = Json.getObjectMapper().readValue(
                aiResult.substring(start, end + 1),
                Long[].class
        );
        if (prodIds == null || prodIds.length == 0) {
            throw new IllegalArgumentException("DeepSeek 返回的语义排序结果为空");
        }
        return Arrays.asList(prodIds);
    }

    private List<SearchProdDto> reorderProducts(List<SearchProdDto> candidates, List<Long> orderedProdIds) {
        Map<Long, SearchProdDto> candidateMap = new LinkedHashMap<>();
        for (SearchProdDto candidate : candidates) {
            if (candidate.getProdId() != null) {
                candidateMap.putIfAbsent(candidate.getProdId(), candidate);
            }
        }

        Set<Long> addedProdIds = new HashSet<>();
        List<SearchProdDto> sortedProducts = new ArrayList<>(candidates.size());
        for (Long prodId : orderedProdIds) {
            SearchProdDto candidate = candidateMap.get(prodId);
            if (candidate != null && addedProdIds.add(prodId)) {
                sortedProducts.add(candidate);
            }
        }

        // AI 漏返回或返回无效 ID 时，按 MySQL 原始顺序补回，避免商品丢失。
        for (SearchProdDto candidate : candidates) {
            if (candidate.getProdId() == null || addedProdIds.add(candidate.getProdId())) {
                sortedProducts.add(candidate);
            }
        }
        return sortedProducts;
    }
}