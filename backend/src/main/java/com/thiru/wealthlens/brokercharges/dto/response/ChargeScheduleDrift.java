package com.thiru.wealthlens.brokercharges.dto.response;

import java.util.List;

/**
 * A shipped rate card that does not match the one on file.
 *
 * <p>Seeding is idempotent by {@code scheduleCode}, so a card already in the database is left alone
 * however far it has moved from the file that ships beside it. That is deliberate — an operator who
 * corrects a rate through the API must not have it overwritten on the next restart — but silence
 * about it is not: the repository and the database end up disagreeing about what every user is
 * charged, and nothing says so.
 *
 * <p>Reporting the difference is what keeps the two answerable. It does not say which is right:
 * a card corrected in production is drift, and so is a card someone edited in the repository and
 * expected a deploy to apply. They need opposite fixes, which is exactly why a human has to look.
 *
 * @param scheduleCode    the card in question
 * @param fileName        the shipped file it was compared against
 * @param differingFields the top-level fields that disagree, or a single entry saying the card is
 *                        absent from the database entirely — a different problem, and one a
 *                        restart fixes
 */
public record ChargeScheduleDrift(String scheduleCode, String fileName, List<String> differingFields) {

    static final String ABSENT = "absent from the database";

    public static ChargeScheduleDrift absent(String scheduleCode, String fileName) {
        return new ChargeScheduleDrift(scheduleCode, fileName, List.of(ABSENT));
    }
}
