package ru.gpb.jira.plugin.autoassignment.dto;

/**
 * Global plugin settings.
 */
public class SettingsDto {

    private boolean enabled;
    private String coAssigneeFieldId;

    public SettingsDto() {
    }

    public SettingsDto(boolean enabled, String coAssigneeFieldId) {
        this.enabled = enabled;
        this.coAssigneeFieldId = coAssigneeFieldId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCoAssigneeFieldId() {
        return coAssigneeFieldId;
    }

    public void setCoAssigneeFieldId(String coAssigneeFieldId) {
        this.coAssigneeFieldId = coAssigneeFieldId;
    }
}
