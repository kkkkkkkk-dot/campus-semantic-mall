package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.DeterministicFusionRanker;
import com.yami.shop.service.ranking.SemanticCandidate;

import java.util.List;

public class PureSemanticStrategy implements RankingStrategy {

    private final DeterministicFusionRanker pureSemanticRanker;

    public PureSemanticStrategy(DeterministicFusionRanker pureSemanticRanker) {
        this.pureSemanticRanker = pureSemanticRanker;
    }

    @Override
    public String scheme() {
        return "PURE_DEEPSEEK";
    }

    @Override
    public List<SemanticCandidate> rank(LabeledQuery query, List<SemanticCandidate> candidates, List<Long> semanticOrder) {
        return pureSemanticRanker.rank(candidates, semanticOrder);
    }
}