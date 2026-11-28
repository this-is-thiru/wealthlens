package com.thiru.wealthlens.taxplanning.gazette;

import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Where the Gazette watch has read to (W-236): the year folder and the last number read in it. One document; set by the
 * owner to start the watch, moved forward only by the watch.
 */
@Entity
@Table(name = "tax_gazette_cursor")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class GazetteCursor {

    /** The one cursor's id. */
    public static final String ID = "egazette";

    @Id
    private String id;

    @Column(name = "year")
    private int year;

    @Column(name = "last_number")
    private long lastNumber;

    /**
     * Who moved the cursor and when (W-337f, D-951 C7a): the standard audit columns. {@code TaxJobRunner.run()} wraps a
     * job in {@code auditor.runAs(job.name(), ...)},
     * so this fills with the job's own name.
     */
    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();
}
