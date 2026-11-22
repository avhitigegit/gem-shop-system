package lk.nuvi.gemshop.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a text field encrypted: {@code @Convert(converter = EncryptedString.class) private String passport;}
 * (column type {@code text}). The application reads and writes plain text; the database only holds {@code v1:…}.
 */
@Converter
public class EncryptedString implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String plain) {
        return plain == null || plain.isEmpty() ? plain : FieldCrypto.get().encrypt(plain);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return stored == null || stored.isEmpty() ? stored : FieldCrypto.get().decrypt(stored);
    }
}
