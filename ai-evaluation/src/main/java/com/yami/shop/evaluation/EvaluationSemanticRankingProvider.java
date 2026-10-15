package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.SemanticCandidate;

import java.util.List;

public interface EvaluationSemanticRankingProvider {
    List<Long> rank(LabeledQuery query, List<SemanticCandidate> candidates);
}