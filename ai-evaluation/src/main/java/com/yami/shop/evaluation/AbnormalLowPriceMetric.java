package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.DeterministicFusionRanker;
import com.yami.shop.service.ranking.SemanticCandidate;

import java.util.List;

public class AbnormalLowPriceMetric {

    private static final int K = 3;

    private final DeterministicFusionRanker fusionRanker;

    public AbnormalLowPriceMetric(DeterministicFusionRanker fusionRanker) {
        this.fusionRanker = fusionRanker;
    }

    public double calculate(List<SemanticCandidate> allCandidates, List<SemanticCandidate> rankedCandidates) {
        int limit = Math.min(K, rankedCandidates.size());
        int abnormalCount = 0;
        for (int i = 0; i < limit; i++) {
            if (fusionRanker.isAbnormalLowPrice(rankedCandidates.get(i), allCandidates)) {
                abnormalCount++;
            }
        }
        return (double) abnormalCount / K;
    }
}