package ru.gpb.jira.plugin.autoassignment.ao;

import net.java.ao.Entity;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Active Objects entity representing a single auto-assignment rule.
 *
 * <p>A rule can set any combination of the assignee, co-assignee and watchers
 * fields in one go. All lists are stored as comma separated user names.</p>
 */
@Table("AO_AUTOASSIGN_RULE")
public interface AssignmentRule extends Entity {

    String getProjectKey();
    void setProjectKey(String projectKey);

    String getName();
    void setName(String name);

    @StringLength(StringLength.UNLIMITED)
    String getAssignees();
    void setAssignees(String assignees);

    @StringLength(StringLength.UNLIMITED)
    String getCoAssignees();
    void setCoAssignees(String coAssignees);

    @StringLength(StringLength.UNLIMITED)
    String getWatchers();
    void setWatchers(String watchers);

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
