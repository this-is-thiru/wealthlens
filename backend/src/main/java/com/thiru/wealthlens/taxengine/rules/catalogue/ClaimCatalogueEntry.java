package com.thiru.wealthlens.taxengine.rules.catalogue;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.Optional;

/**
 * How one deduction or exemption is claimed ({@code DR 22}; W-117a, D-699): the gap between a figure the engine can
 * compute and a person knowing whether to write to their employer, wait, or claim it in the return.
 *
 * @param rule the pack rule the entry is for; a catalogue has one entry for a rule
 * @param section the section catalogue entry that names and summarises it, by key
 * @param channel how the claim reaches the computed tax
 * @param chapterViiiPart the Part of Chapter VIII its section sits in, for a deduction under that Chapter
 * @param ifMissingFromForm16 what the claim's absence from the employer's certificate means
 * @param basis where the law puts it on that channel
 * @param guidance what the person does
 */
public record ClaimCatalogueEntry(RuleId rule, String section, ClaimChannel channel,
        Optional<ChapterViiiPart> chapterViiiPart, AbsenceFromForm16 ifMissingFromForm16, ClaimBasis basis,
        ClaimGuidance guidance) {

    public ClaimCatalogueEntry {
        if (rule == null || section == null || section.isBlank() || channel == null || chapterViiiPart == null
                || ifMissingFromForm16 == null || basis == null || guidance == null) {
            throw new IllegalArgumentException("A claim catalogue entry needs a rule, a section, a channel, a"
                    + " chapterViiiPart (Optional.empty() outside Chapter VIII), ifMissingFromForm16, a basis and guidance");
        }
        requireLateReturnWarningInPartCOnly(rule, chapterViiiPart, guidance);
    }

    private static void requireLateReturnWarningInPartCOnly(RuleId rule, Optional<ChapterViiiPart> chapterViiiPart,
            ClaimGuidance guidance) {
        boolean warns = guidance.steps().stream().anyMatch(ClaimStep::warnsOfLateReturnBar);
        boolean partC = chapterViiiPart.filter(ChapterViiiPart.C::equals).isPresent();
        if (partC && !warns) {
            throw new IllegalArgumentException("I24 — " + rule + " is in Part C of Chapter VIII, so one of its steps must"
                    + " warn that a late return loses the deduction");
        }
        if (!partC && warns) {
            throw new IllegalArgumentException("I24 — " + rule + " is not in Part C of Chapter VIII, so no step of it may"
                    + " warn of the late-return bar, which reaches Part C only");
        }
    }
}
