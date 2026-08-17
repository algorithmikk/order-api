package com.umameats.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderSuggestion {
    private String storeId;
    private String storeName;
    private String lastItemName;
    private int orderCount;
    private List<String> itemNames;
}
