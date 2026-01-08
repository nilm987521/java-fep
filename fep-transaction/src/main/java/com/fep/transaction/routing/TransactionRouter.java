package com.fep.transaction.routing;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Routes transactions to appropriate destinations based on configurable rules.
 */
public class TransactionRouter {

    private static final Logger log = LoggerFactory.getLogger(TransactionRouter.class);

    private final List<RoutingRule> rules;
    private RoutingDestination defaultDestination;

    public TransactionRouter() {
        this.rules = new ArrayList<>();
        this.defaultDestination = RoutingDestination.MAINFRAME_CBS;
        initializeDefaultRules();
    }

    /**
     * Initializes default routing rules based on FISC transaction requirements.
     */
    private void initializeDefaultRules() {
        // Interbank transactions -> FISC
        addRule(RoutingRule.builder()
                .ruleName("interbank-withdrawal")
                .priority(10)
                .transactionTypes(Set.of(TransactionType.WITHDRAWAL))
                .destination(RoutingDestination.FISC_INTERBANK)
                .timeoutMs(10000)
                .build());

        addRule(RoutingRule.builder()
                .ruleName("interbank-transfer")
                .priority(10)
                .transactionTypes(Set.of(TransactionType.TRANSFER))
                .destination(RoutingDestination.FISC_INTERBANK)
                .timeoutMs(15000)
                .build());

        addRule(RoutingRule.builder()
                .ruleName("balance-inquiry")
                .priority(10)
                .transactionTypes(Set.of(TransactionType.BALANCE_INQUIRY))
                .destination(RoutingDestination.MAINFRAME_CBS)
                .timeoutMs(5000)
                .build());

        // Bill payment -> FISC bill payment gateway
        addRule(RoutingRule.builder()
                .ruleName("bill-payment")
                .priority(20)
                .transactionTypes(Set.of(TransactionType.BILL_PAYMENT))
                .destination(RoutingDestination.FISC_BILL_PAYMENT)
                .timeoutMs(30000)
                .build());

        // Deposit -> Mainframe
        addRule(RoutingRule.builder()
                .ruleName("deposit")
                .priority(20)
                .transactionTypes(Set.of(TransactionType.DEPOSIT))
                .destination(RoutingDestination.MAINFRAME_CBS)
                .timeoutMs(10000)
                .build());

        // QR Payment and P2P -> Open System API
        addRule(RoutingRule.builder()
                .ruleName("mobile-payment")
                .priority(15)
                .transactionTypes(Set.of(TransactionType.QR_PAYMENT, TransactionType.P2P_TRANSFER))
                .destination(RoutingDestination.OPEN_SYSTEM_API)
                .timeoutMs(10000)
                .build());

        // Reversal -> Same as original transaction type
        addRule(RoutingRule.builder()
                .ruleName("reversal")
                .priority(5)
                .transactionTypes(Set.of(TransactionType.REVERSAL))
                .destination(RoutingDestination.FISC_INTERBANK)
                .timeoutMs(15000)
                .build());

        // Purchase -> Card network
        addRule(RoutingRule.builder()
                .ruleName("purchase")
                .priority(20)
                .transactionTypes(Set.of(TransactionType.PURCHASE, TransactionType.PURCHASE_WITH_CASHBACK))
                .destination(RoutingDestination.CARD_NETWORK)
                .timeoutMs(30000)
                .build());
    }

    /**
     * Adds a routing rule.
     */
    public void addRule(RoutingRule rule) {
        rules.add(rule);
        // Keep rules sorted by priority
        rules.sort(Comparator.comparingInt(RoutingRule::getPriority));
        log.debug("Added routing rule: {} -> {}", rule.getRuleName(), rule.getDestination());
    }

    /**
     * Removes a routing rule by name.
     */
    public boolean removeRule(String ruleName) {
        return rules.removeIf(r -> ruleName.equals(r.getRuleName()));
    }

    /**
     * Routes a transaction request to the appropriate destination.
     */
    public RoutingResult route(TransactionRequest request) {
        String txnId = request.getTransactionId();
        TransactionType txnType = request.getTransactionType();

        log.debug("[{}] Routing transaction type: {}", txnId, txnType);

        // Find matching rule
        for (RoutingRule rule : rules) {
            if (rule.matches(request)) {
                log.info("[{}] Matched rule '{}' -> {} (timeout: {}ms)",
                        txnId, rule.getRuleName(), rule.getDestination(), rule.getTimeoutMs());
                return RoutingResult.success(rule);
            }
        }

        // No specific rule matched, use default
        if (defaultDestination != null) {
            log.info("[{}] No specific rule matched, using default: {}", txnId, defaultDestination);
            return RoutingResult.builder()
                    .routed(true)
                    .destination(defaultDestination)
                    .timeoutMs(30000)
                    .build();
        }

        log.warn("[{}] No routing rule found for transaction type: {}", txnId, txnType);
        return RoutingResult.notFound("No routing rule found for transaction type: " + txnType);
    }

    /**
     * Sets the default destination for unmatched transactions.
     */
    public void setDefaultDestination(RoutingDestination destination) {
        this.defaultDestination = destination;
    }

    /**
     * Gets the number of routing rules.
     */
    public int getRuleCount() {
        return rules.size();
    }

    /**
     * Gets all active rules.
     */
    public List<RoutingRule> getActiveRules() {
        return rules.stream()
                .filter(RoutingRule::isActive)
                .toList();
    }
}
