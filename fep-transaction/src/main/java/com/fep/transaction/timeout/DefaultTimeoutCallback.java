package com.fep.transaction.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of timeout callback with logging.
 */
public class DefaultTimeoutCallback implements TimeoutCallback {

    private static final Logger log = LoggerFactory.getLogger(DefaultTimeoutCallback.class);

    @Override
    public void onWarning(TimeoutContext context) {
        log.warn("[{}] Transaction approaching timeout: elapsed={}ms, remaining={}ms ({}% elapsed)",
                context.getTransactionId(),
                context.getElapsedMs(),
                context.getRemainingMs(),
                String.format("%.1f", context.getElapsedPercentage()));
    }

    @Override
    public void onTimeout(TimeoutContext context) {
        log.error("[{}] Transaction TIMED OUT: type={}, elapsed={}ms, timeout={}ms",
                context.getTransactionId(),
                context.getTransactionType(),
                context.getElapsedMs(),
                context.getTimeoutMs());
    }

    @Override
    public void onComplete(TimeoutContext context) {
        log.info("[{}] Transaction completed within timeout: elapsed={}ms ({}% of timeout)",
                context.getTransactionId(),
                context.getElapsedMs(),
                String.format("%.1f", context.getElapsedPercentage()));
    }
}
