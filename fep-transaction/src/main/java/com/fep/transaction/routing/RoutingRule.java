package com.fep.transaction.routing;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Defines a routing rule for transaction processing.
 */
@Data
@Builder
public class RoutingRule {

    /** Rule name for identification */
    private String ruleName;

    /** Priority (lower = higher priority) */
    @Builder.Default
    private int priority = 100;

    /** Transaction types this rule applies to */
    private Set<TransactionType> transactionTypes;

    /** Channel filter (null = all channels) */
    private Set<String> channels;

    /** Bank code filter for interbank routing */
    private Set<String> bankCodes;

    /** Custom predicate for complex rules */
    private Predicate<TransactionRequest> condition;

    /** Target destination */
    private RoutingDestination destination;

    /** Timeout in milliseconds for this route */
    @Builder.Default
    private long timeoutMs = 30000;

    /** Whether this rule is active */
    @Builder.Default
    private boolean active = true;

    /**
     * Checks if this rule matches the given request.
     */
    public boolean matches(TransactionRequest request) {
        if (!active) {
            return false;
        }

        // Check transaction type
        if (transactionTypes != null && !transactionTypes.isEmpty()) {
            if (request.getTransactionType() == null ||
                !transactionTypes.contains(request.getTransactionType())) {
                return false;
            }
        }

        // Check channel
        if (channels != null && !channels.isEmpty()) {
            if (request.getChannel() == null ||
                !channels.contains(request.getChannel())) {
                return false;
            }
        }

        // Check bank code (for interbank routing)
        if (bankCodes != null && !bankCodes.isEmpty()) {
            String targetBank = request.getDestinationBankCode();
            if (targetBank == null || !bankCodes.contains(targetBank)) {
                return false;
            }
        }

        // Check custom condition
        if (condition != null && !condition.test(request)) {
            return false;
        }

        return true;
    }
}
