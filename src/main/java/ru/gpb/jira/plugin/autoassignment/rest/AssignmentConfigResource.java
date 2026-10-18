package ru.gpb.jira.plugin.autoassignment.rest;

import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.bc.project.component.ProjectComponentManager;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.context.IssueContext;
import com.atlassian.jira.issue.context.IssueContextImpl;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.FieldException;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.NavigableField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gpb.jira.plugin.autoassignment.dto.ConditionDto;
import ru.gpb.jira.plugin.autoassignment.dto.RuleDto;
import ru.gpb.jira.plugin.autoassignment.service.RuleService;
import ru.gpb.jira.plugin.autoassignment.service.SettingsService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for the auto-assignment configuration pages.
 *
 * <p>Request and response bodies are plain maps/lists on purpose: JIRA's REST layer
 * uses Jackson 1.x and does not auto-detect bean properties, so serializing custom
 * DTOs fails with "no properties discovered". Maps are handled natively by Jackson.</p>
 */
@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Component
@ExportAsService
public class AssignmentConfigResource {

    private final RuleService ruleService;
    private final SettingsService settingsService;
    private final FieldManager fieldManager;
    private final CustomFieldManager customFieldManager;
    private final UserManager userManager;
    private final ProjectManager projectManager;
    private final PermissionManager permissionManager;
    private final JiraAuthenticationContext authenticationContext;
    private final ConstantsManager constantsManager;
    private final ProjectComponentManager projectComponentManager;
    private final VersionManager versionManager;

    @Autowired
    public AssignmentConfigResource(
            RuleService ruleService,
            SettingsService settingsService,
            @ComponentImport FieldManager fieldManager,
            @ComponentImport CustomFieldManager customFieldManager,
            @ComponentImport UserManager userManager,
            @ComponentImport ProjectManager projectManager,
            @ComponentImport PermissionManager permissionManager,
            @ComponentImport JiraAuthenticationContext authenticationContext,
            @ComponentImport ConstantsManager constantsManager,
            @ComponentImport ProjectComponentManager projectComponentManager,
            @ComponentImport VersionManager versionManager) {
        this.ruleService = ruleService;
        this.settingsService = settingsService;
        this.fieldManager = fieldManager;
        this.customFieldManager = customFieldManager;
        this.userManager = userManager;
        this.projectManager = projectManager;
        this.permissionManager = permissionManager;
        this.authenticationContext = authenticationContext;
        this.constantsManager = constantsManager;
        this.projectComponentManager = projectComponentManager;
        this.versionManager = versionManager;
    }

