package com.fep.bpmn.scanner.model;

import java.util.Objects;

/**
 * Represents an input or output variable used by a JavaDelegate.
 */
public class DelegateVariable {

    private final String name;
    private final String type;
    private final boolean required;
    private final String description;
    private final String defaultValue;

    public DelegateVariable(String name, String type, boolean required, String description, String defaultValue) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    public DelegateVariable(String name, String type, boolean required) {
        this(name, type, required, null, null);
    }

    public DelegateVariable(String name, String type) {
        this(name, type, false, null, null);
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DelegateVariable that = (DelegateVariable) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return String.format("%s: %s%s", name, type, required ? " (required)" : "");
    }
}
