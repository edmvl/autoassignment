package ru.gpb.jira.plugin.autoassignment.util;

import java.util.List;

/**
 * Evaluates a single rule condition against an extracted field value.
 * Conditions may carry several expected values (multi-select).
 */
public final class ConditionMatcher {

    public static final String OP_EQUALS = "EQUALS";
    public static final String OP_NOT_EQUALS = "NOT_EQUALS";
    public static final String OP_CONTAINS = "CONTAINS";
    public static final String OP_IS_EMPTY = "IS_EMPTY";
    public static final String OP_IS_NOT_EMPTY = "IS_NOT_EMPTY";

    private ConditionMatcher() {
    }

    public static boolean matches(String actual, String operator, List<String> expected) {
        if (OP_IS_EMPTY.equals(operator)) {
            return actual == null || actual.trim().isEmpty();
        }
        if (OP_IS_NOT_EMPTY.equals(operator)) {
            return actual != null && !actual.trim().isEmpty();
        }

        boolean hasExpected = false;
        if (expected != null) {
            for (String value : expected) {
                if (value != null && !value.isEmpty()) {
                    hasExpected = true;
                    break;
                }
            }
        }
        // No expected values: "not equals" trivially holds, the rest never match.
        if (!hasExpected) {
            return OP_NOT_EQUALS.equals(operator);
        }

        if (OP_EQUALS.equals(operator)) {
            for (String value : expected) {
                if (value != null && value.equals(actual)) {
                    return true;
                }
            }
            return false;
        }

        if (OP_NOT_EQUALS.equals(operator)) {
            for (String value : expected) {
                if (value != null && value.equals(actual)) {
                    return false;
                }
            }
            return true;
        }

        if (OP_CONTAINS.equals(operator)) {
            if (actual == null) {
                return false;
            }
            String lower = actual.toLowerCase();
            for (String value : expected) {
                if (value != null && !value.isEmpty() && lower.contains(value.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }

        return false;
    }
}
