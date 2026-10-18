package ru.gpb.jira.plugin.autoassignment.listener;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gpb.jira.plugin.autoassignment.engine.AssignmentEngine;

/**
 * Listens for the issue-created event and delegates to the assignment engine.
 */
@Component
public class IssueCreatedListener implements InitializingBean, DisposableBean {

    private final EventPublisher eventPublisher;
    private final AssignmentEngine assignmentEngine;

    @Autowired
    public IssueCreatedListener(@ComponentImport EventPublisher eventPublisher,
                                AssignmentEngine assignmentEngine) {
        this.eventPublisher = eventPublisher;
        this.assignmentEngine = assignmentEngine;
    }

    @Override
    public void afterPropertiesSet() {
        eventPublisher.register(this);
    }

    @Override
    public void destroy() {
        eventPublisher.unregister(this);
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
        if (EventType.ISSUE_CREATED_ID.equals(issueEvent.getEventTypeId())) {
            assignmentEngine.onIssueCreated(issueEvent);
        }
    }
}
