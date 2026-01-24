package com.fep.bpmn.scanner.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Custom Gson serializer for DelegateCategory enum.
 * Serializes the enum as a full object with id, displayName, color, and icon
 * instead of just the enum name string.
 */
public class DelegateCategorySerializer implements JsonSerializer<DelegateCategory> {

    @Override
    public JsonElement serialize(DelegateCategory category, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("id", category.getId());
        json.addProperty("displayName", category.getDisplayName());
        json.addProperty("color", category.getColor());
        json.addProperty("icon", category.getIcon());
        return json;
    }
}
