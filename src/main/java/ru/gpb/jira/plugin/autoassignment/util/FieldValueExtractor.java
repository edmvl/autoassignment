package ru.gpb.jira.plugin.autoassignment.util;

import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

/**
 * Extracts a string representation of a field value from an issue so it can be
 * compared against a rule condition. Supports custom fields and a set of common
 * system fields.
 */
public final class FieldValueExtractor {

    private FieldValueExtractor() {
    }

    public static String getFieldValue(Issue issue, String fieldId, CustomFieldManager customFieldManager) {
        if (issue == null || fieldId == null) {
            return null;
        }

        CustomField customField = customFieldManager.getCustomFieldObject(fieldId);
        if (customField != null) {
            return convert(customField.getValue(issue));
        }

        switch (fieldId) {
            case IssueFieldConstants.SUMMARY:
                return issue.getSummary();
            case IssueFieldConstants.DESCRIPTION:
                return issue.getDescription();
            case IssueFieldConstants.ENVIRONMENT:
                return issue.getEnvironment();
            case IssueFieldConstants.PRIORITY:
                return issue.getPriority() == null ? null : issue.getPriority().getName();
            case IssueFieldConstants.ISSUE_TYPE:
                return issue.getIssueType() == null ? null : issue.getIssueType().getName();
            case IssueFieldConstants.STATUS:
                return issue.getStatus() == null ? null : issue.getStatus().getName();
            case IssueFieldConstants.ASSIGNEE:
                return issue.getAssignee() == null ? null : issue.getAssignee().getName();
            case IssueFieldConstants.REPORTER:
                return issue.getReporterUser() == null ? null : issue.getReporterUser().getName();
            case IssueFieldConstants.RESOLUTION:
                return issue.getResolution() == null ? null : issue.getResolution().getName();
            case IssueFieldConstants.PROJECT:
                return issue.getProjectObject() == null ? null : issue.getProjectObject().getKey();
            case IssueFieldConstants.COMPONENTS:
                return convert(issue.getComponents());
            case IssueFieldConstants.FIX_FOR_VERSIONS:
                return convert(issue.getFixVersions());
            case IssueFieldConstants.AFFECTED_VERSIONS:
                return convert(issue.getAffectedVersions());
            case IssueFieldConstants.LABELS:
                return convert(issue.getLabels());
            case IssueFieldConstants.DUE_DATE:
                return convert(issue.getDueDate());
            case IssueFieldConstants.CREATED:
                return convert(issue.getCreated());
            case IssueFieldConstants.UPDATED:
                return convert(issue.getUpdated());
            default:
                return null;
        }
    }

    private static String convert(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof ApplicationUser) {
            return ((ApplicationUser) value).getName();
        }
        if (value instanceof Option) {
            return ((Option) value).getValue();
        }
        if (value instanceof ProjectComponent) {
            return ((ProjectComponent) value).getName();
        }
        if (value instanceof Version) {
            return ((Version) value).getName();
        }
        if (value instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            for (Object item : (Collection<?>) value) {
                String converted = convert(item);
                if (converted == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(converted);
            }
            return sb.toString();
        }
        if (value instanceof Date) {
            return new SimpleDateFormat("yyyy-MM-dd").format((Date) value);
        }
        return value.toString();
    }
}
