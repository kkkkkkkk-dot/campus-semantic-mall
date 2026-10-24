package com.yami.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yami.shop.bean.dto.SearchProdDto;
import com.yami.shop.bean.model.Product;
import com.yami.shop.service.ProductService;
import com.yami.shop.service.ranking.DeterministicFusionRanker;
import com.yami.shop.service.ranking.DeepSeekSemanticRankingProvider;
import com.yami.shop.service.ranking.SemanticFusionProperties;
import com.yami.shop.service.ranking.SemanticRankingProvider;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticSearchServiceImplTest {

    @Test
    void shouldUseCachedOrderAndSkipAiWhenCacheHit() throws Exception {
        Harness harness = new Harness();
        when(harness.valueOperations.get(anyString())).thenReturn("[2,1]");

        IPage<SearchProdDto> result = harness.service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(2L, 1L);
        verify(harness.rankingProvider, never()).rank(anyString(), any());
    }

    @Test
    void shouldReturnOriginalResultWhenEmptyMarkerIsCached() throws Exception {
        Harness harness = new Harness();
        when(harness.valueOperations.get(anyString())).thenReturn("[]");

        IPage<SearchProdDto> result = harness.service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(1L, 2L);
        verify(harness.rankingProvider, never()).rank(anyString(), any());
    }

    @Test
    void shouldFallbackToOriginalWhenLockCannotBeAcquired() throws Exception {
        Harness harness = new Harness();
        when(harness.valueOperations.get(anyString())).thenReturn(null);
        when(harness.lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        IPage<SearchProdDto> result = harness.service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(1L, 2L);
        verify(harness.rankingProvider, never()).rank(anyString(), any());
    }

    @Test
    void shouldContinueWithAiWhenRedisReadThrows() throws Exception {
        Harness harness = new Harness();
        when(harness.valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(harness.rankingProvider.rank(eq("手机"), any())).thenReturn(List.of(2L, 1L));

        IPage<SearchProdDto> result = harness.service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(2L, 1L);
        verify(harness.redissonClient, never()).getLock(anyString());
    }

    @Test
    void shouldReturnFusedResultWhenCacheWriteThrows() throws Exception {
        Harness harness = new Harness();
        when(harness.valueOperations.get(anyString())).thenReturn(null);
        when(harness.rankingProvider.rank(eq("手机"), any())).thenReturn(List.of(2L, 1L));
        doThrow(new RuntimeException("redis write down"))
                .when(harness.valueOperations)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        IPage<SearchProdDto> result = harness.service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(2L, 1L);
        verify(harness.valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldUseAiOrderWhenAiCallSucceeds() throws Exception {
        Harness harness = new Harness();
        when(harness.rankingProvider.rank(eq("手机"), any())).thenReturn(List.of(2L, 1L));

        IPage<SearchProdDto> result = harness.service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(2L, 1L);
    }    @Test
    void shouldFallbackWhenNoModelIsAvailable() throws Exception {
        Harness harness = new Harness();
        when(harness.rankingProvider.rank(eq("手机"), any()))
                .thenThrow(new IllegalStateException("未配置 ChatModel"));

        IPage<SearchProdDto> result = harness.service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(1L, 2L);
    }

    @Test
    void shouldFallbackWhenAiThrowsException() throws Exception {
        Harness harness = new Harness();
        when(harness.rankingProvider.rank(eq("手机"), any()))
                .thenThrow(new RuntimeException("timeout"));

        IPage<SearchProdDto> result = harness.service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(1L, 2L);
    }

    @Test
    void shouldFallbackWhenAiReturnsInvalidJson() throws Exception {
        Harness harness = new Harness();
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("这不是 JSON 数组"))
        )));
        DeepSeekSemanticRankingProvider realProvider = new DeepSeekSemanticRankingProvider(chatModel);
        SemanticSearchServiceImpl service = new SemanticSearchServiceImpl(
                harness.productService,
                realProvider,
                harness.ranker,
                harness.redisTemplate,
                harness.redissonClient
        );

        IPage<SearchProdDto> result = service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(1L, 2L);
    }

    private static class Harness {

        private final ProductService productService = mock(ProductService.class);

        private final SemanticRankingProvider rankingProvider = mock(SemanticRankingProvider.class);

        private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        @SuppressWarnings("unchecked")
        private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        private final RedissonClient redissonClient = mock(RedissonClient.class);

        private final RLock lock = mock(RLock.class);

        private final DeterministicFusionRanker ranker = ranker();

        private final SemanticSearchServiceImpl service;

        private Harness() throws InterruptedException {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(lock.isHeldByCurrentThread()).thenReturn(true);

            Page<SearchProdDto> page = new Page<>(1, 50, 2);
            page.setRecords(List.of(searchProd(1L, "旧手机"), searchProd(2L, "新手机")));
            when(productService.getSearchProdDtoPageByProdName(any(Page.class), eq("手机"), eq(null), eq(null)))
                    .thenReturn(page);
            doReturn(List.of(product(1L), product(2L))).when(productService).list(any(Wrapper.class));

            service = new SemanticSearchServiceImpl(
                    productService, rankingProvider, ranker, redisTemplate, redissonClient
            );
        }
    }

    private static DeterministicFusionRanker ranker() {
        SemanticFusionProperties properties = new SemanticFusionProperties();
        properties.setAlpha(1D);
        properties.setBeta(0D);
        properties.setGamma(0D);
        properties.setDelta(0D);
        return new DeterministicFusionRanker(properties);
    }

    private static SearchProdDto searchProd(Long prodId, String prodName) {
        SearchProdDto dto = new SearchProdDto();
        dto.setProdId(prodId);
        dto.setProdName(prodName);
        return dto;
    }

    private static Product product(Long prodId) {
        Product product = new Product();
        product.setProdId(prodId);
        product.setPrice(100D);
        product.setSoldNum(10);
        product.setTotalStocks(5);
        product.setCategoryId(1L);
        return product;
    }
}