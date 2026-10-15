package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.SemanticCandidate;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NdcgMetric {

    private static final int K = 10;

    public double calculate(LabeledQuery query, List<SemanticCandidate> rankedCandidates) {
        Map<Long, Integer> relevanceMap = new HashMap<>();
        for (IdealItem item : query.getIdeal()) {
            relevanceMap.put(item.getProdId(), item.getRelevance());
        }

        double dcg = calculateDcg(rankedCandidates, relevanceMap, K);
        List<Integer> idealRelevances = query.getIdeal().stream()
                .map(IdealItem::getRelevance)
                .sorted(Comparator.reverseOrder())
                .toList();
        double idcg = calculateIdealDcg(idealRelevances, K);
        return idcg == 0D ? 0D : dcg / idcg;
    }

    private double calculateDcg(List<SemanticCandidate> rankedCandidates, Map<Long, Integer> relevanceMap, int k) {
        double dcg = 0D;
        int limit = Math.min(k, rankedCandidates.size());
        for (int i = 0; i < limit; i++) {
            int relevance = relevanceMap.getOrDefault(rankedCandidates.get(i).getProdId(), 0);
            dcg += gain(relevance) / log2(i + 2D);
        }
        return dcg;
    }

    private double calculateIdealDcg(List<Integer> relevances, int k) {
        double dcg = 0D;
        int limit = Math.min(k, relevances.size());
        for (int i = 0; i < limit; i++) {
            dcg += gain(relevances.get(i)) / log2(i + 2D);
        }
        return dcg;
    }

    private double gain(int relevance) {
        return Math.pow(2D, relevance) - 1D;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2D);
    }
}