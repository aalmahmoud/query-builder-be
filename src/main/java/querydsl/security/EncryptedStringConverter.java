package querydsl.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA {@link AttributeConverter} for column-level encryption of String fields.
 * Phase 4 fix 4.15.
 *
 * <p>Used by annotating an entity field with
 * {@code @Convert(converter = EncryptedStringConverter.class)}.
 *
 * <p>Spring Boot / Hibernate 6 automatically wires {@code @Component}-annotated
 * converters via the {@code SpringBeanContainer} integration, so the
 * {@link EncryptionService} dependency is injected through the constructor.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final EncryptionService encryption;

    public EncryptedStringConverter(EncryptionService encryption) {
        this.encryption = encryption;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryption.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryption.decrypt(dbData);
    }
}
