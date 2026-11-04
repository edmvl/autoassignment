package ru.gpb.jira.plugin.autoassignment.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gpb.jira.plugin.autoassignment.ao.RuleChangeLog;
import ru.gpb.jira.plugin.autoassignment.dto.RuleHistoryDto;
import ru.gpb.jira.plugin.autoassignment.util.JsonUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Active Objects backed implementation of {@link RuleHistoryService}.
 */
@Component
public class RuleHistoryServiceImpl implements RuleHistoryService {

    private static final int DEFAULT_LIMIT = 100;

    private final ActiveObjects activeObjects;
    private final JiraAuthenticationContext authenticationContext;
    private volatile boolean schemaReady = false;

    @Autowired
    public RuleHistoryServiceImpl(@ComponentImport ActiveObjects activeObjects,
                                  @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.activeObjects = activeObjects;
        this.authenticationContext = authenticationContext;
    }

    /**
     * Active Objects only adds missing tables/columns, so a non-destructive migration is
     * enough to create the history table on existing installations.
     */
    private void ensureSchema() {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            try {
                activeObjects.migrate(RuleChangeLog.class);
                schemaReady = true;
            } catch (RuntimeException e) {
                // Retry on the next call; do not fail the request.
            }
        }
    }

    @Override
    public void log(String projectKey, int ruleId, String ruleName, String action, List<String> details) {
        try {
            ensureSchema();
            RuleChangeLog entity = activeObjects.create(RuleChangeLog.class);
            entity.setProjectKey(projectKey);
            entity.setRuleId(ruleId);
            entity.setRuleName(ruleName == null ? "" : ruleName);
            entity.setActionType(action);
            ApplicationUser user = authenticationContext.getLoggedInUser();
            entity.setUsername(user == null ? "" : user.getName());
            entity.setUserDisplayName(user == null ? "" : user.getDisplayName());
            entity.setChangedAt(System.currentTimeMillis());
            entity.setDetails(JsonUtil.toJson(details == null ? new ArrayList<String>() : details));
            entity.save();
        } catch (RuntimeException e) {
            // History is best-effort: never break the actual rule operation.
        }
    }

    @Override
    public List<RuleHistoryDto> getHistory(String projectKey, int limit) {
        ensureSchema();
        int max = limit <= 0 ? DEFAULT_LIMIT : limit;
        RuleChangeLog[] entities = activeObjects.find(RuleChangeLog.class,
                Query.select()
                        .where("PROJECT_KEY = ?", projectKey)
                        .order("CHANGED_AT DESC")
                        .limit(max));
        List<RuleHistoryDto> result = new ArrayList<>();
        for (RuleChangeLog entity : entities) {
            result.add(toDto(entity));
        }
        return result;
    }

    private RuleHistoryDto toDto(RuleChangeLog entity) {
        RuleHistoryDto dto = new RuleHistoryDto();
        dto.setId(entity.getID());
        dto.setProjectKey(entity.getProjectKey());
        dto.setRuleId(entity.getRuleId());
        dto.setRuleName(entity.getRuleName());
        dto.setAction(entity.getActionType());
        dto.setUsername(entity.getUsername());
        dto.setUserDisplayName(entity.getUserDisplayName());
        dto.setChangedAt(entity.getChangedAt());
        dto.setDetails(JsonUtil.toList(entity.getDetails(), String.class));
        return dto;
    }
}
