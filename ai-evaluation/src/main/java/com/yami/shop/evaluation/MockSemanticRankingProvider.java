package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.SemanticCandidate;

import java.util.List;
import java.util.Map;

public class MockSemanticRankingProvider implements EvaluationSemanticRankingProvider {

    private final Map<String, List<Long>> semanticOrders;

    public MockSemanticRankingProvider(Map<String, List<Long>> semanticOrders) {
        this.semanticOrders = semanticOrders;
    }

    @Override
    public List<Long> rank(LabeledQuery query, List<SemanticCandidate> candidates) {
        List<Long> order = semanticOrders.get(query.getQueryId());
        if (order == null) {
            throw new IllegalStateException("缺少 mock 语义顺序: " + query.getQueryId());
        }
        return order;
    }
}