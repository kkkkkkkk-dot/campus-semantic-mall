package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.DeterministicFusionRanker;
import com.yami.shop.service.ranking.SemanticCandidate;
import com.yami.shop.service.ranking.SemanticFusionProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FusionStrategyTest {

    @Test
    void fusionStrategyShouldPushAbnormalLowPriceProductDown() {
        SemanticFusionProperties properties = new SemanticFusionProperties();
        properties.setAlpha(0.4D);
        properties.setBeta(0.6D);
        properties.setGamma(0D);
        properties.setDelta(0D);
        properties.setMinCategorySamples(2);
        FusionStrategy strategy = new FusionStrategy(new DeterministicFusionRanker(properties));
        SemanticCandidate cheap = candidate(1L, 1D);
        SemanticCandidate normal = candidate(2L, 100D);

        List<SemanticCandidate> ranked = strategy.rank(new LabeledQuery(), List.of(cheap, normal), List.of(1L, 2L));

        assertThat(ranked).extracting(SemanticCandidate::getProdId).containsExactly(2L, 1L);
    }

    private SemanticCandidate candidate(Long prodId, Double price) {
        SemanticCandidate candidate = new SemanticCandidate();
        candidate.setProdId(prodId);
        candidate.setProdName("商品" + prodId);
        candidate.setPrice(price);
        candidate.setSoldNum(10);
        candidate.setTotalStocks(5);
        candidate.setCategoryId(1L);
        return candidate;
    }
}