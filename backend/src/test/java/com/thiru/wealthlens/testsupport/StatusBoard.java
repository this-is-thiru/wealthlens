package com.thiru.wealthlens.testsupport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The open items of {@code docs/taxplanning-build/BACKLOG.md}, read as each item's status (W-435, D-1051).
 *
 * <p>The backlog lists every open item and nothing else, so an item absent from it is finished; every test that checks
 * something against the backlog — the coverage register's pointers, the plans' open steps, a text waiting on an item —
 * reads it here.
 */
public final class StatusBoard {

    /** A backlog row: its id, then its one-character status. */
    private static final Pattern ROW = Pattern.compile("^\\| (W-[0-9]{3}[a-z0-9]*) \\| (\\S) \\|");

    /** Done, and retired or split: no work is left on an item with either status. */
    private static final Set<String> FINISHED = Set.of("D", "—");

    private StatusBoard() {
    }

    /** Each backlog row's id and status. */
    public static Map<String, String> read(List<String> lines) {
        Map<String, String> board = new HashMap<>();
        for (String line : lines) {
            Matcher row = ROW.matcher(line);
            if (row.find()) {
                board.put(row.group(1), row.group(2));
            }
        }
        return board;
    }

    /** Whether a status means the item is done or retired. */
    public static boolean isFinished(String status) {
        return FINISHED.contains(status);
    }

    /** Whether {@code item} is finished: absent from the backlog, or marked done or retired in it. */
    public static boolean isFinished(Map<String, String> board, String item) {
        String status = board.get(item);
        return status == null || isFinished(status);
    }
}
