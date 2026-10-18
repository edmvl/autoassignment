package ru.gpb.jira.plugin.autoassignment.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * A single auto-assignment rule. One rule can set any combination of
 * assignees, co-assignees and watchers.
 */
public class RuleDto {

    private Integer id;
    private String projectKey;
    private String name;
    private List<String> assignees = new ArrayList<>();
    private List<String> coAssignees = new ArrayList<>();
    private List<String> watchers = new ArrayList<>();
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

    public List<String> getAssignees() {
        return assignees;
    }

    public void setAssignees(List<String> assignees) {
        this.assignees = assignees == null ? new ArrayList<>() : assignees;
    }

    public List<String> getCoAssignees() {
        return coAssignees;
    }

    public void setCoAssignees(List<String> coAssignees) {
        this.coAssignees = coAssignees == null ? new ArrayList<>() : coAssignees;
    }

    public List<String> getWatchers() {
        return watchers;
    }

    public void setWatchers(List<String> watchers) {
        this.watchers = watchers == null ? new ArrayList<>() : watchers;
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
