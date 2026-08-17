package com.thiru.wealthlens.shared.config.mongodb;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import org.springframework.core.convert.converter.Converter;

public class DateToLocalDateConverter implements Converter<Date, LocalDate> {

    @Override
    public LocalDate convert(Date source) {
        // Convert a Date object to LocalDate
        return source.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
