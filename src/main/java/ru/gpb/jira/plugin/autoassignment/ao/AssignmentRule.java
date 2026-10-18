package ru.gpb.jira.plugin.autoassignment.ao;

import net.java.ao.Entity;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Active Objects entity representing a single auto-assignment rule.
 *
 * A rule is evaluated against an issue on creation: if all of its
 * {@link #getConditions()} match the issue, the rule's target field
 * ({@link #getTargetType()}) is populated with {@link #getTargetValue()}.
 */
@Table("AO_AUTOASSIGN_RULE")
public interface AssignmentRule extends Entity {

    String getProjectKey();
    void setProjectKey(String projectKey);

    String getName();
    void setName(String name);

    /**
     * One of: ASSIGNEE, CO_ASSIGNEE, WATCHERS.
     */
    String getTargetType();
    void setTargetType(String targetType);

    /**
     * Comma separated list of user names used to populate the target field.
     */
    @StringLength(StringLength.UNLIMITED)
    String getTargetValue();
    void setTargetValue(String targetValue);

    /**
     * JSON array of condition objects: [{fieldId, operator, value}, ...].
     */
    @StringLength(StringLength.UNLIMITED)
    String getConditions();
    void setConditions(String conditions);

    int getSortOrder();
    void setSortOrder(int sortOrder);

    boolean isEnabled();
    void setEnabled(boolean enabled);
}
