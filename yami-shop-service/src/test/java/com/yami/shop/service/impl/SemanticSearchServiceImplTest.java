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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticSearchServiceImplTest {

    @Test
    void shouldUseAiOrderWhenAiCallSucceeds() {
        ProductService productService = mockProductService();
        SemanticRankingProvider provider = mock(SemanticRankingProvider.class);
        when(provider.rank(eq("手机"), any())).thenReturn(List.of(2L, 1L));
        SemanticSearchServiceImpl service = new SemanticSearchServiceImpl(productService, provider, ranker());

        IPage<SearchProdDto> result = service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(2L, 1L);
    }

    @Test
    void shouldFallbackWhenNoModelIsAvailable() {
        ProductService productService = mockProductService();
        SemanticRankingProvider provider = mock(SemanticRankingProvider.class);
        when(provider.rank(eq("手机"), any())).thenThrow(new IllegalStateException("未配置 ChatModel"));
        SemanticSearchServiceImpl service = new SemanticSearchServiceImpl(productService, provider, ranker());

        IPage<SearchProdDto> result = service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(1L, 2L);
    }

    @Test
    void shouldFallbackWhenAiThrowsException() {
        ProductService productService = mockProductService();
        SemanticRankingProvider provider = mock(SemanticRankingProvider.class);
        when(provider.rank(eq("手机"), any())).thenThrow(new RuntimeException("timeout"));
        SemanticSearchServiceImpl service = new SemanticSearchServiceImpl(productService, provider, ranker());

        IPage<SearchProdDto> result = service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(1L, 2L);
    }

    @Test
    void shouldFallbackWhenAiReturnsInvalidJson() {
        ProductService productService = mockProductService();
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("这不是 JSON 数组"))
        )));
        DeepSeekSemanticRankingProvider provider = new DeepSeekSemanticRankingProvider(chatModel);
        SemanticSearchServiceImpl service = new SemanticSearchServiceImpl(productService, provider, ranker());

        IPage<SearchProdDto> result = service.semanticSearch("手机", null, null);

        assertThat(result.getRecords()).extracting(SearchProdDto::getProdId).containsExactly(1L, 2L);
    }

    private ProductService mockProductService() {
        ProductService productService = mock(ProductService.class);
        Page<SearchProdDto> page = new Page<>(1, 50, 2);
        page.setRecords(List.of(searchProd(1L, "旧手机"), searchProd(2L, "新手机")));
        when(productService.getSearchProdDtoPageByProdName(any(Page.class), eq("手机"), eq(null), eq(null)))
                .thenReturn(page);
        doReturn(List.of(product(1L), product(2L))).when(productService).list(any(Wrapper.class));
        return productService;
    }

    private DeterministicFusionRanker ranker() {
        SemanticFusionProperties properties = new SemanticFusionProperties();
        properties.setAlpha(1D);
        properties.setBeta(0D);
        properties.setGamma(0D);
        properties.setDelta(0D);
        return new DeterministicFusionRanker(properties);
    }

    private SearchProdDto searchProd(Long prodId, String prodName) {
        SearchProdDto dto = new SearchProdDto();
        dto.setProdId(prodId);
        dto.setProdName(prodName);
        return dto;
    }

    private Product product(Long prodId) {
        Product product = new Product();
        product.setProdId(prodId);
        product.setPrice(100D);
        product.setSoldNum(10);
        product.setTotalStocks(5);
        product.setCategoryId(1L);
        return product;
    }
}