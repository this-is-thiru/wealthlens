package com.thiru.wealthlens.shared.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedriveResult {
    private List<String> succeeded;
    private Map<String, String> failed;
    private List<String> stillFiltered;
    private List<String> filteredOut;
    private String message;
}
