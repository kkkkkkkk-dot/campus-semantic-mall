package com.yami.shop.evaluation;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class MockRanking {
    private String queryId;
    private List<Long> semanticOrder;
}