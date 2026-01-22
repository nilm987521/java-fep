package com.fep.bpmn.scanner.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a discovered JavaDelegate implementation.
 * Contains metadata about the delegate including variables, category, and source location.
 */
public class JavaDelegate {

    private final String name;           // Bean name (camelCase)
    private final String className;      // Simple class name
    private final String packageName;    // Full package name
    private final String filePath;       // Absolute file path
    private final int lineNumber;        // Line number of class declaration
    private final String description;    // From JavaDoc
    private final String displayName;    // Human-readable name
    private final DelegateCategory category;
    private final List<DelegateVariable> inputVariables;
    private final List<DelegateVariable> outputVariables;
    private final boolean valid;
    private final long lastModified;

    private JavaDelegate(Builder builder) {
        this.name = builder.name;
        this.className = builder.className;
        this.packageName = builder.packageName;
        this.filePath = builder.filePath;
        this.lineNumber = builder.lineNumber;
        this.description = builder.description;
        this.displayName = builder.displayName;
        this.category = builder.category;
        this.inputVariables = new ArrayList<>(builder.inputVariables);
        this.outputVariables = new ArrayList<>(builder.outputVariables);
        this.valid = builder.valid;
        this.lastModified = builder.lastModified;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getFullyQualifiedName() {
        return packageName + "." + className;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getDescription() {
        return description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DelegateCategory getCategory() {
        return category;
    }

    public List<DelegateVariable> getInputVariables() {
        return new ArrayList<>(inputVariables);
    }

    public List<DelegateVariable> getOutputVariables() {
        return new ArrayList<>(outputVariables);
    }

    public boolean isValid() {
        return valid;
    }

    public long getLastModified() {
        return lastModified;
    }

    /**
     * Get the delegate expression for use in BPMN.
     * Format: ${delegateName}
     */
    public String getDelegateExpression() {
        return "${" + name + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaDelegate that = (JavaDelegate) o;
        return Objects.equals(getFullyQualifiedName(), that.getFullyQualifiedName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFullyQualifiedName());
    }

    @Override
    public String toString() {
        return String.format("%s (%s) [%s]", name, className, category.getDisplayName());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String className;
        private String packageName;
        private String filePath;
        private int lineNumber = 1;
        private String description = "";
        private String displayName = "";
        private DelegateCategory category = DelegateCategory.OTHER;
        private List<DelegateVariable> inputVariables = new ArrayList<>();
        private List<DelegateVariable> outputVariables = new ArrayList<>();
        private boolean valid = true;
        private long lastModified = System.currentTimeMillis();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder category(DelegateCategory category) {
            this.category = category;
            return this;
        }

        public Builder inputVariables(List<DelegateVariable> inputVariables) {
            this.inputVariables = inputVariables;
            return this;
        }

        public Builder addInputVariable(DelegateVariable variable) {
            this.inputVariables.add(variable);
            return this;
        }

        public Builder outputVariables(List<DelegateVariable> outputVariables) {
            this.outputVariables = outputVariables;
            return this;
        }

        public Builder addOutputVariable(DelegateVariable variable) {
            this.outputVariables.add(variable);
            return this;
        }

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder lastModified(long lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public JavaDelegate build() {
            Objects.requireNonNull(className, "className is required");
            if (name == null || name.isEmpty()) {
                // Convert class name to bean name (camelCase)
                name = Character.toLowerCase(className.charAt(0)) + className.substring(1);
            }
            if (displayName == null || displayName.isEmpty()) {
                // Generate display name from class name
                displayName = generateDisplayName(className);
            }
            if (category == null || category == DelegateCategory.OTHER) {
                // Auto-detect category
                category = DelegateCategory.detect(className);
            }
            return new JavaDelegate(this);
        }

        private String generateDisplayName(String className) {
            // Convert CamelCase to space-separated words
            // e.g., "CheckLimitDelegate" -> "Check Limit"
            String name = className.replaceAll("Delegate$", "");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (i > 0 && Character.isUpperCase(c)) {
                    result.append(' ');
                }
                result.append(c);
            }
            return result.toString();
        }
    }
}
