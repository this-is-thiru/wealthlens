package com.thiru.wealthlens.shared.config.mongodb;

import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.convert.PropertyValueConverterFactory;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class MongoConfig {

    /**
     * The date converters, and property value converters ({@code @ValueConverter}) made as Spring beans, so that one can be handed
     * what it needs — a field cipher's key — rather than build it itself (D-447).
     */
    @Bean
    public MongoCustomConversions mongoCustomConversions(BeanFactory beanFactory) {
        return MongoCustomConversions.create(adapter -> adapter
                .registerConverters(List.of(new LocalDateToDateConverter(), new DateToLocalDateConverter()))
                .registerPropertyValueConverterFactory(
                        PropertyValueConverterFactory.caching(PropertyValueConverterFactory.beanFactoryAware(beanFactory))));
    }
}
