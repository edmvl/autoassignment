package ru.gpb.jira.plugin.autoassignment.util;

/**
 * Evaluates a single rule condition against an extracted field value.
 */
public final class ConditionMatcher {

    public static final String OP_EQUALS = "EQUALS";
    public static final String OP_NOT_EQUALS = "NOT_EQUALS";
    public static final String OP_CONTAINS = "CONTAINS";
    public static final String OP_IS_EMPTY = "IS_EMPTY";
    public static final String OP_IS_NOT_EMPTY = "IS_NOT_EMPTY";

    private ConditionMatcher() {
    }

    /**
     * @param actual   the value of the field on the issue, or {@code null}
     * @param operator one of the {@code OP_*} constants
     * @param expected the value configured in the rule, may be {@code null}
     */
    public static boolean matches(String actual, String operator, String expected) {
        if (OP_IS_EMPTY.equals(operator)) {
            return actual == null || actual.trim().isEmpty();
        }
        if (OP_IS_NOT_EMPTY.equals(operator)) {
            return actual != null && !actual.trim().isEmpty();
        }
        if (OP_EQUALS.equals(operator)) {
            return expected != null && expected.equals(actual);
        }
        if (OP_NOT_EQUALS.equals(operator)) {
            return expected != null && !expected.equals(actual);
        }
        if (OP_CONTAINS.equals(operator)) {
            return actual != null && expected != null
                    && actual.toLowerCase().contains(expected.toLowerCase());
        }
        return false;
    }
}
