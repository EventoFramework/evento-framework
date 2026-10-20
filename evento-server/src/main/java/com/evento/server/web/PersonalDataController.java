package com.evento.server.web;

import com.evento.server.es.personaldata.SubjectKeyStore;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The right to erasure for {@code @PersonalData} (3.0): forgetting a data subject deletes its key,
 * so its personal data can no longer be read anywhere in the event store. An admin action, in the
 * audit trail like the others. Bundles keep a key they read for their cache period
 * ({@code EventoBundle.Builder#setSubjectKeyCacheTtl}, one minute by default).
 */
@RestController
@RequestMapping("api/personal-data")
public class PersonalDataController {

    private final SubjectKeyStore keys;
    private final AdminActions actions;

    public PersonalDataController(SubjectKeyStore keys, AdminActions actions) {
        this.keys = keys;
        this.actions = actions;
    }

    @DeleteMapping("/subjects/{subject}")
    @Secured("ROLE_ADMIN")
    public Map<String, Object> forget(@PathVariable String subject) {
        var forgotten = new AtomicBoolean();
        actions.run("personal_data.forget", Map.of("subject", subject), () -> forgotten.set(keys.forget(subject)));
        return Map.of("subject", subject, "forgotten", forgotten.get());
    }
}
