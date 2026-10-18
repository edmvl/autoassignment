package ru.gpb.jira.plugin.autoassignment.servlet;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
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
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves the global (JIRA administrator) configuration page.
 */
@Component
public class GlobalConfigServlet extends HttpServlet {

    private static final String PLUGIN_KEY = "ru.gpb.jira.plugin.autoassignment";

    private final TemplateRenderer templateRenderer;
    private final JiraAuthenticationContext authenticationContext;
    private final PermissionManager permissionManager;
    private final WebResourceManager webResourceManager;

    @Autowired
    public GlobalConfigServlet(
            @ComponentImport TemplateRenderer templateRenderer,
            @ComponentImport JiraAuthenticationContext authenticationContext,
            @ComponentImport PermissionManager permissionManager,
            @ComponentImport WebResourceManager webResourceManager) {
        this.templateRenderer = templateRenderer;
        this.authenticationContext = authenticationContext;
        this.permissionManager = permissionManager;
        this.webResourceManager = webResourceManager;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null) {
            String destination = request.getRequestURI()
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            response.sendRedirect(request.getContextPath()
                    + "/login.jsp?os_destination=" + URLEncoder.encode(destination, "UTF-8"));
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("userDisplayName", user.getDisplayName());
        if (!permissionManager.hasPermission(Permissions.ADMINISTER, user)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            params.put("accessDenied", true);
        }
        render(response, params);
    }

    private void render(HttpServletResponse response, Map<String, Object> params) throws IOException {
        webResourceManager.requireResource("com.atlassian.auiplugin:ajs");
        webResourceManager.requireResource(PLUGIN_KEY + ":autoassignment-resources");
        String resourcesHtml = webResourceManager.getRequiredResources();

        StringWriter buffer = new StringWriter();
        templateRenderer.render("templates/global-config.vm", params, buffer);
        String html = buffer.toString().replace("<!--AUTOASSIGN_RESOURCES-->", resourcesHtml);
        response.getWriter().write(html);
    }
}
