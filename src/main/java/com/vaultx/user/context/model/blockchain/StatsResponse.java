package com.vaultx.user.context.model.blockchain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class StatsResponse {
    private Map<String, Long> counts;
}
