package ru.gpb.jira.plugin.autoassignment.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gpb.jira.plugin.autoassignment.ao.AssignmentRule;
import ru.gpb.jira.plugin.autoassignment.dto.ConditionDto;
import ru.gpb.jira.plugin.autoassignment.dto.RuleDto;
import ru.gpb.jira.plugin.autoassignment.util.JsonUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Active Objects backed implementation of {@link RuleService}.
 */
@Component
public class RuleServiceImpl implements RuleService {

    private final ActiveObjects activeObjects;
    private volatile boolean schemaReady = false;

    @Autowired
    public RuleServiceImpl(@ComponentImport ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    /**
     * Active Objects only adds missing columns; run a non-destructive migration once
     * so that upgrades from older rule models get the new target columns.
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
                activeObjects.migrate(AssignmentRule.class);
                schemaReady = true;
            } catch (RuntimeException e) {
                // Retry on the next call; do not fail the request.
            }
        }
    }

    @Override
    public List<RuleDto> getRules(String projectKey) {
        ensureSchema();
        AssignmentRule[] entities = activeObjects.find(AssignmentRule.class,
                Query.select()
                        .where("PROJECT_KEY = ?", projectKey)
                        .order("SORT_ORDER ASC"));
        List<RuleDto> result = new ArrayList<>();
        for (AssignmentRule entity : entities) {
            result.add(toDto(entity));
        }
        return result;
    }

    @Override
    public RuleDto getRule(int id) {
        ensureSchema();
        AssignmentRule entity = activeObjects.get(AssignmentRule.class, id);
        return entity == null ? null : toDto(entity);
    }

    @Override
    public RuleDto createRule(RuleDto rule) {
        ensureSchema();
        AssignmentRule entity = activeObjects.create(AssignmentRule.class);
        applyToEntity(entity, rule);
        if (rule.getSortOrder() <= 0) {
            entity.setSortOrder(getRules(rule.getProjectKey()).size() + 1);
        }
        entity.save();
        return toDto(entity);
    }

    @Override
    public RuleDto updateRule(int id, RuleDto rule) {
        ensureSchema();
        AssignmentRule entity = activeObjects.get(AssignmentRule.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("Rule not found: " + id);
        }
        applyToEntity(entity, rule);
        entity.save();
        return toDto(entity);
    }

    @Override
    public void deleteRule(int id) {
        ensureSchema();
        AssignmentRule entity = activeObjects.get(AssignmentRule.class, id);
        if (entity != null) {
            activeObjects.delete(entity);
        }
    }

    private void applyToEntity(AssignmentRule entity, RuleDto dto) {
        entity.setProjectKey(dto.getProjectKey());
        entity.setName(dto.getName());
        entity.setAssignees(join(dto.getAssignees()));
        entity.setCoAssignees(join(dto.getCoAssignees()));
        entity.setWatchers(join(dto.getWatchers()));
        entity.setConditions(JsonUtil.toJson(dto.getConditions()));
        entity.setSortOrder(dto.getSortOrder());
        entity.setEnabled(dto.isEnabled());
    }

    private RuleDto toDto(AssignmentRule entity) {
        RuleDto dto = new RuleDto();
        dto.setId(entity.getID());
        dto.setProjectKey(entity.getProjectKey());
        dto.setName(entity.getName());
        dto.setAssignees(split(entity.getAssignees()));
        dto.setCoAssignees(split(entity.getCoAssignees()));
        dto.setWatchers(split(entity.getWatchers()));
        dto.setConditions(JsonUtil.toList(entity.getConditions(), ConditionDto.class));
        dto.setSortOrder(entity.getSortOrder());
        dto.setEnabled(entity.isEnabled());
        return dto;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(value.trim());
        }
        return sb.toString();
    }

    private static List<String> split(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(value.split(",")));
    }
}
