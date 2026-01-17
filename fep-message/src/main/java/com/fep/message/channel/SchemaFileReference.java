package com.fep.message.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reference to a schema definition file.
 * Used in the channel configuration to specify which schema files to load.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchemaFileReference {

    /**
     * Path to the schema file (relative to the configuration file location).
     */
    private String path;

    /**
     * Description of what schemas are contained in this file.
     */
    private String description;
}
