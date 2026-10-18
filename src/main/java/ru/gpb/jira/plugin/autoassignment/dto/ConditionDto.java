package ru.gpb.jira.plugin.autoassignment.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single condition of a rule: a field, an operator and a set of expected values.
 *
 * <p>{@code values} may contain several entries for predefined-value fields (users,
 * issue types, priorities, options, ...). {@code value} is kept for backward
 * compatibility with rules stored before multi-value support.</p>
 */
public class ConditionDto {

    private String fieldId;
    private String operator;
    private List<String> values = new ArrayList<>();
    private String value;

    public ConditionDto() {
    }

    public ConditionDto(String fieldId, String operator, String value) {
        this.fieldId = fieldId;
        this.operator = operator;
        this.value = value;
        if (value != null) {
            this.values = new ArrayList<>(Collections.singletonList(value));
        }
    }

    public ConditionDto(String fieldId, String operator, List<String> values) {
        this.fieldId = fieldId;
        this.operator = operator;
        this.values = values == null ? new ArrayList<>() : values;
    }

    public String getFieldId() {
        return fieldId;
    }

    public void setFieldId(String fieldId) {
        this.fieldId = fieldId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public List<String> getValues() {
        if ((values == null || values.isEmpty()) && value != null && !value.isEmpty()) {
            return new ArrayList<>(Collections.singletonList(value));
        }
        return values == null ? new ArrayList<>() : values;
    }

    public void setValues(List<String> values) {
        this.values = values == null ? new ArrayList<>() : values;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
