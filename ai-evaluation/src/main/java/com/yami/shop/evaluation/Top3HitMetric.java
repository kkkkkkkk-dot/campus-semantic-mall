package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.SemanticCandidate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Top3HitMetric {

    public double calculate(LabeledQuery query, List<SemanticCandidate> rankedCandidates) {
        Set<Long> relevantIds = new HashSet<>();
        for (IdealItem item : query.getIdeal()) {
            if (item.getRelevance() != null && item.getRelevance() > 0) {
                relevantIds.add(item.getProdId());
            }
        }

        int limit = Math.min(3, rankedCandidates.size());
        for (int i = 0; i < limit; i++) {
            if (relevantIds.contains(rankedCandidates.get(i).getProdId())) {
                return 1D;
            }
        }
        return 0D;
    }
}