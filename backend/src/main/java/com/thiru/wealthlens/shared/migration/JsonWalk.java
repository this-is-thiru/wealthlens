package com.thiru.wealthlens.shared.migration;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the JSON a column holds from the stored value, by the column's Java type (W-337g2b1, W-337g2 C2a). A class's or
 * record's stored key is its field's MongoDB {@code @Field} name, else its Java name, and becomes the Java name Jackson writes;
 * a key with no field is refused. The tree is read into the type, which proves it parses, and written by a default
 * {@code JsonMapper} — the form the application's converters write — never through an {@code AttributeConverter}, so nothing
 * is encrypted twice (W-337g2 finding 2).
 */
final class JsonWalk {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String TYPE_HINT = "_class";

    private JsonWalk() {
    }

    static String json(Object stored, Type type, ZoneId writtenZone, String where) {
        JavaType javaType = MAPPER.constructType(type);
        Object value = MAPPER.treeToValue(node(stored, type, Optional.empty(), writtenZone, where), javaType);
        return MAPPER.writeValueAsString(value);
    }

    /** The field a value sits in, for the time table; empty for a value no field holds. */
    private record Owner(Class<?> declaringClass, String field) {
    }

    private static JsonNode node(Object stored, Type type, Optional<Owner> owner, ZoneId writtenZone, String where) {
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (Collection.class.isAssignableFrom(raw)) {
                ArrayNode array = MAPPER.createArrayNode();
                for (Object element : require(stored, List.class, type, where)) {
                    array.add(node(element, arguments[0], owner, writtenZone, where));
                }
                return array;
            }
            if (Map.class.isAssignableFrom(raw)) {
                ObjectNode map = MAPPER.createObjectNode();
                require(stored, Document.class, type, where).forEach((key, value) -> {
                    if (value != null) {
                        map.set(key, node(value, arguments[1], owner, writtenZone, where));
                    }
                });
                return map;
            }
        }
        if (!(type instanceof Class<?> target)) {
            throw new IllegalStateException(where + ": the copier walks no " + type.getTypeName());
        }
        return leaf(stored, target, owner, writtenZone, where).orElseGet(() -> object(stored, target, writtenZone, where));
    }

    /** Reads a stored leaf as its Java type, or refuses it. */
    @FunctionalInterface
    private interface LeafReader {
        Object read(Object stored, String where);
    }

    private static final Map<Class<?>, LeafReader> LEAVES = Map.ofEntries(
            Map.entry(String.class, (stored, where) -> require(stored, String.class, String.class, where)),
            Map.entry(Integer.class, JsonWalk::whole), Map.entry(int.class, JsonWalk::whole),
            Map.entry(Long.class, JsonWalk::wide), Map.entry(long.class, JsonWalk::wide),
            Map.entry(Double.class, JsonWalk::fraction), Map.entry(double.class, JsonWalk::fraction),
            Map.entry(Boolean.class, JsonWalk::truth), Map.entry(boolean.class, JsonWalk::truth),
            Map.entry(LocalDate.class, (stored, where) -> ColumnValue.date(require(stored, Date.class, LocalDate.class, where))));

    private static Optional<JsonNode> leaf(Object stored, Class<?> target, Optional<Owner> owner, ZoneId writtenZone, String where) {
        if (target.isEnum()) {
            return Optional.of(MAPPER.valueToTree(constant(require(stored, String.class, target, where), target, where)));
        }
        if (target == LocalDateTime.class) {
            return Optional.of(MAPPER.valueToTree(time(require(stored, Date.class, target, where), owner, writtenZone, where)));
        }
        return Optional.ofNullable(LEAVES.get(target)).map(reader -> MAPPER.valueToTree(reader.read(stored, where)));
    }

    private static Object whole(Object stored, String where) {
        return require(stored, Integer.class, Integer.class, where);
    }

    /** A whole number stored as an int is widened; nothing is narrowed. */
    private static Object wide(Object stored, String where) {
        return stored instanceof Integer whole ? Long.valueOf(whole) : require(stored, Long.class, Long.class, where);
    }

    private static Object fraction(Object stored, String where) {
        return stored instanceof Integer || stored instanceof Long ? ((Number) stored).doubleValue()
                : require(stored, Double.class, Double.class, where);
    }

    private static Object truth(Object stored, String where) {
        return require(stored, Boolean.class, Boolean.class, where);
    }

    private static LocalDateTime time(Date stored, Optional<Owner> owner, ZoneId writtenZone, String where) {
        Owner holder = owner.orElseThrow(() -> new IllegalStateException(where + ": a time no field holds cannot be classified"));
        ZoneId zone = EmbeddedTimes.readZone(holder.declaringClass(), holder.field(), writtenZone)
                .orElseThrow(() -> new IllegalStateException(where + ": no entry in EmbeddedTimes says how "
                        + holder.declaringClass().getName() + "." + holder.field() + " was held"));
        return LocalDateTime.ofInstant(stored.toInstant(), zone);
    }

    private static ObjectNode object(Object stored, Class<?> target, ZoneId writtenZone, String where) {
        List<Field> fields = fields(target);
        ObjectNode object = MAPPER.createObjectNode();
        require(stored, Document.class, target, where).forEach((key, value) -> {
            if (key.equals(TYPE_HINT) || value == null) {
                return;
            }
            Field field = fields.stream().filter(candidate -> storedName(candidate).equals(key)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(where + ": " + target.getSimpleName()
                            + " has no field for the stored key " + key));
            object.set(field.getName(), node(value, field.getGenericType(), Optional.of(new Owner(target, field.getName())),
                    writtenZone, where));
        });
        // a field the document never stored reads as Java's default, as Spring Data read it at m1.6.1
        fields.stream().filter(field -> !object.has(field.getName()))
                .forEach(field -> object.set(field.getName(), MAPPER.valueToTree(absent(field.getType()))));
        return object;
    }

    /** A record's components, else every instance field of the class and its superclasses. */
    private static List<Field> fields(Class<?> target) {
        List<Field> fields = new ArrayList<>();
        if (target.isRecord()) {
            for (RecordComponent component : target.getRecordComponents()) {
                fields.add(declared(target, component.getName()));
            }
            return fields;
        }
        for (Class<?> each = target; each != null && each != Object.class; each = each.getSuperclass()) {
            for (Field field : each.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static Field declared(Class<?> target, String name) {
        try {
            return target.getDeclaredField(name);
        } catch (NoSuchFieldException absent) {
            throw new IllegalStateException("A record's component is its field: " + target.getName() + "." + name, absent);
        }
    }

    private static String storedName(Field field) {
        org.springframework.data.mongodb.core.mapping.Field named =
                field.getAnnotation(org.springframework.data.mongodb.core.mapping.Field.class);
        if (named == null) {
            return field.getName();
        }
        return named.value().isEmpty() ? (named.name().isEmpty() ? field.getName() : named.name()) : named.value();
    }

    private static Object absent(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        return type == boolean.class ? Boolean.FALSE : Array.get(Array.newInstance(type, 1), 0);
    }

    private static String constant(String stored, Class<?> target, String where) {
        for (Object constant : target.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(stored)) {
                return stored;
            }
        }
        throw new IllegalStateException(where + ": " + stored + " is not a " + target.getSimpleName());
    }

    private static <T> T require(Object stored, Class<T> kind, Type target, String where) {
        if (!kind.isInstance(stored)) {
            throw new IllegalStateException(where + ": a " + stored.getClass().getSimpleName() + " cannot be read as a "
                    + target.getTypeName());
        }
        return kind.cast(stored);
    }
}
