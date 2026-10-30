package com.thiru.wealthlens.testsupport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tax-planning status board in {@code docs/taxplanning-build/STATE.md}, read as each item's status.
 *
 * <p>The board is the one place an item's status is kept (D-159), so every test that checks the backlog against
 * something else reads it here — the coverage register's pointers and the plans' open steps (W-200).
 */
public final class StatusBoard {

    private static final Pattern BOARD_ENTRY = Pattern.compile("(W-[0-9]{3}[a-z0-9]*) (\\S)");

    /** Done, and retired or split: no work is left on an item with either status. */
    private static final Set<String> FINISHED = Set.of("D", "—");

    private StatusBoard() {
    }

    /** The board: the lines from {@code ## Status board} up to the counts line, each {@code W-0nn X} entry. */
    public static Map<String, String> read(List<String> lines) {
        Map<String, String> board = new HashMap<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.startsWith("## Status board")) {
                inside = true;
            } else if (inside && line.startsWith("Counts")) {
                break;
            } else if (inside) {
                Matcher entry = BOARD_ENTRY.matcher(line);
                while (entry.find()) {
                    board.put(entry.group(1), entry.group(2));
                }
            }
        }
        return board;
    }

    /** Whether a board status means the item is done or retired. */
    public static boolean isFinished(String status) {
        return FINISHED.contains(status);
    }
}
