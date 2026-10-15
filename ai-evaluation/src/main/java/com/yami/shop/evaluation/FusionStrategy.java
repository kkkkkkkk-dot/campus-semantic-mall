package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.DeterministicFusionRanker;
import com.yami.shop.service.ranking.SemanticCandidate;

import java.util.List;

public class FusionStrategy implements RankingStrategy {

    private final DeterministicFusionRanker fusionRanker;

    public FusionStrategy(DeterministicFusionRanker fusionRanker) {
        this.fusionRanker = fusionRanker;
    }

    @Override
    public String scheme() {
        return "FUSION";
    }

    @Override
    public List<SemanticCandidate> rank(LabeledQuery query, List<SemanticCandidate> candidates, List<Long> semanticOrder) {
        return fusionRanker.rank(candidates, semanticOrder);
    }
}