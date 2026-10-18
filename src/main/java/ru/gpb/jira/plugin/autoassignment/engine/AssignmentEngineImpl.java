package ru.gpb.jira.plugin.autoassignment.engine;

import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.watchers.WatcherManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gpb.jira.plugin.autoassignment.dto.ConditionDto;
import ru.gpb.jira.plugin.autoassignment.dto.RuleDto;
import ru.gpb.jira.plugin.autoassignment.service.RuleService;
import ru.gpb.jira.plugin.autoassignment.service.SettingsService;
import ru.gpb.jira.plugin.autoassignment.util.ConditionMatcher;
import ru.gpb.jira.plugin.autoassignment.util.FieldValueExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Evaluates rules on issue creation and fills Assignee, Co-assignee and Watchers.
 * Any single rule may set any combination of these target fields.
 */
@Component
public class AssignmentEngineImpl implements AssignmentEngine {

    private static final String MULTI_USER_PICKER = "com.atlassian.jira.plugin.system.customfieldtypes:multiuserpicker";
    private static final String SINGLE_USER_PICKER = "com.atlassian.jira.plugin.system.customfieldtypes:userpicker";

    private final RuleService ruleService;
    private final SettingsService settingsService;
    private final IssueManager issueManager;
    private final WatcherManager watcherManager;
    private final UserManager userManager;
    private final CustomFieldManager customFieldManager;
    private final JiraAuthenticationContext authenticationContext;

    @Autowired
    public AssignmentEngineImpl(
            RuleService ruleService,
            SettingsService settingsService,
            @ComponentImport IssueManager issueManager,
            @ComponentImport WatcherManager watcherManager,
            @ComponentImport UserManager userManager,
            @ComponentImport CustomFieldManager customFieldManager,
            @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.ruleService = ruleService;
        this.settingsService = settingsService;
        this.issueManager = issueManager;
        this.watcherManager = watcherManager;
        this.userManager = userManager;
        this.customFieldManager = customFieldManager;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void onIssueCreated(IssueEvent event) {
        if (!settingsService.isEnabled()) {
            return;
        }
        Issue issue = event.getIssue();
        if (issue == null) {
            return;
        }
        Project project = issue.getProjectObject();
        if (project == null) {
            return;
        }

        List<RuleDto> rules = ruleService.getRules(project.getKey());
        if (rules == null || rules.isEmpty()) {
            return;
        }

        ApplicationUser actingUser = resolveActingUser(event, issue);

        applyAssignee(issue, firstMatch(rules, issue, RuleDto::getAssignees), actingUser);
        applyCoAssignee(issue, firstMatch(rules, issue, RuleDto::getCoAssignees), actingUser);
        applyWatchers(issue, firstMatch(rules, issue, RuleDto::getWatchers));
    }

    /**
     * Returns the first enabled rule (in order) that defines the given target and
     * whose conditions all match the issue.
     */
    private RuleDto firstMatch(List<RuleDto> rules, Issue issue, Function<RuleDto, List<String>> target) {
        for (RuleDto rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            List<String> values = target.apply(rule);
            if (values == null || values.isEmpty()) {
                continue;
            }
            if (allConditionsMatch(rule, issue)) {
                return rule;
            }
        }
        return null;
    }

    private boolean allConditionsMatch(RuleDto rule, Issue issue) {
        List<ConditionDto> conditions = rule.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (ConditionDto condition : conditions) {
            String actual = FieldValueExtractor.getFieldValue(
                    issue, condition.getFieldId(), customFieldManager);
            if (!ConditionMatcher.matches(actual, condition.getOperator(), condition.getValues())) {
                return false;
            }
        }
        return true;
    }

    private void applyAssignee(Issue issue, RuleDto rule, ApplicationUser actingUser) {
        if (rule == null) {
            return;
        }
        List<ApplicationUser> users = resolveUsers(rule.getAssignees());
        if (users.isEmpty()) {
            return;
        }
        ApplicationUser assignee = users.get(0);

        MutableIssue mutable = issueManager.getIssueObject(issue.getId());
        if (mutable == null || assignee.equals(mutable.getAssignee())) {
            return;
        }
        mutable.setAssignee(assignee);
        issueManager.updateIssue(actingUser, mutable, EventDispatchOption.ISSUE_UPDATED, false);
    }

    private void applyCoAssignee(Issue issue, RuleDto rule, ApplicationUser actingUser) {
        if (rule == null) {
            return;
        }
        String fieldId = settingsService.getCoAssigneeFieldId();
        if (fieldId == null || fieldId.trim().isEmpty()) {
            return;
        }
        CustomField customField = customFieldManager.getCustomFieldObject(fieldId);
        if (customField == null) {
            return;
        }
        List<ApplicationUser> users = resolveUsers(rule.getCoAssignees());
        if (users.isEmpty()) {
            return;
        }

        MutableIssue mutable = issueManager.getIssueObject(issue.getId());
        if (mutable == null) {
            return;
        }
        setCustomFieldUsers(mutable, customField, users);
        issueManager.updateIssue(actingUser, mutable, EventDispatchOption.ISSUE_UPDATED, false);
    }

    private void setCustomFieldUsers(MutableIssue mutable, CustomField customField, List<ApplicationUser> users) {
        String typeKey = customField.getCustomFieldType().getKey();
        if (MULTI_USER_PICKER.equals(typeKey)) {
            mutable.setCustomFieldValue(customField, users);
        } else if (SINGLE_USER_PICKER.equals(typeKey)) {
            mutable.setCustomFieldValue(customField, users.get(0));
        } else {
            // Fallback: store user names as comma separated text.
            String names = users.stream()
                    .map(ApplicationUser::getName)
                    .collect(Collectors.joining(","));
            mutable.setCustomFieldValue(customField, names);
        }
    }

    private void applyWatchers(Issue issue, RuleDto rule) {
        if (rule == null) {
            return;
        }
        for (ApplicationUser watcher : resolveUsers(rule.getWatchers())) {
            try {
                if (!watcherManager.isWatching(watcher, issue)) {
                    watcherManager.startWatching(watcher, issue);
                }
            } catch (RuntimeException e) {
                // Ignore per-user failures so one bad watcher does not break the rest.
            }
        }
    }

    private List<ApplicationUser> resolveUsers(List<String> names) {
        List<ApplicationUser> result = new ArrayList<>();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            ApplicationUser user = userManager.getUserByName(name.trim());
            if (user != null) {
                result.add(user);
            }
        }
        return result;
    }

    private ApplicationUser resolveActingUser(IssueEvent event, Issue issue) {
        ApplicationUser user = event.getUser();
        if (user == null) {
            user = issue.getReporterUser();
        }
        if (user == null) {
            user = authenticationContext.getLoggedInUser();
        }
        return user;
    }
}
