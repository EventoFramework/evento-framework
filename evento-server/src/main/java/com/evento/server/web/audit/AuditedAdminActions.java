package com.evento.server.web.audit;

import com.evento.server.web.spi.AdminActionListener;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records the console's state-changing actions as {@value #TYPE} audit events: who, what, on which
 * target, from where, and whether it worked. The event is recorded when the action ends, so the
 * outcome is the real one, also for the actions the bundles answer asynchronously.
 */
public class AuditedAdminActions implements AdminActionListener {

    public static final String TYPE = "ADMIN_ACTION";

    private final ApplicationEventPublisher publisher;

    public AuditedAdminActions(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /** Principal and request data are read here, on the request thread. */
    @Override
    public Completion started(String action, Map<String, Object> target) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        var principal = auth == null ? "anonymous" : auth.getName();
        var data = new LinkedHashMap<String, Object>();
        data.put("action", action);
        data.putAll(target);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
            data.put("remoteAddress", request.getRequest().getRemoteAddr());
        }
        return error -> {
            var event = new LinkedHashMap<>(data);
            event.put("outcome", error == null ? "ok" : "failed");
            if (error != null) event.put("error", error.getClass().getSimpleName() + ": " + error.getMessage());
            publisher.publishEvent(new AuditApplicationEvent(new AuditEvent(principal, TYPE, event)));
        };
    }
}
