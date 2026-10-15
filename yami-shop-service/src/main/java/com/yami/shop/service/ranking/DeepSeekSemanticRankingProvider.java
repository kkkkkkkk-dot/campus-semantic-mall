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

import com.yami.shop.common.util.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用 DeepSeek 对商品进行纯语义排序。
 */
@Slf4j
@Component
public class DeepSeekSemanticRankingProvider implements SemanticRankingProvider {

    private static final String SYSTEM_PROMPT = """
            你是电商商品搜索排序助手。请根据用户搜索词，对候选商品按照语义相关性从高到低排序。
            只返回一个 JSON 数字数组，数组元素是候选商品的 prodId，按相关性从高到低排列。
            必须包含全部候选商品 ID，每个 ID 只能出现一次。
            不要返回 Markdown、代码块、解释或任何其他文字。
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;

    private final ChatModel fixedChatModel;

    @Autowired
    public DeepSeekSemanticRankingProvider(
            @Qualifier("deepSeekChatModel") ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
        this.fixedChatModel = null;
    }

    public DeepSeekSemanticRankingProvider(ChatModel fixedChatModel) {
        this.chatModelProvider = null;
        this.fixedChatModel = fixedChatModel;
    }

    @Override
    public List<Long> rank(String query, List<SemanticCandidate> candidates) {
        ChatModel chatModel = resolveChatModel();
        if (chatModel == null) {
            throw new IllegalStateException("未配置可用的 DeepSeek ChatModel");
        }

        Prompt prompt = buildPrompt(query, candidates);
        log.info("DeepSeek semantic rank call start: query={}, candidateCount={}, prompt={}",
                query, candidates.size(), prompt.getContents());

        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (Exception e) {
            log.error("DeepSeek semantic rank call failed: query={}, prompt={}", query, prompt.getContents(), e);
            throw e;
        }

        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null
                || !StringUtils.hasText(response.getResult().getOutput().getText())) {
            log.error("DeepSeek semantic rank invalid response: query={}, response={}", query, response);
            throw new IllegalStateException("DeepSeek 未返回有效的语义排序结果");
        }

        String rawResponse = response.getResult().getOutput().getText();
        log.info("DeepSeek semantic rank raw response: query={}, rawResponse={}", query, rawResponse);
        List<Long> orderedProdIds = parseOrderedProdIds(rawResponse);
        log.info("DeepSeek semantic rank parsed order: query={}, orderedProdIds={}", query, orderedProdIds);
        return orderedProdIds;
    }

    private ChatModel resolveChatModel() {
        if (fixedChatModel != null) {
            return fixedChatModel;
        }
        return chatModelProvider == null ? null : chatModelProvider.getIfAvailable();
    }

    private Prompt buildPrompt(String query, List<SemanticCandidate> candidates) {
        List<Map<String, Object>> promptCandidates = new ArrayList<>(candidates.size());
        for (SemanticCandidate candidate : candidates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("prodId", candidate.getProdId());
            item.put("prodName", candidate.getProdName());
            promptCandidates.add(item);
        }

        String userPrompt = """
                用户搜索词：%s
                候选商品：%s
                请返回按语义相关性排序后的 prodId JSON 数组。
                """.formatted(query, Json.toJsonString(promptCandidates));

        return new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(userPrompt)
        ));
    }

    private List<Long> parseOrderedProdIds(String aiResult) {
        int start = aiResult.indexOf('[');
        int end = aiResult.lastIndexOf(']');
        if (start < 0 || end <= start) {
            log.error("DeepSeek semantic rank parse failed: no JSON array, rawResponse={}", aiResult);
            throw new IllegalArgumentException("DeepSeek 返回内容中不存在 JSON 数组");
        }

        try {
            Long[] prodIds = Json.getObjectMapper().readValue(
                    aiResult.substring(start, end + 1),
                    Long[].class
            );
            if (prodIds == null || prodIds.length == 0) {
                log.error("DeepSeek semantic rank parse failed: empty JSON array, rawResponse={}", aiResult);
                throw new IllegalArgumentException("DeepSeek 返回的语义排序结果为空");
            }
            return Arrays.asList(prodIds);
        } catch (Exception e) {
            log.error("DeepSeek semantic rank parse failed: rawResponse={}", aiResult, e);
            throw e;
        }
    }
}