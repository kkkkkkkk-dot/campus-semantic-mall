package com.yami.shop.service.ranking;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicFusionRankerTest {

    @Test
    void semanticScoreShouldUseLinearRankBoundaries() {
        SemanticFusionProperties properties = pureProperties();
        DeterministicFusionRanker ranker = new DeterministicFusionRanker(properties);
        SemanticCandidate first = candidate(1L, 100D, 10, 10, date(1), 10L);
        SemanticCandidate second = candidate(2L, 100D, 10, 10, date(1), 10L);

        List<DeterministicFusionRanker.ScoredCandidate> result = ranker.rankWithScores(
                List.of(first, second),
                List.of(2L, 1L)
        );

        assertThat(result).extracting(item -> item.getCandidate().getProdId())
                .containsExactly(2L, 1L);
        assertThat(result.get(0).getSemanticScore()).isEqualTo(1D);
        assertThat(result.get(1).getSemanticScore()).isEqualTo(0D);
    }

    @Test
    void pricePenaltyShouldPushAbnormallyCheapProductDown() {
        SemanticFusionProperties properties = new SemanticFusionProperties();
        properties.setAlpha(0.4D);
        properties.setBeta(0.6D);
        properties.setGamma(0D);
        properties.setDelta(0D);
        properties.setMinCategorySamples(2);
        DeterministicFusionRanker ranker = new DeterministicFusionRanker(properties);
        SemanticCandidate cheap = candidate(1L, 1D, 100, 10, date(1), 10L);
        SemanticCandidate normal = candidate(2L, 100D, 100, 10, date(1), 10L);

        List<SemanticCandidate> result = ranker.rank(List.of(cheap, normal), List.of(1L, 2L));

        assertThat(result).extracting(SemanticCandidate::getProdId).containsExactly(2L, 1L);
    }

    @Test
    void salesAndTimeScoresShouldRespectNormalization() {
        SemanticFusionProperties salesProperties = new SemanticFusionProperties();
        salesProperties.setAlpha(0D);
        salesProperties.setBeta(0D);
        salesProperties.setGamma(1D);
        salesProperties.setDelta(0D);
        DeterministicFusionRanker salesRanker = new DeterministicFusionRanker(salesProperties);
        SemanticCandidate lowSales = candidate(1L, 100D, 0, 1, date(1), 10L);
        SemanticCandidate midSales = candidate(2L, 100D, 9, 1, date(1), 10L);
        SemanticCandidate highSales = candidate(3L, 100D, 99, 1, date(1), 10L);

        assertThat(salesRanker.rank(List.of(lowSales, highSales, midSales), List.of(1L, 2L, 3L)))
                .extracting(SemanticCandidate::getProdId)
                .containsExactly(3L, 2L, 1L);

        SemanticFusionProperties timeProperties = new SemanticFusionProperties();
        timeProperties.setAlpha(0D);
        timeProperties.setBeta(0D);
        timeProperties.setGamma(0D);
        timeProperties.setDelta(1D);
        DeterministicFusionRanker timeRanker = new DeterministicFusionRanker(timeProperties);
        SemanticCandidate oldProduct = candidate(1L, 100D, 1, 1, date(1), 10L);
        SemanticCandidate newProduct = candidate(2L, 100D, 1, 1, date(3), 10L);

        assertThat(timeRanker.rank(List.of(oldProduct, newProduct), List.of(1L, 2L)))
                .extracting(SemanticCandidate::getProdId)
                .containsExactly(2L, 1L);
    }

    @Test
    void equalScoresShouldUseStockAndProdIdAsStableTieBreakers() {
        SemanticFusionProperties properties = pureProperties();
        DeterministicFusionRanker ranker = new DeterministicFusionRanker(properties);
        SemanticCandidate outOfStock = candidate(1L, 100D, 10, 0, date(1), 10L);
        SemanticCandidate inStock = candidate(2L, 100D, 10, 5, date(1), 10L);

        assertThat(ranker.rank(List.of(outOfStock, inStock), List.of()))
                .extracting(SemanticCandidate::getProdId)
                .containsExactly(2L, 1L);

        SemanticCandidate lowerId = candidate(1L, 100D, 10, 5, date(1), 10L);
        SemanticCandidate higherId = candidate(2L, 100D, 10, 5, date(1), 10L);
        assertThat(ranker.rank(List.of(higherId, lowerId), List.of()))
                .extracting(SemanticCandidate::getProdId)
                .containsExactly(1L, 2L);
    }

    private SemanticFusionProperties pureProperties() {
        SemanticFusionProperties properties = new SemanticFusionProperties();
        properties.setAlpha(1D);
        properties.setBeta(0D);
        properties.setGamma(0D);
        properties.setDelta(0D);
        return properties;
    }

    private SemanticCandidate candidate(
            Long prodId,
            Double price,
            Integer soldNum,
            Integer totalStocks,
            Date putawayTime,
            Long categoryId) {
        SemanticCandidate candidate = new SemanticCandidate();
        candidate.setProdId(prodId);
        candidate.setProdName("商品" + prodId);
        candidate.setPrice(price);
        candidate.setSoldNum(soldNum);
        candidate.setTotalStocks(totalStocks);
        candidate.setPutawayTime(putawayTime);
        candidate.setCategoryId(categoryId);
        candidate.setShopId(1L);
        return candidate;
    }

    private Date date(int day) {
        return new Date(1_700_000_000_000L + day * 86_400_000L);
    }
}