    @GET
    @Path("fields")
    public Response getFields() {
        requireLoggedIn();
        List<Map<String, Object>> fields = new ArrayList<>();
        try {
            for (NavigableField field : fieldManager.getAllAvailableNavigableFields()) {
                // The project is an implicit part of every rule (rules are stored per project),
                // so it must not be offered as a selectable condition field.
                if (IssueFieldConstants.PROJECT.equals(field.getId())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", field.getId());
                item.put("name", field.getName());
                item.put("type", fieldType(field));
                fields.add(item);
            }
        } catch (FieldException e) {
            return Response.serverError().entity("Failed to list fields: " + e.getMessage()).build();
        }
        return Response.ok(fields).build();
    }

    /**
     * Returns the selectable values for a field (options for option-based custom fields,
     * fixed values for priority/status/resolution/etc.). Empty list = free text.
     */
    @GET
    @Path("fields/{fieldId}/values")
    public Response getFieldValues(@PathParam("fieldId") String fieldId,
                                   @QueryParam("projectKey") String projectKey) {
        requireLoggedIn();
        Project project = projectKey == null ? null : projectManager.getProjectByCurrentKey(projectKey);
        List<Map<String, Object>> values = new ArrayList<>();

        CustomField customField = customFieldManager.getCustomFieldObject(fieldId);
        if (customField != null) {
            if (isUserPicker(customField)) {
                values.addAll(userValues());
            } else {
                values.addAll(customFieldOptions(customField, project));
            }
        } else {
            values.addAll(systemFieldValues(fieldId, project));
        }
        return Response.ok(values).build();
    }

    @GET
    @Path("users")
    public Response getUsers() {
        requireLoggedIn();
        List<Map<String, Object>> users = new ArrayList<>();
        for (ApplicationUser user : userManager.getAllApplicationUsers()) {
            if (user.isActive()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", user.getName());
                item.put("displayName", user.getDisplayName());
                users.add(item);
            }
        }
        return Response.ok(users).build();
    }

    @GET
    @Path("config")
    public Response getConfig() {
        requireLoggedIn();
        return Response.ok(settingsMap()).build();
    }

    @PUT
    @Path("config")
    public Response updateConfig(Map<String, Object> body) {
        requireGlobalAdmin();
        settingsService.setEnabled(asBoolean(body.get("enabled")));
        settingsService.setCoAssigneeFieldId(asString(body.get("coAssigneeFieldId")));
        return Response.ok(settingsMap()).build();
    }

    @GET
    @Path("rules")
    public Response getRules(@QueryParam("projectKey") String projectKey) {
        requireProjectAdmin(projectKey);
        List<Map<String, Object>> result = new ArrayList<>();
        for (RuleDto rule : ruleService.getRules(projectKey)) {
            result.add(ruleMap(rule));
        }
        return Response.ok(result).build();
    }

    @POST
    @Path("rules")
    public Response createRule(Map<String, Object> body) {
        try {
            RuleDto dto = ruleFromMap(body);
            requireProjectAdmin(dto.getProjectKey());
            return Response.status(Response.Status.CREATED)
                    .entity(ruleMap(ruleService.createRule(dto))).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (RuntimeException e) {
            return Response.serverError().entity(errorMap(e)).build();
        }
    }

    @PUT
    @Path("rules/{id}")
    public Response updateRule(@PathParam("id") int id, Map<String, Object> body) {
        try {
            RuleDto dto = ruleFromMap(body);
            requireProjectAdmin(dto.getProjectKey());
            return Response.ok(ruleMap(ruleService.updateRule(id, dto))).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(errorMap(e)).build();
        } catch (RuntimeException e) {
            return Response.serverError().entity(errorMap(e)).build();
        }
    }

    @DELETE
    @Path("rules/{id}")
    public Response deleteRule(@PathParam("id") int id, @QueryParam("projectKey") String projectKey) {
        try {
            requireProjectAdmin(projectKey);
            ruleService.deleteRule(id);
            return Response.noContent().build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (RuntimeException e) {
            return Response.serverError().entity(errorMap(e)).build();
        }
    }

    private Map<String, Object> errorMap(Throwable e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("error", e.getClass().getSimpleName());
        map.put("message", e.getMessage() == null ? String.valueOf(e) : e.getMessage());
        return map;
    }

    // ------------------------------------------------------------ conversion

    private Map<String, Object> settingsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", settingsService.isEnabled());
        map.put("coAssigneeFieldId", settingsService.getCoAssigneeFieldId());
        return map;
    }

    private Map<String, Object> ruleMap(RuleDto rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rule.getId());
        map.put("projectKey", rule.getProjectKey());
        map.put("name", rule.getName());
        map.put("assignees", rule.getAssignees());
        map.put("coAssignees", rule.getCoAssignees());
        map.put("watchers", rule.getWatchers());

        List<Map<String, Object>> conditions = new ArrayList<>();
        if (rule.getConditions() != null) {
            for (ConditionDto condition : rule.getConditions()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fieldId", condition.getFieldId());
                item.put("operator", condition.getOperator());
                item.put("values", condition.getValues());
                conditions.add(item);
            }
        }
        map.put("conditions", conditions);
        map.put("sortOrder", rule.getSortOrder());
        map.put("enabled", rule.isEnabled());
        return map;
    }

    private RuleDto ruleFromMap(Map<String, Object> body) {
        RuleDto rule = new RuleDto();
        if (body == null) {
            return rule;
        }
        rule.setProjectKey(asString(body.get("projectKey")));
        rule.setName(asString(body.get("name")));
        rule.setAssignees(asStringList(body.get("assignees")));
        rule.setCoAssignees(asStringList(body.get("coAssignees")));
        rule.setWatchers(asStringList(body.get("watchers")));
        rule.setConditions(asConditions(body.get("conditions")));
        rule.setSortOrder(asInt(body.get("sortOrder")));
        rule.setEnabled(asBoolean(body.get("enabled")));
        return rule;
    }

    private List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    private List<ConditionDto> asConditions(Object value) {
        List<ConditionDto> result = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                if (item instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) item;
                    result.add(new ConditionDto(
                            asString(map.get("fieldId")),
                            asString(map.get("operator")),
                            asStringList(map.get("values"))));
                }
            }
        }
        return result;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    // ------------------------------------------------------- field values

    private boolean isUserPicker(CustomField customField) {
        String key = customField.getCustomFieldType().getKey();
        return key != null && key.contains("userpicker");
    }

    private List<Map<String, Object>> userValues() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ApplicationUser user : userManager.getAllApplicationUsers()) {
            if (user.isActive()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("value", user.getName());
                item.put("label", user.getDisplayName());
                result.add(item);
            }
        }
        return result;
    }

    private List<Map<String, Object>> customFieldOptions(CustomField customField, Project project) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            IssueContext issueContext = project != null ? new IssueContextImpl(project, null) : null;
            FieldConfig config = customField.getRelevantConfig(issueContext);
            if (config == null) {
                config = firstConfig(customField);
            }
            if (config == null) {
                return result;
            }
            Options options = customField.getOptions(null, config, null);
            if (options != null) {
                addOptions(result, options.getRootOptions());
                if (result.isEmpty()) {
                    addOptions(result, options);
                }
            }
        } catch (RuntimeException e) {
            // Field has no options (e.g. free text / user picker) or context is missing.
        }
        return result;
    }

    private FieldConfig firstConfig(CustomField customField) {
        List<FieldConfigScheme> schemes = customField.getConfigurationSchemes();
        if (schemes == null) {
            return null;
        }
        for (FieldConfigScheme scheme : schemes) {
            Map<String, FieldConfig> configs = scheme.getConfigs();
            if (configs != null && !configs.isEmpty()) {
                return configs.values().iterator().next();
            }
        }
        return null;
    }

    private void addOptions(List<Map<String, Object>> result, Collection<Option> options) {
        if (options == null) {
            return;
        }
        for (Option option : options) {
            if (option == null || Boolean.TRUE.equals(option.getDisabled())) {
                continue;
            }
            result.add(value(option.getValue()));
        }
    }

    private List<Map<String, Object>> systemFieldValues(String fieldId, Project project) {
        List<Map<String, Object>> result = new ArrayList<>();
        Collection<?> objects = null;
        switch (fieldId) {
            case IssueFieldConstants.ASSIGNEE:
            case IssueFieldConstants.REPORTER:
                return userValues();
            case IssueFieldConstants.PRIORITY:
                objects = constantsManager.getPriorityObjects();
                break;
            case IssueFieldConstants.ISSUE_TYPE:
                objects = constantsManager.getAllIssueTypeObjects();
                break;
            case IssueFieldConstants.STATUS:
                objects = constantsManager.getStatusObjects();
                break;
            case IssueFieldConstants.RESOLUTION:
                objects = constantsManager.getResolutionObjects();
                break;
            case IssueFieldConstants.COMPONENTS:
                if (project != null) {
                    objects = projectComponentManager.findAllForProject(project.getId());
                }
                break;
            case IssueFieldConstants.FIX_FOR_VERSIONS:
            case IssueFieldConstants.AFFECTED_VERSIONS:
                if (project != null) {
                    objects = versionManager.getVersions(project.getId());
                }
                break;
            default:
                return result;
        }
        if (objects != null) {
            for (Object object : objects) {
                String name = objectName(object);
                if (name != null) {
                    result.add(value(name));
                }
            }
        }
        return result;
    }

    private String objectName(Object object) {
        if (object instanceof Priority) {
            return ((Priority) object).getName();
        }
        if (object instanceof IssueType) {
            return ((IssueType) object).getName();
        }
        if (object instanceof Status) {
            return ((Status) object).getName();
        }
        if (object instanceof Resolution) {
            return ((Resolution) object).getName();
        }
        if (object instanceof ProjectComponent) {
            return ((ProjectComponent) object).getName();
        }
        if (object instanceof Version) {
            return ((Version) object).getName();
        }
        return null;
    }

    private Map<String, Object> value(String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", text);
        item.put("label", text);
        return item;
    }

    // ------------------------------------------------------------- helpers

    private String fieldType(NavigableField field) {
        if (field instanceof CustomField) {
            return ((CustomField) field).getCustomFieldType().getKey();
        }
        return "system";
    }

    private ApplicationUser requireLoggedIn() {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        return user;
    }

    private void requireGlobalAdmin() {
        ApplicationUser user = requireLoggedIn();
        if (!permissionManager.hasPermission(Permissions.ADMINISTER, user)) {
            throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).build());
        }
    }

    private void requireProjectAdmin(String projectKey) {
        ApplicationUser user = requireLoggedIn();
        if (projectKey == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }
        Project project = projectManager.getProjectByCurrentKey(projectKey);
        if (project == null) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }
        if (!permissionManager.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, user)) {
            throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).build());
        }
    }
}
