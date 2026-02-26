package com.sea.aiservice.model;

/**
 * Severity level of an AI-generated insight.
 * Determined based on budget percentage used.
 *
 * LOW      → under 50% used (healthy spending)
 * MEDIUM   → 50-74% used (on track)
 * HIGH     → 75-89% used (caution)
 * CRITICAL → 90%+ used or budget exceeded (warning!)
 */
public enum SeverityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}