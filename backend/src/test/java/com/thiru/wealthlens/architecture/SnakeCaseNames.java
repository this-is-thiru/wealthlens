package com.thiru.wealthlens.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.util.regex.Pattern;

/**
 * The names a table and its columns are stored under are snake_case (CLAUDE.md). JPA applies no naming strategy that would
 * catch a camelCase name, and the schema's Flyway DDL is snake_case, so a camelCase name in an annotation names a column the
 * table lacks — found only when the first query fails (W-337j2: the rule moved here from MongoDB's {@code @Field}).
 */
final class SnakeCaseNames {

    private static final Pattern SNAKE_CASE = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)*$");

    private SnakeCaseNames() {
    }

    static ArchCondition<JavaField> columnName() {
        return new ArchCondition<>("have a snake_case @Column name") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                String name = field.tryGetAnnotationOfType(Column.class).map(Column::name).orElse("");
                if (!name.isEmpty()) {
                    events.add(new SimpleConditionEvent(field, SNAKE_CASE.matcher(name).matches(), String.format(
                            "@Column(name = \"%s\") on %s.%s is not snake_case", name, field.getOwner().getSimpleName(), field.getName())));
                }
            }
        };
    }

    static ArchCondition<JavaClass> tableName() {
        return new ArchCondition<>("have a snake_case @Table name") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                String name = type.tryGetAnnotationOfType(Table.class).map(Table::name).orElse("");
                if (!name.isEmpty()) {
                    events.add(new SimpleConditionEvent(type, SNAKE_CASE.matcher(name).matches(),
                            String.format("@Table(name = \"%s\") on %s is not snake_case", name, type.getSimpleName())));
                }
            }
        };
    }
}
