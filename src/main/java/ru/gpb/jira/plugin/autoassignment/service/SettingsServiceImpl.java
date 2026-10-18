package ru.gpb.jira.plugin.autoassignment.service;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Default implementation backed by global plugin settings.
 */
@Component
public class SettingsServiceImpl implements SettingsService {

    private final PluginSettingsFactory pluginSettingsFactory;

    @Autowired
    public SettingsServiceImpl(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    private PluginSettings settings() {
        return pluginSettingsFactory.createGlobalSettings();
    }

    @Override
    public boolean isEnabled() {
        Object value = settings().get(KEY_ENABLED);
        // Enabled by default so the feature works as soon as rules exist.
        return value == null || Boolean.parseBoolean(String.valueOf(value));
    }

    @Override
    public void setEnabled(boolean enabled) {
        settings().put(KEY_ENABLED, String.valueOf(enabled));
    }

    @Override
    public String getCoAssigneeFieldId() {
        Object value = settings().get(KEY_CO_ASSIGNEE_FIELD);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        return String.valueOf(value);
    }

    @Override
    public void setCoAssigneeFieldId(String fieldId) {
        if (fieldId == null || fieldId.trim().isEmpty()) {
            settings().remove(KEY_CO_ASSIGNEE_FIELD);
        } else {
            settings().put(KEY_CO_ASSIGNEE_FIELD, fieldId);
        }
    }
}
