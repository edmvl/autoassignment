package ru.gpb.jira.plugin.autoassignment.dto;

/**
 * A single condition of a rule: a field, an operator and (optionally) an expected value.
 */
public class ConditionDto {

    private String fieldId;
    private String operator;
    private String value;

    public ConditionDto() {
    }

    public ConditionDto(String fieldId, String operator, String value) {
        this.fieldId = fieldId;
        this.operator = operator;
        this.value = value;
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

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
