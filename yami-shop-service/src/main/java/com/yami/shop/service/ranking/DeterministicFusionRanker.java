/*
 * Copyright (c) 2018-2999 广州市蓝海创新科技有限公司 All rights reserved.
 *
 * https://www.mall4j.com/
 *
 * 未经允许，不可做商业用途！
 *
 * 版权所有，侵权必究！
 */

package com.yami.shop.service.ranking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 确定性融合排序器。
 *
 * <p>DeepSeek 只提供语义名次，价格、销量、上架时间等业务信号全部在本地计算。</p>
 */
@Component
@RequiredArgsConstructor
public class DeterministicFusionRanker {

    private static final double NEUTRAL_SCORE = 0.5D;

    private static final int MISSING_SEMANTIC_RANK = Integer.MAX_VALUE;

    private final SemanticFusionProperties properties;

    public List<SemanticCandidate> rank(List<SemanticCandidate> candidates, List<Long> semanticOrder) {
        return rankWithScores(candidates, semanticOrder).stream()
                .map(ScoredCandidate::getCandidate)
                .toList();
    }

    public List<ScoredCandidate> rankWithScores(List<SemanticCandidate> candidates, List<Long> semanticOrder) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<SemanticCandidate> safeCandidates = candidates.stream()
                .filter(candidate -> candidate != null && candidate.getProdId() != null)
                .toList();
        if (safeCandidates.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> semanticRanks = buildSemanticRanks(safeCandidates, semanticOrder);
        Map<Long, Double> priceScores = calculatePriceScores(safeCandidates);
        Map<Long, Double> salesScores = calculateSalesScores(safeCandidates);
        Map<Long, Double> timeScores = calculateTimeScores(safeCandidates);

        List<ScoredCandidate> scoredCandidates = new ArrayList<>(safeCandidates.size());
        for (SemanticCandidate candidate : safeCandidates) {
            int semanticRank = semanticRanks.getOrDefault(candidate.getProdId(), MISSING_SEMANTIC_RANK);
            double semanticScore = calculateSemanticScore(semanticRank, safeCandidates.size());
            double priceScore = priceScores.getOrDefault(candidate.getProdId(), NEUTRAL_SCORE);
            double salesScore = salesScores.getOrDefault(candidate.getProdId(), NEUTRAL_SCORE);
            double timeScore = timeScores.getOrDefault(candidate.getProdId(), NEUTRAL_SCORE);
            double finalScore = properties.getAlpha() * semanticScore
                    + properties.getBeta() * priceScore
                    + properties.getGamma() * salesScore
                    + properties.getDelta() * timeScore;

            scoredCandidates.add(new ScoredCandidate(
                    candidate, semanticRank, semanticScore, priceScore, salesScore, timeScore, finalScore
            ));
        }

        scoredCandidates.sort(buildComparator());
        return scoredCandidates;
    }

    public boolean isAbnormalLowPrice(SemanticCandidate candidate, List<SemanticCandidate> candidates) {
        if (candidate == null || candidate.getPrice() == null || candidate.getPrice() <= 0) {
            return false;
        }
        Double benchmark = resolvePriceBenchmark(candidate, candidates);
        if (benchmark == null || benchmark <= 0) {
            return false;
        }
        return candidate.getPrice() < benchmark * properties.getPriceAnomalyRatio();
    }    private Comparator<ScoredCandidate> buildComparator() {
        return Comparator
                .comparingDouble(ScoredCandidate::getFinalScore)
                .reversed()
                .thenComparingInt(ScoredCandidate::getSemanticRank)
                .thenComparing((left, right) -> Boolean.compare(
                        isInStock(right.getCandidate()), isInStock(left.getCandidate())
                ))
                .thenComparing((left, right) -> Integer.compare(
                        nullSafe(right.getCandidate().getSoldNum()),
                        nullSafe(left.getCandidate().getSoldNum())
                ))
                .thenComparing((left, right) -> Long.compare(
                        timestamp(right.getCandidate()), timestamp(left.getCandidate())
                ))
                .thenComparing(scored -> scored.getCandidate().getProdId(), Comparator.nullsLast(Long::compareTo));
    }

    private Map<Long, Integer> buildSemanticRanks(List<SemanticCandidate> candidates, List<Long> semanticOrder) {
        Set<Long> candidateIds = new HashSet<>();
        for (SemanticCandidate candidate : candidates) {
            candidateIds.add(candidate.getProdId());
        }

        Map<Long, Integer> ranks = new LinkedHashMap<>();
        if (semanticOrder != null) {
            for (Long prodId : semanticOrder) {
                if (prodId != null && candidateIds.contains(prodId) && !ranks.containsKey(prodId)) {
                    ranks.put(prodId, ranks.size() + 1);
                }
            }
        }

        int missingRank = ranks.size() + 1;
        for (SemanticCandidate candidate : candidates) {
            ranks.putIfAbsent(candidate.getProdId(), missingRank);
        }
        return ranks;
    }

