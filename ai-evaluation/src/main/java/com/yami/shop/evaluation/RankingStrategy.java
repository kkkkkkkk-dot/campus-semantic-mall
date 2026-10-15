package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.SemanticCandidate;

import java.util.List;

public interface RankingStrategy {
    String scheme();
    List<SemanticCandidate> rank(LabeledQuery query, List<SemanticCandidate> candidates, List<Long> semanticOrder);
}