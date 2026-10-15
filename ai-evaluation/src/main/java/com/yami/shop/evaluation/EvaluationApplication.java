package com.yami.shop.evaluation;

import com.yami.shop.common.util.Json;
import com.yami.shop.service.ranking.DeterministicFusionRanker;
import com.yami.shop.service.ranking.DeepSeekSemanticRankingProvider;
import com.yami.shop.service.ranking.SemanticCandidate;
import com.yami.shop.service.ranking.SemanticFusionProperties;
import com.yami.shop.service.ranking.SemanticRankingProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EvaluationApplication {

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        Path dataDir = Path.of(options.getOrDefault("data-dir", "ai-evaluation/data"));
        Path resultsDir = Path.of(options.getOrDefault("results-dir", "ai-evaluation/results"));

        if (Boolean.parseBoolean(options.getOrDefault("capture-snapshot", "false"))) {
            captureSnapshot(dataDir, options);
            return;
        }

        String providerName = options.getOrDefault("provider", "mock");
        if (!"mock".equalsIgnoreCase(providerName)) {
            throw new IllegalArgumentException("调参阶段只允许使用 mock provider，真实 DeepSeek 仅用于 --capture-snapshot=true");
        }

        SemanticFusionProperties fusionProperties = buildFusionProperties(options);
        SemanticFusionProperties pureProperties = pureSemanticProperties();
        DeterministicFusionRanker fusionRanker = new DeterministicFusionRanker(fusionProperties);
        DeterministicFusionRanker pureRanker = new DeterministicFusionRanker(pureProperties);

        JsonlDatasetLoader loader = new JsonlDatasetLoader();
        Map<String, List<Long>> mockOrders = loader.loadMockRankings(dataDir.resolve("mock-rankings.jsonl"))
                .stream()
                .collect(Collectors.toMap(MockRanking::getQueryId, MockRanking::getSemanticOrder));
        EvaluationSemanticRankingProvider provider = new MockSemanticRankingProvider(mockOrders);

        EvaluationRunner runner = new EvaluationRunner(
                provider,
                List.of(
                        new LikeBaselineStrategy(),
                        new PureSemanticStrategy(pureRanker),
                        new FusionStrategy(fusionRanker)
                ),
                fusionRanker
        );
        boolean assertHardNegativesOut = Boolean.parseBoolean(options.getOrDefault("assert-hard-negatives-out", "false"));
        String queriesFile = options.getOrDefault("queries-file", "queries.jsonl");
        EvaluationRunner.RunResult result = runner.run(dataDir, resultsDir, queriesFile, assertHardNegativesOut);
        assertFusionNotWorse(result, Boolean.parseBoolean(options.getOrDefault("assert-fusion-not-worse", "true")));
    }

    private static void captureSnapshot(Path dataDir, Map<String, String> options) throws Exception {
        JsonlDatasetLoader loader = new JsonlDatasetLoader();
        List<LabeledQuery> queries = loader.loadQueries(dataDir.resolve("queries.jsonl"));
        Map<Long, EvaluationCandidate> catalog = loader.loadCatalog(dataDir.resolve("catalog.jsonl"))
                .stream()
                .collect(Collectors.toMap(EvaluationCandidate::getProdId, Function.identity()));

        SemanticRankingProvider provider = new DeepSeekSemanticRankingProvider(buildChatModel(options));
        EvaluationSemanticRankingProvider evaluationProvider = new DeepSeekEvaluationProvider(provider);
        List<String> lines = new ArrayList<>();
        for (LabeledQuery query : queries) {
            List<SemanticCandidate> candidates = EvaluationRunner.buildCandidates(query, catalog);
            System.out.println("capture-snapshot query start: queryId=" + query.getQueryId()
                    + ", query=" + query.getQuery() + ", candidateIds=" + query.getCandidateIds());
            List<Long> semanticOrder = evaluationProvider.rank(query, candidates);
            System.out.println("capture-snapshot query done: queryId=" + query.getQueryId()
                    + ", semanticOrder=" + semanticOrder);
            MockRanking ranking = new MockRanking();
            ranking.setQueryId(query.getQueryId());
            ranking.setSemanticOrder(semanticOrder);
            lines.add(Json.toJsonString(ranking));
        }
        Files.write(dataDir.resolve("mock-rankings.jsonl"), lines);
        System.out.println("DeepSeek 语义快照已写入: " + dataDir.resolve("mock-rankings.jsonl"));
    }

    private static ChatModel buildChatModel(Map<String, String> options) {
        String apiKey = options.getOrDefault("api-key", System.getenv().getOrDefault("DEEPSEEK_API_KEY", ""));
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("抓取真实语义快照需要 --api-key 或 DEEPSEEK_API_KEY");
        }
        printApiKeyStatus(apiKey);
        System.out.println("DeepSeek model config: baseUrl=" + options.getOrDefault("base-url", "https://api.deepseek.com/v1")
                + ", model=" + options.getOrDefault("model", "deepseek-chat"));
        OpenAiChatOptions optionsModel = OpenAiChatOptions.builder()
                .baseUrl(options.getOrDefault("base-url", "https://api.deepseek.com/v1"))
                .apiKey(apiKey)
                .model(options.getOrDefault("model", "deepseek-chat"))
                .temperature(Double.parseDouble(options.getOrDefault("temperature", "0.0")))
                .maxTokens(Integer.parseInt(options.getOrDefault("max-tokens", "800")))
                .timeout(Duration.parse(options.getOrDefault("timeout", "PT15S")))
                .maxRetries(Integer.parseInt(options.getOrDefault("max-retries", "2")))
                .build();
        return OpenAiChatModel.builder().options(optionsModel).build();
    }

    private static void printApiKeyStatus(String apiKey) {
        String prefix = apiKey.length() >= 4 ? apiKey.substring(0, 4) : apiKey;
        System.out.println("DeepSeek apiKey status: present=true, prefix=" + prefix + ", length=" + apiKey.length());
    }

    private static SemanticFusionProperties buildFusionProperties(Map<String, String> options) {
        SemanticFusionProperties properties = new SemanticFusionProperties();
        properties.setAlpha(doubleOption(options, "alpha", properties.getAlpha()));
        properties.setBeta(doubleOption(options, "beta", properties.getBeta()));
        properties.setGamma(doubleOption(options, "gamma", properties.getGamma()));
        properties.setDelta(doubleOption(options, "delta", properties.getDelta()));
        properties.setPriceAnomalyRatio(doubleOption(
                options, "price-anomaly-ratio", properties.getPriceAnomalyRatio()
        ));
        properties.setMinCategorySamples(intOption(
                options, "min-category-samples", properties.getMinCategorySamples()
        ));
        return properties;
    }

    private static SemanticFusionProperties pureSemanticProperties() {
        SemanticFusionProperties properties = new SemanticFusionProperties();
        properties.setAlpha(1D);
        properties.setBeta(0D);
        properties.setGamma(0D);
        properties.setDelta(0D);
        return properties;
    }

    private static void assertFusionNotWorse(EvaluationRunner.RunResult result, boolean enabled) {
        double pureNdcg = ndcg(result, "PURE_DEEPSEEK");
        double fusionNdcg = ndcg(result, "FUSION");
        if (fusionNdcg + 0.000001D < pureNdcg) {
            String message = "FUSION ndcg_at_10 低于 PURE_DEEPSEEK: fusion="
                    + fusionNdcg + ", pure=" + pureNdcg + "。请调整 alpha/beta/gamma/delta 或 price-anomaly-ratio。";
            System.out.println("WARNING: " + message);
            if (enabled) {
                throw new IllegalStateException(message);
            }
        }
    }

    private static double ndcg(EvaluationRunner.RunResult result, String scheme) {
        return result.summaries().stream()
                .filter(summary -> scheme.equals(summary.scheme()))
                .mapToDouble(EvaluationRunner.SchemeSummary::ndcgAt10)
                .findFirst()
                .orElse(0D);
    }

    private static double doubleOption(Map<String, String> options, String key, double defaultValue) {
        String value = options.get(key);
        return value == null ? defaultValue : Double.parseDouble(value);
    }

    private static int intOption(Map<String, String> options, String key, int defaultValue) {
        String value = options.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String arg : args) {
            if (arg != null && arg.startsWith("--") && arg.contains("=")) {
                int index = arg.indexOf('=');
                options.put(arg.substring(2, index), arg.substring(index + 1));
            }
        }
        return options;
    }
}