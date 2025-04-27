package com.vaultx.user.context.model.blockchain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EventHistory {
    private String txId;
    private String timestamp;
    private boolean isDelete;
    private String value;
}