package com.evento.common.personaldata;

import com.evento.common.modeling.common.SerializedObject;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalDataModuleTest {

    public static class Address {
        public String street;
        public String city;

        public Address() {
        }

        Address(String street, String city) {
            this.street = street;
            this.city = city;
        }
    }

    public static class CustomerRegistered extends DomainEvent {
        @DataSubject
        public String customerId;
        @PersonalData
        public String name;
        @PersonalData
        public List<String> phones;
        @PersonalData
        public Address address;
        @PersonalData
        public int birthYear;
        public int points;

        public CustomerRegistered() {
        }
    }

    /** The same shape, before its fields were declared personal data. */
    public static class LegacyCustomerRegistered extends DomainEvent {
        public String customerId;
        public String name;
    }

    public static class NoSubject extends DomainEvent {
        @PersonalData
        public String name;
    }

    private InMemorySubjectKeyProvider keys;

    @BeforeEach
    void install() {
        keys = new InMemorySubjectKeyProvider();
        SubjectKeys.install(keys);
    }

    @AfterEach
    void uninstall() {
        SubjectKeys.uninstall(keys);
    }

    private static CustomerRegistered registered(String id) {
        var e = new CustomerRegistered();
        e.customerId = id;
        e.name = "Ada Lovelace";
        e.phones = new ArrayList<>(List.of("+39 333 1234567"));
        e.address = new Address("Via Roma 1", "Milano");
        e.birthYear = 1815;
        e.points = 42;
        return e;
    }

    @Test
    void personalDataIsStoredEncryptedAndReadBack() {
        var stored = new SerializedObject<>(registered("c-1"));

        assertThat(stored.getSerializedObject())
                .doesNotContain("Ada", "333", "Roma", "Milano", "1815")
                .contains("c-1", "evento-pd:1:");
        var read = (CustomerRegistered) stored.getObject();
        assertThat(read.name).isEqualTo("Ada Lovelace");
        assertThat(read.phones).containsExactly("+39 333 1234567");
        assertThat(read.address.city).isEqualTo("Milano");
        assertThat(read.birthYear).isEqualTo(1815);
        assertThat(read.points).isEqualTo(42);
    }

    @Test
    void aForgottenSubjectReadsAsNullAndTheRestStays() {
        var stored = new SerializedObject<>(registered("c-2"));
        var other = new SerializedObject<>(registered("c-3"));

        assertThat(SubjectKeys.forget("c-2")).isTrue();

        var read = (CustomerRegistered) stored.getObject();
        assertThat(read.name).isNull();
        assertThat(read.phones).isNull();
        assertThat(read.address).isNull();
        assertThat(read.birthYear).as("a primitive reads as its default").isZero();
        assertThat(read.customerId).isEqualTo("c-2");
        assertThat(read.points).isEqualTo(42);
        assertThat(((CustomerRegistered) other.getObject()).name).as("other subjects untouched").isEqualTo("Ada Lovelace");
        assertThat(SubjectKeys.forget("c-2")).isFalse();
    }

    @Test
    void aSubjectWrittenAgainAfterBeingForgottenGetsANewKey() {
        var before = new SerializedObject<>(registered("c-4"));
        SubjectKeys.forget("c-4");
        var after = new SerializedObject<>(registered("c-4"));

        assertThat(((CustomerRegistered) before.getObject()).name).as("the old key is gone").isNull();
        assertThat(((CustomerRegistered) after.getObject()).name).isEqualTo("Ada Lovelace");
    }

    @Test
    void valuesWrittenBeforeTheFieldWasPersonalDataAreReadAsTheyAre() {
        var legacy = new LegacyCustomerRegistered();
        legacy.customerId = "c-5";
        legacy.name = "Grace Hopper";
        SerializedObject<?> stored = new SerializedObject<>(legacy);
        stored.setSerializedObject(stored.getSerializedObject().replace(
                LegacyCustomerRegistered.class.getName(), CustomerRegistered.class.getName()));
        stored.setObjectClass(CustomerRegistered.class.getName());

        assertThat(((CustomerRegistered) (Object) stored.getObject()).name).isEqualTo("Grace Hopper");
    }

    @Test
    void personalDataNeedsASubject() {
        assertThatThrownBy(() -> new SerializedObject<>(new NoSubject() {{ name = "x"; }}))
                .hasStackTraceContaining("no @DataSubject field");
        var nullSubject = registered(null);
        assertThatThrownBy(() -> new SerializedObject<>(nullSubject))
                .hasStackTraceContaining("is null: personal data needs its subject");
    }

    @Test
    void withoutAProviderPersonalDataIsNeitherWrittenNorRead() {
        var stored = new SerializedObject<>(registered("c-6"));
        SubjectKeys.uninstall(keys);

        // SerializedObject reports a payload it cannot read as null (and logs why), never in clear.
        assertThat(stored.getObject()).isNull();
        assertThatThrownBy(() -> new SerializedObject<>(registered("c-7")))
                .hasStackTraceContaining("No SubjectKeyProvider installed");
    }

    @Test
    void theCiphertextIsBoundToItsSubject() {
        var stored = new SerializedObject<>(registered("c-8"));
        new SerializedObject<>(registered("c-9"));
        // The value of c-8 relabelled as c-9's: c-9's key does not open it.
        var relabelled = stored.getSerializedObject().replace(
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("c-8".getBytes()),
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("c-9".getBytes()));
        stored.setSerializedObject(relabelled);

        assertThat(((CustomerRegistered) stored.getObject()).name).isNull();
    }
}
