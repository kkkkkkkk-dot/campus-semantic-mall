package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.SemanticCandidate;

import java.util.ArrayList;
import java.util.List;

public class LikeBaselineStrategy implements RankingStrategy {

    @Override
    public String scheme() {
        return "LIKE_BASELINE";
    }

    @Override
    public List<SemanticCandidate> rank(LabeledQuery query, List<SemanticCandidate> candidates, List<Long> semanticOrder) {
        return new ArrayList<>(candidates);
    }
}