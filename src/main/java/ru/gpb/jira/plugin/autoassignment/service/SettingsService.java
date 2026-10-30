package ru.gpb.jira.plugin.autoassignment.service;

import java.util.List;

/**
 * Stores global plugin settings.
 */
public interface SettingsService {

    String KEY_ENABLED = "autoassignment.enabled";
    String KEY_CO_ASSIGNEE_FIELD = "autoassignment.coassignee.field";
    String KEY_CONDITION_FIELDS = "autoassignment.condition.fields";
    String KEY_CONDITION_FIELDS_SET = "autoassignment.condition.fields.configured";

    boolean isEnabled();

    void setEnabled(boolean enabled);

    /**
     * @return the id of the custom field representing "Co-assignee", or {@code null}
     */
    String getCoAssigneeFieldId();

    void setCoAssigneeFieldId(String fieldId);

    /**
     * Field ids that may be used in rule conditions.
     *
     * @return the configured field ids, or {@code null} when the list has never been
     *         configured (meaning: all available fields are allowed)
     */
    List<String> getConditionFieldIds();

    /**
     * Stores the field ids allowed in rule conditions. An empty list means that no
     * fields are allowed; {@code null} resets the setting to "all fields".
     */
    void setConditionFieldIds(List<String> fieldIds);
}
