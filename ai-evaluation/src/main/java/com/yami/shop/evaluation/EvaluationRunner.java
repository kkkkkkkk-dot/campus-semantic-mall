package com.yami.shop.evaluation;

import com.yami.shop.service.ranking.DeterministicFusionRanker;
import com.yami.shop.service.ranking.SemanticCandidate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EvaluationRunner {

    private final JsonlDatasetLoader datasetLoader = new JsonlDatasetLoader();

    private final NdcgMetric ndcgMetric = new NdcgMetric();

    private final Top3HitMetric top3HitMetric = new Top3HitMetric();

    private final EvaluationSemanticRankingProvider semanticRankingProvider;

    private final List<RankingStrategy> strategies;

    private final DeterministicFusionRanker fusionRanker;

    private final AbnormalLowPriceMetric abnormalLowPriceMetric;

    public EvaluationRunner(
            EvaluationSemanticRankingProvider semanticRankingProvider,
            List<RankingStrategy> strategies,
            DeterministicFusionRanker fusionRanker) {
        this.semanticRankingProvider = semanticRankingProvider;
        this.strategies = strategies;
        this.fusionRanker = fusionRanker;
        this.abnormalLowPriceMetric = new AbnormalLowPriceMetric(fusionRanker);
    }

    public RunResult run(Path dataDir, Path resultsDir, String queriesFile, boolean assertHardNegativesOut) throws Exception {
        List<LabeledQuery> queries = datasetLoader.loadQueries(dataDir.resolve(queriesFile));
        Map<Long, EvaluationCandidate> catalog = datasetLoader.loadCatalog(dataDir.resolve("catalog.jsonl"))
                .stream()
                .collect(Collectors.toMap(EvaluationCandidate::getProdId, Function.identity()));

        List<PerQueryRow> rows = new ArrayList<>();
        Map<String, List<Double>> ndcgValues = initMetricMap();
        Map<String, List<Double>> top3Values = initMetricMap();
        Map<String, List<Double>> abnormalValues = initMetricMap();
        List<String> warnings = new ArrayList<>();

        for (LabeledQuery query : queries) {
            List<SemanticCandidate> candidates = buildCandidates(query, catalog);
            List<Long> semanticOrder = semanticRankingProvider.rank(query, candidates);
            for (RankingStrategy strategy : strategies) {
                List<SemanticCandidate> ranked = strategy.rank(query, candidates, semanticOrder);
                double ndcg = ndcgMetric.calculate(query, ranked);
                double top3 = top3HitMetric.calculate(query, ranked);
                double abnormal = abnormalLowPriceMetric.calculate(candidates, ranked);
                boolean hardNegativeInTop3 = hardNegativeInTop3(query, ranked);

                ndcgValues.get(strategy.scheme()).add(ndcg);
                top3Values.get(strategy.scheme()).add(top3);
                abnormalValues.get(strategy.scheme()).add(abnormal);
                rows.add(new PerQueryRow(
                        query.getQueryId(), strategy.scheme(), ndcg, top3, abnormal,
                        hardNegativeInTop3, hardNegativeIdsInTop3(query, ranked)
                ));

                if ("FUSION".equals(strategy.scheme()) && hardNegativeInTop3) {
                    warnings.add("默认权重下 hardNegative 仍进入 FUSION Top3: query="
                            + query.getQueryId() + ", ids=" + hardNegativeIdsInTop3(query, ranked)
                            + "。建议优先提高 beta；或适当提高 price-anomaly-ratio。");
                }
            }
        }

        List<SchemeSummary> summaries = buildSummaries(ndcgValues, top3Values, abnormalValues);
        new ResultWriter().write(resultsDir, summaries, rows, warnings);
        assertHardNegativesOut(rows, assertHardNegativesOut);
        return new RunResult(summaries, rows, warnings);
    }

    private void assertHardNegativesOut(List<PerQueryRow> rows, boolean enabled) {
        if (!enabled) {
            return;
        }
        List<PerQueryRow> violations = rows.stream()
                .filter(row -> "FUSION".equals(row.scheme()))
                .filter(PerQueryRow::hardNegativeInTop3)
                .toList();
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(row -> row.queryId() + "=" + row.hardNegativeIdsInTop3())
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("FUSION hardNegative 仍在 Top3: " + details);
        }
    }
    public static List<SemanticCandidate> buildCandidates(
            LabeledQuery query,
            Map<Long, EvaluationCandidate> catalog) {
        List<SemanticCandidate> candidates = new ArrayList<>();
        for (Long prodId : query.getCandidateIds()) {
            EvaluationCandidate source = catalog.get(prodId);
            if (source == null) {
                throw new IllegalArgumentException("catalog 缺少商品: " + prodId + ", query=" + query.getQueryId());
            }
            SemanticCandidate candidate = new SemanticCandidate();
            candidate.setProdId(source.getProdId());
            candidate.setProdName(source.getProdName());
            candidate.setPrice(source.getPrice());
            candidate.setSoldNum(source.getSoldNum());
            candidate.setTotalStocks(source.getTotalStocks());
            candidate.setPutawayTime(parseDate(source.getPutawayTime()));
            candidate.setCategoryId(source.getCategoryId());
            candidate.setShopId(source.getShopId());
            candidates.add(candidate);
        }
        return candidates;
    }

    private static Date parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        LocalDateTime dateTime = LocalDateTime.parse(value);
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private Map<String, List<Double>> initMetricMap() {
        Map<String, List<Double>> values = new LinkedHashMap<>();
        for (RankingStrategy strategy : strategies) {
            values.put(strategy.scheme(), new ArrayList<>());
        }
        return values;
    }

    private List<SchemeSummary> buildSummaries(
            Map<String, List<Double>> ndcgValues,
            Map<String, List<Double>> top3Values,
            Map<String, List<Double>> abnormalValues) {
        List<SchemeSummary> summaries = new ArrayList<>();
        for (RankingStrategy strategy : strategies) {
            String scheme = strategy.scheme();
            summaries.add(new SchemeSummary(
                    scheme,
                    average(ndcgValues.get(scheme)),
                    average(top3Values.get(scheme)),
                    average(abnormalValues.get(scheme))
            ));
        }
        return summaries;
    }

    private double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private boolean hardNegativeInTop3(LabeledQuery query, List<SemanticCandidate> ranked) {
        return !hardNegativeIdsInTop3(query, ranked).isEmpty();
    }

    private List<Long> hardNegativeIdsInTop3(LabeledQuery query, List<SemanticCandidate> ranked) {
        if (query.getHardNegatives() == null || query.getHardNegatives().isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        int limit = Math.min(3, ranked.size());
        for (int i = 0; i < limit; i++) {
            Long prodId = ranked.get(i).getProdId();
            if (query.getHardNegatives().contains(prodId)) {
                ids.add(prodId);
            }
        }
        return ids;
    }

    public record RunResult(List<SchemeSummary> summaries, List<PerQueryRow> rows, List<String> warnings) {
    }

    public record SchemeSummary(
            String scheme,
            double ndcgAt10,
            double top3HitRate,
            double abnormalLowPriceTop3Ratio) {
    }

    public record PerQueryRow(
            String queryId,
            String scheme,
            double ndcgAt10,
            double top3HitRate,
            double abnormalLowPriceTop3Ratio,
            boolean hardNegativeInTop3,
            List<Long> hardNegativeIdsInTop3) {
    }
}