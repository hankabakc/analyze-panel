package com.hankabakc.analyzepanel.core.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * PiiConverter: JPA varlıklarındaki hassas alanları (Email, Telefon vb.) 
 * veritabanına yazarken otomatik şifreler, okurken çözer.
 */
@Converter
public class PiiConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return EncryptionUtils.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return EncryptionUtils.decrypt(dbData);
    }
}
