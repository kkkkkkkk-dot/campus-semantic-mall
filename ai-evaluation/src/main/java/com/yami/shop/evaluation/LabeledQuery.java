package com.yami.shop.evaluation;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class LabeledQuery {
    private String queryId;
    private String query;
    private List<Long> candidateIds;
    private List<IdealItem> ideal;
    private List<Long> hardNegatives;
    private String notes;
}