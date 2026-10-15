package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.SemanticCandidate;
import com.yami.shop.service.ranking.SemanticRankingProvider;

import java.util.List;

/**
 * 真实 DeepSeek 评估入口，仅用于抓取一次语义顺序快照。
 */
public class DeepSeekEvaluationProvider implements EvaluationSemanticRankingProvider {

    private final SemanticRankingProvider delegate;

    public DeepSeekEvaluationProvider(SemanticRankingProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Long> rank(LabeledQuery query, List<SemanticCandidate> candidates) {
        return delegate.rank(query.getQuery(), candidates);
    }
}