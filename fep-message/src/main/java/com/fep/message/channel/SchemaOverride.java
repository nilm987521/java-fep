package com.fep.message.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a schema override for a specific message type.
 * Allows different schemas to be used for request and response
 * based on the message type (MTI).
 *
 * <p>Example: For FISC channel, MTI 0400 (reversal) might use
 * a different schema than MTI 0200 (financial transaction).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchemaOverride {

    /**
     * Schema name for request messages of this type.
     */
    private String request;

    /**
     * Schema name for response messages of this type.
     */
    private String response;
}
