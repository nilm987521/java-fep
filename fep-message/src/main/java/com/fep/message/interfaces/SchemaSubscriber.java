package com.fep.message.interfaces;

import com.fep.message.generic.schema.MessageSchema;

import java.util.Map;

public interface SchemaSubscriber {
    void updateSchemaMap(Map<String, MessageSchema> schemaMap);
}
