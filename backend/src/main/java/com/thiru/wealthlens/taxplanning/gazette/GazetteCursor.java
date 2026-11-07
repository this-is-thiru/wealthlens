package com.thiru.wealthlens.taxplanning.gazette;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * Where the Gazette watch has read to (W-236): the year folder and the last number read in it. One document; set by the
 * owner to start the watch, moved forward only by the watch.
 */
@Data
@NoArgsConstructor
@Document("tax_gazette_cursor")
public class GazetteCursor {

    /** The one cursor's id. */
    public static final String ID = "egazette";

    @MongoId
    private String id;

    @Field("year")
    private int year;

    @Field("last_number")
    private long lastNumber;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Field("updated_by")
    private String updatedBy;
}
