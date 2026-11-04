package ru.gpb.jira.plugin.autoassignment.ao;

import net.java.ao.Entity;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Active Objects entity representing a single change made to a rule.
 *
 * <p>Entries are append-only and are never updated or deleted by the plugin.</p>
 */
@Table("AO_AUTOASSIGN_RULE_LOG")
public interface RuleChangeLog extends Entity {

    String getProjectKey();
    void setProjectKey(String projectKey);

    int getRuleId();
    void setRuleId(int ruleId);

    String getRuleName();
    void setRuleName(String ruleName);

    /**
     * One of {@code CREATED}, {@code UPDATED}, {@code DELETED}, {@code REORDERED}.
     */
    String getActionType();
    void setActionType(String actionType);

    String getUsername();
    void setUsername(String username);

    String getUserDisplayName();
    void setUserDisplayName(String userDisplayName);

    long getChangedAt();
    void setChangedAt(long changedAt);

    /**
     * JSON array of human readable change lines.
     */
    @StringLength(StringLength.UNLIMITED)
    String getDetails();
    void setDetails(String details);
}
