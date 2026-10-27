package com.evento.server.web.audit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.audit.AuditEvent;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAuditEventRepositoryTest {

    @Test
    void oneLineOfKeyValuePairs() {
        var data = new LinkedHashMap<String, Object>();
        data.put("action", "bundle.unregister");
        data.put("bundleId", "orders");
        data.put("error", "IllegalArgumentException: error.bundle.is.null");
        assertThat(LoggingAuditEventRepository.format(new AuditEvent("alice", "ADMIN_ACTION", data)))
                .isEqualTo("event=audit type=ADMIN_ACTION principal=alice action=bundle.unregister bundleId=orders"
                        + " error=\"IllegalArgumentException: error.bundle.is.null\"");
    }

    @Test
    void aCraftedValueCannotForgeALineOrAField() {
        var data = new LinkedHashMap<String, Object>();
        data.put("bundleId", "x\nevent=audit type=ADMIN_ACTION principal=root");
        data.put("consumerId", "a outcome=ok");
        data.put("we ird", "\"quoted\"");
        var line = LoggingAuditEventRepository.format(new AuditEvent("mallory", "ADMIN_ACTION", data));
        assertThat(line).doesNotContain("\n")
                .contains("bundleId=\"x event=audit type=ADMIN_ACTION principal=root\"")
                .contains("consumerId=\"a outcome=ok\"")
                .contains("we_ird=\"\\\"quoted\\\"\"");
    }

    @Test
    void keepsTheLatestEventsForTheEndpoint() {
        var repository = new LoggingAuditEventRepository(2);
        for (var principal : new String[]{"a", "b", "c"}) {
            repository.add(new AuditEvent(principal, "ADMIN_ACTION", new LinkedHashMap<>()));
        }
        assertThat(repository.find(null, null, null)).extracting(AuditEvent::getPrincipal).containsExactly("b", "c");
    }
}
