package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.DeterministicFusionRanker;
import com.yami.shop.service.ranking.SemanticCandidate;
import com.yami.shop.service.ranking.SemanticFusionProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricTest {

    @Test
    void ndcgAndTop3HitShouldRewardIdealOrder() {
        LabeledQuery query = query();
        List<SemanticCandidate> idealOrder = List.of(candidate(1L, 10D), candidate(2L, 10D), candidate(3L, 10D));
        List<SemanticCandidate> reversed = List.of(candidate(3L, 10D), candidate(2L, 10D), candidate(1L, 10D));

        assertThat(new NdcgMetric().calculate(query, idealOrder)).isEqualTo(1D);
        assertThat(new NdcgMetric().calculate(query, reversed)).isLessThan(1D);
        assertThat(new Top3HitMetric().calculate(query, idealOrder)).isEqualTo(1D);
    }

    @Test
    void abnormalLowPriceMetricShouldUseSharedFusionRankerRule() {
        SemanticFusionProperties properties = new SemanticFusionProperties();
        properties.setPriceAnomalyRatio(0.5D);
        properties.setMinCategorySamples(2);
        DeterministicFusionRanker ranker = new DeterministicFusionRanker(properties);
        List<SemanticCandidate> all = List.of(candidate(1L, 1D), candidate(2L, 100D), candidate(3L, 100D));

        double ratio = new AbnormalLowPriceMetric(ranker).calculate(
                all,
                List.of(all.get(0), all.get(1), all.get(2))
        );

        assertThat(ratio).isEqualTo(1D / 3D);
    }

    private LabeledQuery query() {
        IdealItem first = new IdealItem();
        first.setProdId(1L);
        first.setRelevance(3);
        IdealItem second = new IdealItem();
        second.setProdId(2L);
        second.setRelevance(2);
        LabeledQuery query = new LabeledQuery();
        query.setQueryId("q1");
        query.setQuery("手机");
        query.setIdeal(List.of(first, second));
        return query;
    }

    private SemanticCandidate candidate(Long prodId, Double price) {
        SemanticCandidate candidate = new SemanticCandidate();
        candidate.setProdId(prodId);
        candidate.setProdName("商品" + prodId);
        candidate.setPrice(price);
        candidate.setCategoryId(1L);
        return candidate;
    }
}