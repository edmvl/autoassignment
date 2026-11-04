package ru.gpb.jira.plugin.autoassignment.util;

import ru.gpb.jira.plugin.autoassignment.dto.ConditionDto;
import ru.gpb.jira.plugin.autoassignment.dto.RuleDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Turns rule operations into a list of human readable change lines for the history log.
 */
public final class RuleChangeDescriber {

    private static final String EMPTY = "—";

    private final Function<String, String> fieldName;
    private final Function<String, String> userLabel;

    public RuleChangeDescriber(Function<String, String> fieldName, Function<String, String> userLabel) {
        this.fieldName = fieldName;
        this.userLabel = userLabel;
    }

    /**
     * Full snapshot of a newly created (or deleted) rule.
     */
    public List<String> describeSnapshot(RuleDto rule) {
        List<String> lines = new ArrayList<>();
        lines.add("Название: " + label(rule.getName()));
        addTarget(lines, "Исполнитель", rule.getAssignees());
        addTarget(lines, "Соисполнители", rule.getCoAssignees());
        addTarget(lines, "Наблюдатели", rule.getWatchers());
        if (rule.getConditions() == null || rule.getConditions().isEmpty()) {
            lines.add("Без условий (применяется всегда)");
        } else {
            for (ConditionDto condition : rule.getConditions()) {
                lines.add("Условие: " + conditionText(condition));
            }
        }
        lines.add(rule.isEnabled() ? "Активно" : "Выключено");
        return lines;
    }

    /**
     * Only the differences between the old and the new rule version.
     */
    public List<String> describeChanges(RuleDto before, RuleDto after) {
        List<String> lines = new ArrayList<>();
        if (!Objects.equals(before.getName(), after.getName())) {
            lines.add("Название: " + label(before.getName()) + " → " + label(after.getName()));
        }
        addListDiff(lines, "Исполнитель", before.getAssignees(), after.getAssignees());
        addListDiff(lines, "Соисполнители", before.getCoAssignees(), after.getCoAssignees());
        addListDiff(lines, "Наблюдатели", before.getWatchers(), after.getWatchers());
        addConditionDiff(lines, before.getConditions(), after.getConditions());
        if (before.isEnabled() != after.isEnabled()) {
            lines.add(after.isEnabled() ? "Правило включено" : "Правило выключено");
        }
        return lines;
    }

    public String conditionText(ConditionDto condition) {
        String field = label(fieldName.apply(condition.getFieldId()));
        String operator = operatorLabel(condition.getOperator());
        if ("IS_EMPTY".equals(condition.getOperator()) || "IS_NOT_EMPTY".equals(condition.getOperator())) {
            return field + " " + operator;
        }
        List<String> values = new ArrayList<>();
        for (String value : condition.getValues()) {
            values.add(label(value));
        }
        return field + " " + operator + " " + (values.isEmpty() ? EMPTY : String.join(", ", values));
    }

    private void addConditionDiff(List<String> lines, List<ConditionDto> before, List<ConditionDto> after) {
        List<String> beforeTexts = conditionTexts(before);
        List<String> afterTexts = conditionTexts(after);
        for (String text : afterTexts) {
            if (!beforeTexts.contains(text)) {
                lines.add("Добавлено условие: " + text);
            }
        }
        for (String text : beforeTexts) {
            if (!afterTexts.contains(text)) {
                lines.add("Удалено условие: " + text);
            }
        }
    }

    private List<String> conditionTexts(List<ConditionDto> conditions) {
        List<String> texts = new ArrayList<>();
        if (conditions == null) {
            return texts;
        }
        for (ConditionDto condition : conditions) {
            String text = conditionText(condition);
            if (!texts.contains(text)) {
                texts.add(text);
            }
        }
        return texts;
    }

    private void addListDiff(List<String> lines, String label, List<String> before, List<String> after) {
        Set<String> beforeSet = new LinkedHashSet<>(before == null ? new ArrayList<>() : before);
        Set<String> afterSet = new LinkedHashSet<>(after == null ? new ArrayList<>() : after);
        if (beforeSet.equals(afterSet)) {
            return;
        }
        lines.add(label + ": " + userNames(before) + " → " + userNames(after));
    }

    private void addTarget(List<String> lines, String label, List<String> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        lines.add(label + ": " + userNames(users));
    }

    private String userNames(List<String> users) {
        if (users == null || users.isEmpty()) {
            return EMPTY;
        }
        List<String> labels = new ArrayList<>();
        for (String user : users) {
            labels.add(label(userLabel.apply(user)));
        }
        return String.join(", ", labels);
    }

    private String operatorLabel(String operator) {
        if (operator == null) {
            return "";
        }
        switch (operator) {
            case "EQUALS":
                return "равно";
            case "NOT_EQUALS":
                return "не равно";
            case "CONTAINS":
                return "содержит";
            case "IS_EMPTY":
                return "пусто";
            case "IS_NOT_EMPTY":
                return "не пусто";
            default:
                return operator;
        }
    }

    private static String label(String value) {
        return value == null || value.trim().isEmpty() ? EMPTY : value;
    }
}
