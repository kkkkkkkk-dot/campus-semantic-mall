package com.yami.shop.evaluation;

import com.yami.shop.common.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JsonlDatasetLoader {

    public List<LabeledQuery> loadQueries(Path path) throws IOException {
        return load(path, LabeledQuery.class);
    }

    public List<EvaluationCandidate> loadCatalog(Path path) throws IOException {
        return load(path, EvaluationCandidate.class);
    }

    public List<MockRanking> loadMockRankings(Path path) throws IOException {
        return load(path, MockRanking.class);
    }

    public <T> List<T> load(Path path, Class<T> type) throws IOException {
        List<T> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(path);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i) == null ? "" : lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                result.add(Json.getObjectMapper().readValue(line, type));
            } catch (Exception e) {
                throw new IOException("加载 JSONL 失败: " + path + ", line=" + (i + 1), e);
            }
        }
        return result;
    }
}