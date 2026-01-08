package com.fep.settlement.domain;

/**
 * Priority levels for discrepancy handling.
 */
public enum DiscrepancyPriority {

    /** Critical - requires immediate attention */
    CRITICAL("緊急", 1, 1),

    /** High priority */
    HIGH("高", 2, 4),

    /** Medium priority */
    MEDIUM("中", 3, 24),

    /** Low priority */
    LOW("低", 4, 72);

    private final String chineseName;
    private final int level;
    private final int resolutionHours;

    DiscrepancyPriority(String chineseName, int level, int resolutionHours) {
        this.chineseName = chineseName;
        this.level = level;
        this.resolutionHours = resolutionHours;
    }

    public String getChineseName() {
        return chineseName;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Get target resolution time in hours.
     */
    public int getResolutionHours() {
        return resolutionHours;
    }

    /**
     * Check if this priority is higher than another.
     */
    public boolean isHigherThan(DiscrepancyPriority other) {
        return this.level < other.level;
    }

    /**
     * Get priority from level.
     */
    public static DiscrepancyPriority fromLevel(int level) {
        for (DiscrepancyPriority p : values()) {
            if (p.level == level) {
                return p;
            }
        }
        return MEDIUM;
    }
}
