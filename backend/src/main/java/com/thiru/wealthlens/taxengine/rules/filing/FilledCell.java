package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.id.CellCode;
import com.thiru.wealthlens.taxengine.rules.id.ScheduleCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * A form's cell as a computed return fills it (W-093, D-654): its figure, or why there is none — never blank, never a
 * zero standing in for "not computed" (C4a).
 *
 * @param schedule the schedule it is printed in
 * @param path where the form prints it
 * @param cell the cell
 * @param label what the figure is
 * @param amount the figure, where the return gives one
 * @param unavailableBecause why there is no figure; present exactly when {@code amount} is empty
 */
public record FilledCell(ScheduleCode schedule, String path, CellCode cell, String label, Optional<Money> amount,
        Optional<String> unavailableBecause) {

    public FilledCell {
        if (schedule == null || path == null || cell == null || label == null || amount == null
                || unavailableBecause == null) {
            throw new IllegalArgumentException("A filled cell names its schedule, path, cell and label, and says"
                    + " either its figure or why it has none");
        }
        if (amount.isPresent() == unavailableBecause.isPresent()) {
            throw new IllegalArgumentException(path + " must carry either a figure or the reason it has none, not "
                    + (amount.isPresent() ? "both" : "neither"));
        }
    }
}
