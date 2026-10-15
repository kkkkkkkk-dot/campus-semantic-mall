package com.yami.shop.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class ResultWriter {

    public void write(
            Path resultsDir,
            List<EvaluationRunner.SchemeSummary> summaries,
            List<EvaluationRunner.PerQueryRow> rows,
            List<String> warnings) throws IOException {
        Files.createDirectories(resultsDir);
        writeSummary(resultsDir.resolve("summary-report.csv"), summaries);
        writePerQuery(resultsDir.resolve("per-query.csv"), rows);
        writeWarnings(resultsDir.resolve("fusion-warnings.txt"), warnings);
        printSummary(summaries, warnings);
    }

    private void writeSummary(Path path, List<EvaluationRunner.SchemeSummary> summaries) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("scheme,ndcg_at_10,top3_hit_rate,abnormal_low_price_top3_ratio\n");
        for (EvaluationRunner.SchemeSummary summary : summaries) {
            builder.append(csv(summary.scheme())).append(',')
                    .append(format(summary.ndcgAt10())).append(',')
                    .append(format(summary.top3HitRate())).append(',')
                    .append(format(summary.abnormalLowPriceTop3Ratio())).append('\n');
        }
        Files.writeString(path, builder.toString());
    }

    private void writePerQuery(Path path, List<EvaluationRunner.PerQueryRow> rows) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("query_id,scheme,ndcg_at_10,top3_hit_rate,abnormal_low_price_top3_ratio,")
                .append("hard_negative_in_top3,hard_negative_ids_in_top3\n");
        for (EvaluationRunner.PerQueryRow row : rows) {
            builder.append(csv(row.queryId())).append(',')
                    .append(csv(row.scheme())).append(',')
                    .append(format(row.ndcgAt10())).append(',')
                    .append(format(row.top3HitRate())).append(',')
                    .append(format(row.abnormalLowPriceTop3Ratio())).append(',')
                    .append(row.hardNegativeInTop3()).append(',')
                    .append(csv(row.hardNegativeIdsInTop3().toString())).append('\n');
        }
        Files.writeString(path, builder.toString());
    }

    private void writeWarnings(Path path, List<String> warnings) throws IOException {
        String content = warnings.isEmpty() ? "no fusion warnings\n" : String.join("\n", warnings) + "\n";
        Files.writeString(path, content);
    }
    private void printSummary(List<EvaluationRunner.SchemeSummary> summaries, List<String> warnings) {
        System.out.println("scheme,ndcg_at_10,top3_hit_rate,abnormal_low_price_top3_ratio");
        for (EvaluationRunner.SchemeSummary summary : summaries) {
            System.out.printf(Locale.ROOT, "%s,%s,%s,%s%n",
                    summary.scheme(),
                    format(summary.ndcgAt10()),
                    format(summary.top3HitRate()),
                    format(summary.abnormalLowPriceTop3Ratio()));
        }
        for (String warning : warnings) {
            System.out.println("WARNING: " + warning);
        }
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}