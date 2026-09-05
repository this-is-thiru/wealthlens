package com.thiru.wealthlens.architecture;

import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.regex.Pattern;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Verifies that the value of a {@code @Field} annotation is snake_case.
 *
 * <p>Spring Data offers no naming strategy that would catch this, and a camelCase field name
 * silently creates a second, differently-named column in MongoDB — invisible until a query
 * returns nothing.
 */
final class SnakeCaseFieldCondition extends ArchCondition<JavaField> {

    private static final Pattern SNAKE_CASE = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)*$");

    private SnakeCaseFieldCondition() {
        super("have a snake_case @Field name");
    }

    static SnakeCaseFieldCondition snakeCase() {
        return new SnakeCaseFieldCondition();
    }

    @Override
    public void check(JavaField field, ConditionEvents events) {
        String name = field.tryGetAnnotationOfType(Field.class)
                .map(annotation -> annotation.name().isEmpty() ? annotation.value() : annotation.name())
                .orElse("");

        if (name.isEmpty()) {
            return;
        }

        boolean satisfied = SNAKE_CASE.matcher(name).matches();
        String message = String.format("@Field(\"%s\") on %s.%s is not snake_case",
                name, field.getOwner().getSimpleName(), field.getName());
        events.add(new SimpleConditionEvent(field, satisfied, message));
    }
}
