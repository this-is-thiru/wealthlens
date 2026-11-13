package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.id.ScheduleCode;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import java.util.List;

/**
 * A part or schedule of a return form, as the Gazette lays it out (DR 21 I5; W-092, D-654).
 *
 * @param code the schedule, as the form names it
 * @param displayName its title, as the form prints it
 * @param fields its cells, in the form's order
 * @param provenance the Gazette page it was read from
 */
public record ItrSchedule(ScheduleCode code, String displayName, List<ScheduleField> fields, Provenance provenance) {

    public ItrSchedule {
        if (code == null || provenance == null) {
            throw new IllegalArgumentException("A schedule names its code and where it was read");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(code + " must have a display name");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException(code + " has no fields; a schedule shows at least one figure");
        }
        fields = List.copyOf(fields);
    }
}
