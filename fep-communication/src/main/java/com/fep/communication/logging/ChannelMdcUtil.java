package com.fep.communication.logging;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility class for managing MDC (Mapped Diagnostic Context) in channel operations.
 *
 * <p>This enables per-channel log file separation using Logback's SiftingAppender.
 * Each TCP channel can have its own log file based on the channelName in MDC.
 *
 * <p>Usage:
 * <pre>{@code
 * try (var ignored = ChannelMdcUtil.withChannel("ATM_CHANNEL")) {
 *     log.info("This goes to channel-ATM_CHANNEL.log");
 * }
 * // MDC automatically cleared after try block
 * }</pre>
 */
public final class ChannelMdcUtil {

    /** MDC key for channel name */
    public static final String CHANNEL_NAME_KEY = "channelName";

    /** MDC key for transaction STAN */
    public static final String STAN_KEY = "stan";

    /** MDC key for message MTI */
    public static final String MTI_KEY = "mti";

    /** MDC key for trace ID (UUID for end-to-end tracking) */
    public static final String TRACE_ID_KEY = "traceId";

    private ChannelMdcUtil() {
        // Utility class
    }

    /**
     * Sets the channel name in MDC.
     *
     * @param channelName the channel name
     */
    public static void setChannel(String channelName) {
        if (channelName != null) {
            MDC.put(CHANNEL_NAME_KEY, sanitizeChannelName(channelName));
        }
    }

    /**
     * Clears the channel name from MDC.
     */
    public static void clearChannel() {
        MDC.remove(CHANNEL_NAME_KEY);
    }

    /**
     * Sets transaction context in MDC.
     *
     * @param stan the STAN (System Trace Audit Number)
     * @param mti the MTI (Message Type Indicator)
     */
    public static void setTransactionContext(String stan, String mti) {
        if (stan != null) {
            MDC.put(STAN_KEY, stan);
        }
        if (mti != null) {
            MDC.put(MTI_KEY, mti);
        }
    }

    /**
     * Clears transaction context from MDC.
     */
    public static void clearTransactionContext() {
        MDC.remove(STAN_KEY);
        MDC.remove(MTI_KEY);
    }

    /**
     * Generates a new trace ID (UUID).
     *
     * @return a new UUID string
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Sets the trace ID in MDC.
     *
     * @param traceId the trace ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * Gets the trace ID from MDC.
     *
     * @return the trace ID, or null if not set
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * Clears the trace ID from MDC.
     */
    public static void clearTraceId() {
        MDC.remove(TRACE_ID_KEY);
    }

    /**
     * Clears all FEP-related MDC entries.
     */
    public static void clearAll() {
        clearChannel();
        clearTransactionContext();
        clearTraceId();
    }

    /**
     * Creates an AutoCloseable scope with the channel name set in MDC.
     * The MDC is automatically cleared when the scope is closed.
     *
     * @param channelName the channel name
     * @return an AutoCloseable that clears the MDC when closed
     */
    public static MdcScope withChannel(String channelName) {
        setChannel(channelName);
        return new MdcScope(true, false);
    }

    /**
     * Creates an AutoCloseable scope with full transaction context in MDC.
     *
     * @param channelName the channel name
     * @param stan the STAN
     * @param mti the MTI
     * @return an AutoCloseable that clears the MDC when closed
     */
    public static MdcScope withTransaction(String channelName, String stan, String mti) {
        setChannel(channelName);
        setTransactionContext(stan, mti);
        return new MdcScope(true, true, false);
    }

    /**
     * Creates an AutoCloseable scope with full transaction context including trace ID.
     *
     * @param channelName the channel name
     * @param stan the STAN
     * @param mti the MTI
     * @param traceId the trace ID
     * @return an AutoCloseable that clears the MDC when closed
     */
    public static MdcScope withFullTransaction(String channelName, String stan, String mti, String traceId) {
        setChannel(channelName);
        setTransactionContext(stan, mti);
        setTraceId(traceId);
        return new MdcScope(true, true, true);
    }

    /**
     * Sanitizes channel name for use in file names.
     * Replaces characters that are invalid in file names.
     *
     * @param channelName the original channel name
     * @return sanitized channel name
     */
    private static String sanitizeChannelName(String channelName) {
        return channelName
            .replaceAll("[^a-zA-Z0-9_-]", "_")
            .replaceAll("_+", "_");
    }

    /**
     * Formats byte array as hex string with spaces between bytes.
     * Example: "02 00 30 30 30 30"
     *
     * @param bytes the byte array to format
     * @return hex string with space-separated bytes, or "null" if input is null
     */
    public static String formatHex(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        if (bytes.length == 0) {
            return "(empty)";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Formats byte array as hex string with spaces, limiting to maxBytes.
     * If truncated, appends "... (N more bytes)".
     *
     * @param bytes the byte array to format
     * @param maxBytes maximum number of bytes to show
     * @return hex string with space-separated bytes
     */
    public static String formatHex(byte[] bytes, int maxBytes) {
        if (bytes == null) {
            return "null";
        }
        if (bytes.length == 0) {
            return "(empty)";
        }
        int limit = Math.min(bytes.length, maxBytes);
        StringBuilder sb = new StringBuilder(limit * 3 + 30);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        if (bytes.length > maxBytes) {
            sb.append(" ... (").append(bytes.length - maxBytes).append(" more bytes)");
        }
        return sb.toString();
    }

    /**
     * AutoCloseable scope for MDC management.
     */
    public static class MdcScope implements AutoCloseable {
        private final boolean clearChannel;
        private final boolean clearTransaction;
        private final boolean clearTrace;

        MdcScope(boolean clearChannel, boolean clearTransaction) {
            this(clearChannel, clearTransaction, false);
        }

        MdcScope(boolean clearChannel, boolean clearTransaction, boolean clearTrace) {
            this.clearChannel = clearChannel;
            this.clearTransaction = clearTransaction;
            this.clearTrace = clearTrace;
        }

        @Override
        public void close() {
            if (clearTrace) {
                clearTraceId();
            }
            if (clearTransaction) {
                clearTransactionContext();
            }
            if (clearChannel) {
                clearChannel();
            }
        }
    }
}
