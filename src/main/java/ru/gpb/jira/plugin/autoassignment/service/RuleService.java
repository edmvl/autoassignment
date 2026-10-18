package ru.gpb.jira.plugin.autoassignment.service;

import ru.gpb.jira.plugin.autoassignment.dto.RuleDto;

import java.util.List;

/**
 * CRUD operations for auto-assignment rules, ordered by {@code sortOrder}.
 */
public interface RuleService {

    List<RuleDto> getRules(String projectKey);

    RuleDto getRule(int id);

    RuleDto createRule(RuleDto rule);

    RuleDto updateRule(int id, RuleDto rule);

    void deleteRule(int id);
}
