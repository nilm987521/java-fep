package com.fep.communication.config;

/**
 * Strategy for handling single channel failure in dual-channel mode.
 *
 * <p>Determines how the system behaves when one of the two channels
 * (Send or Receive) fails while the other remains operational.
 */
public enum ChannelFailureStrategy {

    /**
     * Fail only when both channels are down.
     *
     * <p>Most resilient strategy. If one channel fails:
     * <ul>
     *   <li>Continue attempting reconnection</li>
     *   <li>New transactions will wait or timeout</li>
     *   <li>System remains in degraded state until both fail</li>
     * </ul>
     */
    FAIL_WHEN_BOTH_DOWN,

    /**
     * Fail immediately when any channel goes down.
     *
     * <p>Strictest strategy. If either channel fails:
     * <ul>
     *   <li>Immediately mark connection as failed</li>
     *   <li>Cancel all pending requests</li>
     *   <li>Require full reconnection of both channels</li>
     * </ul>
     */
    FAIL_WHEN_ANY_DOWN,

    /**
     * Fallback to single-channel mode when one channel fails.
     *
     * <p>Graceful degradation strategy. If one channel fails:
     * <ul>
     *   <li>Switch to synchronous mode on the surviving channel</li>
     *   <li>Continue processing with reduced throughput</li>
     *   <li>Attempt reconnection of failed channel in background</li>
     * </ul>
     *
     * <p>Note: This mode is non-standard and may not be supported
     * by all FISC environments.
     */
    FALLBACK_TO_SINGLE
}
