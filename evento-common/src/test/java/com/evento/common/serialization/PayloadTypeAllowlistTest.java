package com.evento.common.serialization;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.HostilePayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link PayloadTypeAllowlist}: open by default (backwards compatible) and a strict
 * package allowlist when {@code evento.serialization.allowed-packages} is configured.
 */
class PayloadTypeAllowlistTest {

    /** Root carrier with an Object field, so default typing (NON_FINAL) emits a discriminator for its value. */
    public static class Holder implements Serializable {
        public Object value;
        public Holder() {}
        public Holder(Object value) { this.value = value; }
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(PayloadTypeAllowlist.PROPERTY);
        PayloadTypeAllowlist.resetForTests();
    }

    private static ObjectMapper mapperWithCurrentAllowlist() {
        var om = new ObjectMapper();
        om.activateDefaultTyping(PayloadTypeAllowlist.build(), ObjectMapper.DefaultTyping.NON_FINAL);
        return om;
    }

    @Test
    void openByDefaultAcceptsAnySerializable() throws Exception {
        System.clearProperty(PayloadTypeAllowlist.PROPERTY);
        var om = mapperWithCurrentAllowlist();

        byte[] bytes = om.writeValueAsBytes(new Holder(new HostilePayload("x")));
        Holder back = om.readValue(bytes, Holder.class);

        assertThat(back.value).isInstanceOf(HostilePayload.class);
        assertThat(((HostilePayload) back.value).name).isEqualTo("x");
    }

    @Test
    void strictModeRejectsTypeOutsideAllowlist() throws Exception {
        // Produce a payload whose discriminator names org.example.HostilePayload using the open mapper.
        System.clearProperty(PayloadTypeAllowlist.PROPERTY);
        byte[] hostileBytes = mapperWithCurrentAllowlist().writeValueAsBytes(new Holder(new HostilePayload("x")));

        // Now read it under a strict allowlist that does NOT include org.example.
        System.setProperty(PayloadTypeAllowlist.PROPERTY, "com.acme.app");
        var strict = mapperWithCurrentAllowlist();

        assertThatThrownBy(() -> strict.readValue(hostileBytes, Holder.class))
                .isInstanceOf(JsonMappingException.class)
                .hasMessageContaining("org.example.HostilePayload");
    }

    @Test
    void strictModeAllowsFrameworkJdkAndConfiguredPackages() throws Exception {
        System.setProperty(PayloadTypeAllowlist.PROPERTY, "org.example");
        var om = mapperWithCurrentAllowlist();

        // configured application package
        byte[] appBytes = om.writeValueAsBytes(new Holder(new HostilePayload("ok")));
        assertThat(((HostilePayload) om.readValue(appBytes, Holder.class).value).name).isEqualTo("ok");

        // curated JDK value package (java.util)
        var list = new ArrayList<String>();
        list.add("a");
        byte[] jdkBytes = om.writeValueAsBytes(new Holder(list));
        assertThat(om.readValue(jdkBytes, Holder.class).value).isEqualTo(list);
    }

    @Test
    void restrictToAppliesToMappersBuiltBefore() throws Exception {
        var om = mapperWithCurrentAllowlist();
        byte[] hostileBytes = om.writeValueAsBytes(new Holder(new HostilePayload("x")));
        assertThat(PayloadTypeAllowlist.isRestricted()).isFalse();

        PayloadTypeAllowlist.restrictTo(List.of("com.acme.app"));

        assertThat(PayloadTypeAllowlist.isRestricted()).isTrue();
        // The mapper built while open refuses it now.
        assertThatThrownBy(() -> om.readValue(hostileBytes, Holder.class))
                .isInstanceOf(JsonMappingException.class)
                .hasMessageContaining("org.example.HostilePayload");
    }

    @Test
    void restrictToAcceptsItsOwnPackagesAndAddsToTheConfiguredOnes() throws Exception {
        System.setProperty(PayloadTypeAllowlist.PROPERTY, "com.acme.app");
        PayloadTypeAllowlist.restrictTo(List.of("org.example"));
        var om = mapperWithCurrentAllowlist();
        byte[] bytes = om.writeValueAsBytes(new Holder(new HostilePayload("ok")));
        assertThat(((HostilePayload) om.readValue(bytes, Holder.class).value).name).isEqualTo("ok");
        assertThat(PayloadTypeAllowlist.applicationPackages()).containsExactly("com.acme.app", "org.example.");
    }
}
