package com.vaultx.user.context.model.blockchain;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatsResponse {
    private Map<String, Long> counts;
}
