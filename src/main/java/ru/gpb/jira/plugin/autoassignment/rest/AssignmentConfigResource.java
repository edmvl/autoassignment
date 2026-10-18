package ru.gpb.jira.plugin.autoassignment.rest;

import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.FieldException;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.NavigableField;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gpb.jira.plugin.autoassignment.dto.FieldDto;
import ru.gpb.jira.plugin.autoassignment.dto.RuleDto;
import ru.gpb.jira.plugin.autoassignment.dto.SettingsDto;
import ru.gpb.jira.plugin.autoassignment.dto.UserDto;
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
import java.util.List;

/**
 * REST API for the auto-assignment configuration page.
 */
@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Component
public class AssignmentConfigResource {

    private final RuleService ruleService;
    private final SettingsService settingsService;
    private final FieldManager fieldManager;
    private final CustomFieldManager customFieldManager;
    private final UserManager userManager;
    private final ProjectManager projectManager;
    private final PermissionManager permissionManager;
    private final JiraAuthenticationContext authenticationContext;

    @Autowired
    public AssignmentConfigResource(
            RuleService ruleService,
            SettingsService settingsService,
            @ComponentImport FieldManager fieldManager,
            @ComponentImport CustomFieldManager customFieldManager,
            @ComponentImport UserManager userManager,
            @ComponentImport ProjectManager projectManager,
            @ComponentImport PermissionManager permissionManager,
            @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.ruleService = ruleService;
        this.settingsService = settingsService;
        this.fieldManager = fieldManager;
        this.customFieldManager = customFieldManager;
        this.userManager = userManager;
        this.projectManager = projectManager;
        this.permissionManager = permissionManager;
        this.authenticationContext = authenticationContext;
    }

    @GET
    @Path("fields")
    public Response getFields() {
        requireLoggedIn();
        List<FieldDto> fields = new ArrayList<>();
        try {
            for (NavigableField field : fieldManager.getAllAvailableNavigableFields()) {
                fields.add(new FieldDto(field.getId(), field.getName(), fieldType(field)));
            }
        } catch (FieldException e) {
            return Response.serverError().entity("Failed to list fields: " + e.getMessage()).build();
        }
        return Response.ok(fields).build();
    }

    @GET
    @Path("users")
    public Response getUsers() {
        requireLoggedIn();
        List<UserDto> users = new ArrayList<>();
        for (ApplicationUser user : userManager.getAllApplicationUsers()) {
            if (user.isActive()) {
                users.add(new UserDto(user.getName(), user.getDisplayName()));
            }
        }
        return Response.ok(users).build();
    }

    @GET
    @Path("config")
    public Response getConfig() {
        requireLoggedIn();
        return Response.ok(new SettingsDto(
                settingsService.isEnabled(),
                settingsService.getCoAssigneeFieldId())).build();
    }

    @PUT
    @Path("config")
    public Response updateConfig(SettingsDto dto) {
        requireGlobalAdmin();
        settingsService.setEnabled(dto.isEnabled());
        settingsService.setCoAssigneeFieldId(dto.getCoAssigneeFieldId());
        return Response.ok(new SettingsDto(
                settingsService.isEnabled(),
                settingsService.getCoAssigneeFieldId())).build();
    }

    @GET
    @Path("rules")
    public Response getRules(@QueryParam("projectKey") String projectKey) {
        requireProjectAdmin(projectKey);
        return Response.ok(ruleService.getRules(projectKey)).build();
    }

    @POST
    @Path("rules")
    public Response createRule(RuleDto dto) {
        requireProjectAdmin(dto.getProjectKey());
        return Response.status(Response.Status.CREATED).entity(ruleService.createRule(dto)).build();
    }

    @PUT
    @Path("rules/{id}")
    public Response updateRule(@PathParam("id") int id, RuleDto dto) {
        requireProjectAdmin(dto.getProjectKey());
        try {
            return Response.ok(ruleService.updateRule(id, dto)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("rules/{id}")
    public Response deleteRule(@PathParam("id") int id, @QueryParam("projectKey") String projectKey) {
        requireProjectAdmin(projectKey);
        ruleService.deleteRule(id);
        return Response.noContent().build();
    }

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
