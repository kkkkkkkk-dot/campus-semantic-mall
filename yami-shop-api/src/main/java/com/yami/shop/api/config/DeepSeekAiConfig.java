package com.yami.shop.api.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnExpression("'${mall4j.ai.deepseek.api-key:}' != ''")
public class DeepSeekAiConfig {

    @Bean(name = "deepSeekChatModel")
    public ChatModel deepSeekChatModel(
            @Value("${mall4j.ai.deepseek.api-key}") String apiKey,
            @Value("${mall4j.ai.deepseek.base-url:https://api.deepseek.com/v1}") String baseUrl,
            @Value("${mall4j.ai.deepseek.model:deepseek-v4-flash}") String model,
            @Value("${mall4j.ai.deepseek.temperature:0.0}") Double temperature,
            @Value("${mall4j.ai.deepseek.max-tokens:800}") Integer maxTokens,
            @Value("${mall4j.ai.deepseek.timeout:15s}") Duration timeout,
            @Value("${mall4j.ai.deepseek.max-retries:2}") Integer maxRetries) {

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();

        return OpenAiChatModel.builder()
                .options(options)
                .build();
    }
}