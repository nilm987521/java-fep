package com.fep.bpmn.scanner.model;

import java.util.regex.Pattern;

/**
 * Represents a category for grouping JavaDelegates.
 * Categories are auto-detected based on class name patterns.
 */
public enum DelegateCategory {

    VALIDATE("validate", "驗證類", "#4CAF50", "check-circle",
            Pattern.compile("(?i)(validate|check|verify)")),

    ACCOUNT("account", "帳務類", "#2196F3", "credit-card",
            Pattern.compile("(?i)(freeze|debit|credit|account|balance|amount|unfreeze|confirm)")),

    MESSAGE("message", "電文類", "#FF9800", "mail",
            Pattern.compile("(?i)(assemble|message|build|compose|format|parse)")),

    COMMUNICATION("communication", "通訊類", "#9C27B0", "broadcast",
            Pattern.compile("(?i)(send|receive|fisc|transmit|response|request)")),

    LOG("log", "日誌類", "#795548", "file-text",
            Pattern.compile("(?i)(log|audit|trace|record)")),

    OTHER("other", "其他", "#607D8B", "package", null);

    private final String id;
    private final String displayName;
    private final String color;
    private final String icon;
    private final Pattern pattern;

    DelegateCategory(String id, String displayName, String color, String icon, Pattern pattern) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.pattern = pattern;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * Detect the category for a given delegate based on its name.
     *
     * @param delegateName the bean name or class name of the delegate
     * @return the detected category, or OTHER if no pattern matches
     */
    public static DelegateCategory detect(String delegateName) {
        if (delegateName == null || delegateName.isEmpty()) {
            return OTHER;
        }

        for (DelegateCategory category : values()) {
            if (category.pattern != null && category.pattern.matcher(delegateName).find()) {
                return category;
            }
        }
        return OTHER;
    }
}
