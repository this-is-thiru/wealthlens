package com.thiru.wealthlens.util.collection;

import com.thiru.wealthlens.dto.Student;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TJsonMapperTest {

    @Test
    void readAsList() {
        // Test String List
        List<String> stringList = List.of("string", "string1", "string2");
        String content = TJsonMapper.writeValueAsString(stringList);
        List<String> list = TJsonMapper.readAsList(content, String.class);

        Assertions.assertEquals(stringList, list);

        // Test Integer List
        List<Integer> integerList = List.of(1, 2, 3);
        content = TJsonMapper.writeValueAsString(integerList);
        List<Integer> integers = TJsonMapper.readAsList(content, Integer.class);

        Assertions.assertEquals(integerList, integers);
    }

    @Test
    void readObject() {
        Student student = new Student("Thiru", LocalDate.now(), LocalDateTime.now());
        Student student1 = TJsonMapper.copy(student, Student.class);
        Assertions.assertEquals(student1.name(), student.name());
        Assertions.assertEquals(student1.dob(), student.dob());
        Assertions.assertEquals(student1.createdDate(), student.createdDate());
    }
}
