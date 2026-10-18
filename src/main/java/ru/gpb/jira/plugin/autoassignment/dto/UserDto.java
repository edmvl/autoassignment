package ru.gpb.jira.plugin.autoassignment.dto;

/**
 * A JIRA user exposed to the configuration UI.
 */
public class UserDto {

    private String name;
    private String displayName;

    public UserDto() {
    }

    public UserDto(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