    private double calculateSemanticScore(int semanticRank, int candidateCount) {
        if (candidateCount <= 1) {
            return semanticRank == MISSING_SEMANTIC_RANK ? 0D : 1D;
        }
        if (semanticRank == MISSING_SEMANTIC_RANK) {
            return 0D;
        }
        return clamp(1D - ((double) semanticRank - 1D) / (candidateCount - 1D));
    }

    private Map<Long, Double> calculatePriceScores(List<SemanticCandidate> candidates) {
        Map<Long, Double> scores = new HashMap<>();
        Double candidateMedian = median(candidates.stream()
                .map(SemanticCandidate::getPrice)
                .filter(price -> price != null && price > 0)
                .toList());

        for (SemanticCandidate candidate : candidates) {
            Double price = candidate.getPrice();
            if (price == null) {
                scores.put(candidate.getProdId(), NEUTRAL_SCORE);
                continue;
            }
            if (price <= 0) {
                scores.put(candidate.getProdId(), 0D);
                continue;
            }

            Double benchmark = resolveCategoryAverage(candidate, candidates);
            if (benchmark == null) {
                benchmark = candidateMedian;
            }
            if (benchmark == null || benchmark <= 0) {
                scores.put(candidate.getProdId(), NEUTRAL_SCORE);
                continue;
            }

            double threshold = benchmark * properties.getPriceAnomalyRatio();
            scores.put(candidate.getProdId(), threshold <= 0 ? NEUTRAL_SCORE : clamp(price / threshold));
        }
        return scores;
    }    private Map<Long, Double> calculateSalesScores(List<SemanticCandidate> candidates) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Double> normalizedValues = new HashMap<>();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (SemanticCandidate candidate : candidates) {
            double value = Math.log1p(Math.max(0, nullSafe(candidate.getSoldNum())));
            normalizedValues.put(candidate.getProdId(), value);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        if (!Double.isFinite(min) || !Double.isFinite(max) || Double.compare(min, max) == 0) {
            for (SemanticCandidate candidate : candidates) {
                scores.put(candidate.getProdId(), NEUTRAL_SCORE);
            }
            return scores;
        }

        for (SemanticCandidate candidate : candidates) {
            double value = normalizedValues.get(candidate.getProdId());
            scores.put(candidate.getProdId(), clamp((value - min) / (max - min)));
        }
        return scores;
    }

    private Map<Long, Double> calculateTimeScores(List<SemanticCandidate> candidates) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Long> timestamps = new HashMap<>();
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (SemanticCandidate candidate : candidates) {
            long timestamp = timestamp(candidate);
            timestamps.put(candidate.getProdId(), timestamp);
            if (timestamp > 0) {
                min = Math.min(min, timestamp);
                max = Math.max(max, timestamp);
            }
        }

        if (min == Long.MAX_VALUE || min == max) {
            for (SemanticCandidate candidate : candidates) {
                scores.put(candidate.getProdId(), NEUTRAL_SCORE);
            }
            return scores;
        }

        for (SemanticCandidate candidate : candidates) {
            long timestamp = timestamps.get(candidate.getProdId());
            scores.put(candidate.getProdId(), timestamp <= 0 ? 0D : clamp((double) (timestamp - min) / (max - min)));
        }
        return scores;
    }

    private Double resolvePriceBenchmark(SemanticCandidate candidate, List<SemanticCandidate> candidates) {
        Double categoryAverage = resolveCategoryAverage(candidate, candidates);
        if (categoryAverage != null) {
            return categoryAverage;
        }
        return median(candidates.stream()
                .map(SemanticCandidate::getPrice)
                .filter(price -> price != null && price > 0)
                .toList());
    }    private Double resolveCategoryAverage(SemanticCandidate target, List<SemanticCandidate> candidates) {
        if (target.getCategoryId() == null) {
            return null;
        }
        List<Double> prices = candidates.stream()
                .filter(candidate -> target.getCategoryId().equals(candidate.getCategoryId()))
                .map(SemanticCandidate::getPrice)
                .filter(price -> price != null && price > 0)
                .toList();
        if (prices.size() < properties.getMinCategorySamples()) {
            return null;
        }
        return prices.stream().mapToDouble(Double::doubleValue).average().orElse(NEUTRAL_SCORE);
    }

    private Double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int size = sorted.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2D;
    }

    private long timestamp(SemanticCandidate candidate) {
        if (candidate == null) {
            return 0L;
        }
        if (candidate.getPutawayTime() != null) {
            return candidate.getPutawayTime().getTime();
        }
        if (candidate.getCreateTime() != null) {
            return candidate.getCreateTime().getTime();
        }
        return 0L;
    }

    private boolean isInStock(SemanticCandidate candidate) {
        return candidate != null && candidate.getTotalStocks() != null && candidate.getTotalStocks() > 0;
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    @Data
    @AllArgsConstructor
    public static class ScoredCandidate {

        private SemanticCandidate candidate;

        private int semanticRank;

        private double semanticScore;

        private double priceScore;

        private double salesScore;

        private double timeScore;

        private double finalScore;
    }
}