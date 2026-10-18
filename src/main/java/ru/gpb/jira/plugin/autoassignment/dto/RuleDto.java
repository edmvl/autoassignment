package ru.gpb.jira.plugin.autoassignment.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer object used both by the REST API and the evaluation engine.
 */
public class RuleDto {

    private Integer id;
    private String projectKey;
    private String name;
    private String targetType;
    private List<String> targetUsers = new ArrayList<>();
    private List<ConditionDto> conditions = new ArrayList<>();
    private int sortOrder;
    private boolean enabled = true;

    public RuleDto() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public List<String> getTargetUsers() {
        return targetUsers;
    }

    public void setTargetUsers(List<String> targetUsers) {
        this.targetUsers = targetUsers == null ? new ArrayList<>() : targetUsers;
    }

    public List<ConditionDto> getConditions() {
        return conditions;
    }

    public void setConditions(List<ConditionDto> conditions) {
        this.conditions = conditions == null ? new ArrayList<>() : conditions;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
