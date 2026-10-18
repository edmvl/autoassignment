package ru.gpb.jira.plugin.autoassignment.service;

/**
 * Stores global plugin settings.
 */
public interface SettingsService {

    String KEY_ENABLED = "autoassignment.enabled";
    String KEY_CO_ASSIGNEE_FIELD = "autoassignment.coassignee.field";

    boolean isEnabled();

    void setEnabled(boolean enabled);

    /**
     * @return the id of the custom field representing "Co-assignee", or {@code null}
     */
    String getCoAssigneeFieldId();

    void setCoAssigneeFieldId(String fieldId);
}
