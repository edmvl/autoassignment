package ru.gpb.jira.plugin.autoassignment.engine;

import com.atlassian.jira.event.issue.IssueEvent;

/**
 * Applies auto-assignment rules to a newly created issue.
 */
public interface AssignmentEngine {

    void onIssueCreated(IssueEvent event);
}
