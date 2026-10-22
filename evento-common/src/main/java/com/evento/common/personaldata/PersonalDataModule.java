package com.evento.common.personaldata;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encrypts the {@link PersonalData} fields of payloads as the payload mapper writes them, and
 * decrypts them as it reads them (3.0, crypto-shredding). The value is first written as the field
 * would be, type information included, then encrypted with {@link PersonalDataCipher}; reading
 * reverses it, and a value whose key is gone reads as the field's null value.
 */
public final class PersonalDataModule extends SimpleModule {

    private static final JsonFactory JSON = new JsonFactory();
    private static final Map<Class<?>, Optional<Field>> SUBJECT_FIELDS = new ConcurrentHashMap<>();

    public PersonalDataModule() {
        super("evento-personal-data");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.insertAnnotationIntrospector(new NopAnnotationIntrospector() {
            @Override
            public Object findSerializer(Annotated a) {
                return a.hasAnnotation(PersonalData.class) ? new Encrypting(null) : null;
            }

            @Override
            public Object findDeserializer(Annotated a) {
                return a.hasAnnotation(PersonalData.class) ? new Decrypting(null) : null;
            }
        });
    }

    /** The subject of {@code bean}'s personal data: its {@link DataSubject} field. */
    static String subjectOf(Object bean) {
        if (bean == null) throw new IllegalStateException("@PersonalData outside an object");
        var field = SUBJECT_FIELDS.computeIfAbsent(bean.getClass(), PersonalDataModule::findSubjectField)
                .orElseThrow(() -> new IllegalStateException(bean.getClass().getName()
                        + " has @PersonalData fields but no @DataSubject field"));
        try {
            var subject = field.get(bean);
            if (subject == null) {
                throw new IllegalStateException(bean.getClass().getName() + "." + field.getName()
                        + " (@DataSubject) is null: personal data needs its subject");
            }
            return subject.toString();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<Field> findSubjectField(Class<?> type) {
        for (var c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (var f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(DataSubject.class)) {
                    f.setAccessible(true);
                    return Optional.of(f);
                }
            }
        }
        return Optional.empty();
    }

    static final class Encrypting extends JsonSerializer<Object> implements ContextualSerializer {

        private final JsonSerializer<Object> delegate;

        Encrypting(JsonSerializer<Object> delegate) {
            this.delegate = delegate;
        }

        @Override
        public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
                throws JsonMappingException {
            return new Encrypting(prov.findValueSerializer(property.getType(), property));
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider prov) throws IOException {
            write(value, gen, prov, null);
        }

        @Override
        public void serializeWithType(Object value, JsonGenerator gen, SerializerProvider prov, TypeSerializer typeSer)
                throws IOException {
            write(value, gen, prov, typeSer);
        }

        private void write(Object value, JsonGenerator gen, SerializerProvider prov, TypeSerializer typeSer)
                throws IOException {
            String subject = subjectOf(gen.currentValue());
            var buffer = new TokenBuffer(gen.getCodec(), false);
            if (typeSer == null) delegate.serialize(value, buffer, prov);
            else delegate.serializeWithType(value, buffer, prov, typeSer);
            var json = new StringWriter();
            try (var out = JSON.createGenerator(json)) {
                buffer.serialize(out);
            }
            byte[] key = SubjectKeys.required().key(subject, true)
                    .orElseThrow(() -> new IllegalStateException("no key for subject " + subject));
            gen.writeString(PersonalDataCipher.encrypt(subject, key, json.toString()));
        }
    }

    static final class Decrypting extends JsonDeserializer<Object> implements ContextualDeserializer {

        private final JsonDeserializer<Object> delegate;

        Decrypting(JsonDeserializer<Object> delegate) {
            this.delegate = delegate;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
                throws JsonMappingException {
            return new Decrypting(ctxt.findContextualValueDeserializer(property.getType(), property));
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return read(p, ctxt, null);
        }

        @Override
        public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeser)
                throws IOException {
            return read(p, ctxt, typeDeser);
        }

        private Object read(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeser) throws IOException {
            if (p.currentToken() != JsonToken.VALUE_STRING || !PersonalDataCipher.isEncrypted(p.getText())) {
                // Written before the field was personal data: as it is.
                return typeDeser == null ? delegate.deserialize(p, ctxt) : delegate.deserializeWithType(p, ctxt, typeDeser);
            }
            String stored = p.getText();
            String subject = PersonalDataCipher.subjectOf(stored);
            var plaintext = SubjectKeys.required().key(subject, false)
                    .flatMap(key -> PersonalDataCipher.decrypt(stored, key));
            if (plaintext.isEmpty()) return delegate.getNullValue(ctxt);
            try (var inner = JSON.createParser(plaintext.get())) {
                inner.setCodec(p.getCodec());
                inner.nextToken();
                return typeDeser == null ? delegate.deserialize(inner, ctxt) : delegate.deserializeWithType(inner, ctxt, typeDeser);
            }
        }

        @Override
        public Object getNullValue(DeserializationContext ctxt) throws JsonMappingException {
            return delegate == null ? null : delegate.getNullValue(ctxt);
        }
    }
}
