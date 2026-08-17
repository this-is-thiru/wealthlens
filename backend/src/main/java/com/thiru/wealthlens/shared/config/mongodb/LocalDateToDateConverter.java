package com.thiru.wealthlens.shared.config.mongodb;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import org.springframework.core.convert.converter.Converter;

public class LocalDateToDateConverter implements Converter<LocalDate, Date> {

    @Override
    public Date convert(LocalDate source) {
        // Convert LocalDate to a Date object at midnight UTC
        return Date.from(source.atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
