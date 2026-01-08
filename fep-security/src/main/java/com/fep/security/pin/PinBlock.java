package com.fep.security.pin;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a PIN Block with its format and data.
 */
@Data
@Builder
public class PinBlock {

    /** PIN block format */
    private PinBlockFormat format;

    /** PIN block data (8 bytes) */
    private byte[] data;

    /** Whether the PIN block is encrypted */
    private boolean encrypted;

    /** Key ID used for encryption (if encrypted) */
    private String keyId;

    /**
     * Returns the PIN block as hex string.
     */
    public String toHexString() {
        if (data == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Creates a PinBlock from hex string.
     */
    public static PinBlock fromHexString(String hex, PinBlockFormat format) {
        if (hex == null || hex.length() != 16) {
            throw new IllegalArgumentException("PIN block hex must be 16 characters");
        }

        byte[] data = new byte[8];
        for (int i = 0; i < 8; i++) {
            data[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }

        return PinBlock.builder()
                .format(format)
                .data(data)
                .build();
    }
}
