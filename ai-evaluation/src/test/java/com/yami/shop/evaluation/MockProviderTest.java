package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.SemanticCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockProviderTest {

    @Test
    void shouldReturnPreRecordedSemanticOrder() {
        MockSemanticRankingProvider provider = new MockSemanticRankingProvider(Map.of("q1", List.of(2L, 1L)));
        LabeledQuery query = new LabeledQuery();
        query.setQueryId("q1");

        assertThat(provider.rank(query, List.of(new SemanticCandidate(), new SemanticCandidate())))
                .containsExactly(2L, 1L);
    }
}