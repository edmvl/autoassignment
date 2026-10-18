package ru.gpb.jira.plugin.autoassignment.servlet;

import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves the auto-assignment configuration page.
 */
@Component
public class AssignmentConfigServlet extends HttpServlet {

    private static final String PLUGIN_KEY = "ru.gpb.jira.plugin.synchronizer";

    private final TemplateRenderer templateRenderer;
    private final JiraAuthenticationContext authenticationContext;
    private final PermissionManager permissionManager;
    private final ProjectManager projectManager;
    private final WebResourceManager webResourceManager;

    @Autowired
    public AssignmentConfigServlet(
            @ComponentImport TemplateRenderer templateRenderer,
            @ComponentImport JiraAuthenticationContext authenticationContext,
            @ComponentImport PermissionManager permissionManager,
            @ComponentImport ProjectManager projectManager,
            @ComponentImport WebResourceManager webResourceManager) {
        this.templateRenderer = templateRenderer;
        this.authenticationContext = authenticationContext;
        this.permissionManager = permissionManager;
        this.projectManager = projectManager;
        this.webResourceManager = webResourceManager;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null) {
            String destination = request.getRequestURI()
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            response.sendRedirect(request.getContextPath()
                    + "/login.jsp?os_destination=" + URLEncoder.encode(destination, "UTF-8"));
            return;
        }

        String projectKey = request.getParameter("projectKey");

        Map<String, Object> params = new HashMap<>();
        params.put("projectKey", projectKey == null ? "" : projectKey);
        params.put("userDisplayName", user.getDisplayName());

        if (projectKey != null && !projectKey.trim().isEmpty() && !isProjectAdmin(projectKey, user)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            params.put("accessDenied", true);
            render(request, response, params);
            return;
        }

        render(request, response, params);
    }

    private boolean isProjectAdmin(String projectKey, ApplicationUser user) {
        Project project = projectManager.getProjectByCurrentKey(projectKey);
        return project != null
                && permissionManager.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, user);
    }

    private void render(HttpServletRequest request, HttpServletResponse response, Map<String, Object> params)
            throws IOException {
        webResourceManager.requireResource("com.atlassian.auiplugin:ajs");
        webResourceManager.requireResource(PLUGIN_KEY + ":autoassignment-resources");
        params.put("webResourceManager", webResourceManager);

        response.setContentType("text/html; charset=UTF-8");
        templateRenderer.render("templates/autoassignment-config.vm", params, response.getWriter());
    }
}
