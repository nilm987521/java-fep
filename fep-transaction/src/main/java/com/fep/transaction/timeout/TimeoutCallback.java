package com.fep.transaction.timeout;

/**
 * Callback interface for timeout events.
 */
public interface TimeoutCallback {

    /**
     * Called when a transaction enters the warning zone.
     */
    void onWarning(TimeoutContext context);

    /**
     * Called when a transaction times out.
     */
    void onTimeout(TimeoutContext context);

    /**
     * Called when a transaction completes within timeout.
     */
    void onComplete(TimeoutContext context);
}
