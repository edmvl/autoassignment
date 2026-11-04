package ru.gpb.jira.plugin.autoassignment.service;

import ru.gpb.jira.plugin.autoassignment.dto.RuleHistoryDto;

import java.util.List;

/**
 * Append-only log of rule changes (who changed which rule, when and what exactly).
 */
public interface RuleHistoryService {

    String ACTION_CREATED = "CREATED";
    String ACTION_UPDATED = "UPDATED";
    String ACTION_DELETED = "DELETED";
    String ACTION_REORDERED = "REORDERED";

    /**
     * Records a change performed by the currently logged in user.
     * Failures are swallowed so that history never breaks rule saving.
     */
    void log(String projectKey, int ruleId, String ruleName, String action, List<String> details);

    /**
     * @return the most recent entries for the project, newest first
     */
    List<RuleHistoryDto> getHistory(String projectKey, int limit);
}
