package com.evento.server.web.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.actuate.security.AbstractAuthenticationAuditListener;
import org.springframework.boot.actuate.security.AbstractAuthorizationAuditListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.authorization.event.AuthorizationEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterInvocation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The console's audit trail. Spring Boot's audit support records into the
 * {@link LoggingAuditEventRepository} (log line + {@code /actuator/auditevents}, ADMIN only):
 * <ul>
 *   <li>{@code ADMIN_ACTION}: every state-changing console action ({@link AuditedAdminActions});</li>
 *   <li>{@code AUTHENTICATION_FAILURE}: a wrong Basic password, a failed OIDC sign-in;</li>
 *   <li>{@code AUTHENTICATION_SUCCESS}, {@code LOGOUT}: OIDC sign-in and sign-out. Basic
 *       authenticates on every request, so its successes are not recorded;</li>
 *   <li>{@code AUTHORIZATION_FAILURE}: a signed-in user refused an action its roles do not grant.
 *       Anonymous refusals (the GUI before sign-in, scanners) are left out.</li>
 * </ul>
 * {@code management.auditevents.enabled=false} turns all of it off.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "management.auditevents.enabled", havingValue = "true", matchIfMissing = true)
public class AuditConfiguration {

    @Bean
    AuditEventRepository auditEventRepository(@Value("${evento.server.audit.capacity:1000}") int capacity) {
        return new LoggingAuditEventRepository(capacity);
    }

    @Bean
    AuditedAdminActions auditedAdminActions(ApplicationEventPublisher publisher) {
        return new AuditedAdminActions(publisher);
    }

    /** Publishes the refusals of the URL and method security ({@code @Secured}) as events. */
    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }

    @Bean
    AbstractAuthenticationAuditListener authenticationAuditListener() {
        return new AuthenticationAudit();
    }

    @Bean
    AbstractAuthorizationAuditListener authorizationAuditListener() {
        return new AuthorizationAudit();
    }

    static final class AuthenticationAudit extends AbstractAuthenticationAuditListener {
        @Override
        public void onApplicationEvent(AbstractAuthenticationEvent event) {
            var auth = event.getAuthentication();
            var data = requestData();
            String type;
            if (event instanceof AbstractAuthenticationFailureEvent failure) {
                type = "AUTHENTICATION_FAILURE";
                data.put("error", failure.getException().getClass().getSimpleName());
                data.put("message", failure.getException().getMessage());
            } else if (event instanceof LogoutSuccessEvent) {
                type = "LOGOUT";
            } else if (event instanceof AuthenticationSuccessEvent && !(auth instanceof UsernamePasswordAuthenticationToken)) {
                type = "AUTHENTICATION_SUCCESS";
            } else {
                return;
            }
            publish(new AuditEvent(auth.getName(), type, data));
        }
    }

    static final class AuthorizationAudit extends AbstractAuthorizationAuditListener {
        @Override
        public void onApplicationEvent(AuthorizationEvent event) {
            if (!(event instanceof AuthorizationDeniedEvent<?> denied)) return;
            Authentication auth = denied.getAuthentication().get();
            if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) return;
            var data = requestData();
            if (denied.getSource() instanceof FilterInvocation invocation) {
                data.put("request", invocation.getRequest().getMethod() + " " + invocation.getRequestUrl());
            }
            publish(new AuditEvent(auth.getName(), "AUTHORIZATION_FAILURE", data));
        }
    }

    private static Map<String, Object> requestData() {
        var data = new LinkedHashMap<String, Object>();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var request = attributes.getRequest();
            data.put("request", request.getMethod() + " " + request.getRequestURI());
            data.put("remoteAddress", request.getRemoteAddr());
        }
        return data;
    }
}
