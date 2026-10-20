package com.evento.server.web.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.actuate.audit.InMemoryAuditEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Keeps the latest audit events in memory, for {@code /actuator/auditevents}, and writes every one
 * of them to the {@value #LOGGER} logger as one {@code key=value} line. The log is the durable
 * record: route that logger to the SIEM or to an append-only file; the memory is lost on restart.
 */
public class LoggingAuditEventRepository implements AuditEventRepository {

    public static final String LOGGER = "evento.audit";

    private static final Logger log = LoggerFactory.getLogger(LOGGER);

    private final InMemoryAuditEventRepository recent;

    public LoggingAuditEventRepository(int capacity) {
        this.recent = new InMemoryAuditEventRepository(capacity);
    }

    @Override
    public void add(AuditEvent event) {
        recent.add(event);
        if (log.isInfoEnabled()) log.info(format(event));
    }

    @Override
    public List<AuditEvent> find(String principal, Instant after, String type) {
        return recent.find(principal, after, type);
    }

    static String format(AuditEvent event) {
        var line = new StringBuilder("event=audit")
                .append(" type=").append(value(event.getType()))
                .append(" principal=").append(value(event.getPrincipal()));
        for (Map.Entry<String, Object> e : event.getData().entrySet()) {
            line.append(' ').append(key(e.getKey())).append('=').append(value(e.getValue()));
        }
        return line.toString();
    }

    private static String key(String key) {
        return key.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    /**
     * Quotes values with spaces or quotes and drops control characters, so a crafted path variable
     * or user name cannot forge a second audit line or a field of its own.
     */
    private static String value(Object value) {
        var s = String.valueOf(value).replaceAll("\\p{Cntrl}", " ");
        if (s.isEmpty() || s.chars().anyMatch(c -> c == ' ' || c == '"' || c == '=')) {
            return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
        return s;
    }
}
