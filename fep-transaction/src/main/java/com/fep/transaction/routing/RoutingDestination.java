package com.fep.transaction.routing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Routing destinations for transaction processing.
 */
@Getter
@RequiredArgsConstructor
public enum RoutingDestination {

    /** Route to mainframe core banking system via MQ */
    MAINFRAME_CBS("主機核心系統", "MQ"),

    /** Route to open system via REST API */
    OPEN_SYSTEM_API("開放系統API", "REST"),

    /** Route to FISC for interbank transactions */
    FISC_INTERBANK("財金跨行", "TCP"),

    /** Route to FISC for bill payment */
    FISC_BILL_PAYMENT("財金繳費", "TCP"),

    /** Route to card scheme network */
    CARD_NETWORK("卡組織", "TCP"),

    /** Route to internal processing (no external call) */
    INTERNAL("內部處理", "INTERNAL"),

    /** Route to external third-party service */
    EXTERNAL_SERVICE("外部服務", "HTTP");

    private final String description;
    private final String protocol;
}
