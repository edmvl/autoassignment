package ru.gpb.jira.plugin.autoassignment.service;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public List<String> getConditionFieldIds() {
        // A separate "configured" flag distinguishes "never configured" (all fields)
        // from an explicitly empty selection (no fields).
        Object configured = settings().get(KEY_CONDITION_FIELDS_SET);
        if (configured == null || !Boolean.parseBoolean(String.valueOf(configured))) {
            return null;
        }
        List<String> result = new ArrayList<>();
        Object value = settings().get(KEY_CONDITION_FIELDS);
        if (value != null) {
            for (String id : String.valueOf(value).split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    @Override
    public void setConditionFieldIds(List<String> fieldIds) {
        if (fieldIds == null) {
            settings().remove(KEY_CONDITION_FIELDS);
            settings().remove(KEY_CONDITION_FIELDS_SET);
            return;
        }
        StringBuilder joined = new StringBuilder();
        for (String fieldId : fieldIds) {
            if (fieldId == null || fieldId.trim().isEmpty()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(fieldId.trim());
        }
        settings().put(KEY_CONDITION_FIELDS, joined.toString());
        settings().put(KEY_CONDITION_FIELDS_SET, "true");
    }
}
