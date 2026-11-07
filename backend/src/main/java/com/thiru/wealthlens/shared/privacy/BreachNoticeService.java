package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Breach intimation (Digital Personal Data Protection Act, 2023 s.8(6); Rules r.7; W-237c). The owner posts a notice;
 * each affected user finds it in their account and is emailed by the owner; the owner records each step towards the
 * Board, and the report of intimations the Board's detailed report needs is read from the stored facts.
 */
@Service
@Log4j2
@Transactional
@RequiredArgsConstructor
public class BreachNoticeService {

    private final BreachNoticeRepository notices;
    private final BreachIntimationRepository intimations;
    private final RegisteredUsers registeredUsers;
    private final PrivacySettings privacy;
    private final Clock clock;

    public BreachCase post(BreachNoticeRequest request) {
        LocalDateTime now = now();
        BreachText text = required(request.text());
        if (request.becameAwareAt() == null || request.becameAwareAt().isAfter(now)) {
            throw new BadRequestException("When the breach became known is required, and cannot be in the future");
        }
        List<String> affected = affected(request);
        BreachNotice notice = new BreachNotice();
        notice.setDescription(text.description());
        notice.setConsequences(text.consequences());
        notice.setMitigation(text.mitigation());
        notice.setSafetyMeasures(text.safetyMeasures());
        notice.setBecameAwareAt(request.becameAwareAt());
        notice.setScope(request.scope());
        notice.setAffected(affected);
        notice.setPostedAt(now);
        notice.setBoardReportDueAt(request.becameAwareAt().plusHours(privacy.breach().boardReportHours()));
        BreachNotice saved = notices.save(notice);
        intimations.saveAll(affected.stream().map(email -> posted(saved.getId(), email, now)).toList());
        log.warn("Breach {} posted to {} users; the Board's report is due by {}", saved.getId(), affected.size(),
                saved.getBoardReportDueAt());
        return breachCase(saved);
    }

    public BreachCase boardFirstIntimated(String noticeId) {
        BreachNotice notice = found(noticeId);
        if (notice.getBoardFirstIntimatedAt() != null) {
            throw new BadRequestException("The Board was first told of breach " + noticeId + " at "
                    + notice.getBoardFirstIntimatedAt());
        }
        notice.setBoardFirstIntimatedAt(now());
        return breachCase(notices.save(notice));
    }

    public BreachCase boardReportSent(String noticeId) {
        BreachNotice notice = found(noticeId);
        if (notice.getBoardReportSentAt() != null) {
            throw new BadRequestException("The Board's report on breach " + noticeId + " was sent at "
                    + notice.getBoardReportSentAt());
        }
        notice.setBoardReportSentAt(now());
        return breachCase(notices.save(notice));
    }

    public BreachCase boardExtension(String noticeId, BoardExtension extension) {
        BreachNotice notice = found(noticeId);
        if (extension == null || extension.reportDueAt() == null || isBlank(extension.reference())) {
            throw new BadRequestException("A longer period needs the new due time and the Board's reference");
        }
        if (!extension.reportDueAt().isAfter(notice.getBoardReportDueAt())) {
            throw new BadRequestException("The new due time " + extension.reportDueAt() + " is not later than "
                    + notice.getBoardReportDueAt());
        }
        notice.setBoardReportDueAt(extension.reportDueAt());
        notice.setBoardExtensionReference(extension.reference());
        return breachCase(notices.save(notice));
    }

    /** Records the users emailed; one already recorded keeps its first time, so a repeated call changes nothing. */
    public BreachReport emailed(String noticeId, BreachEmails emailed) {
        BreachNotice notice = found(noticeId);
        Map<String, BreachIntimation> byEmail = intimations.findByNoticeIdOrderByEmailAsc(noticeId).stream()
                .collect(Collectors.toMap(BreachIntimation::getEmail, Function.identity()));
        if (emailed == null || emailed.emails().isEmpty()) {
            throw new BadRequestException("Name the affected users emailed");
        }
        List<String> unknown = emailed.emails().stream().filter(email -> !byEmail.containsKey(email)).toList();
        if (!unknown.isEmpty()) {
            throw new BadRequestException("Not affected by breach " + noticeId + ": " + unknown);
        }
        LocalDateTime now = now();
        List<BreachIntimation> first = emailed.emails().stream().map(byEmail::get)
                .filter(intimation -> intimation.getEmailedAt() == null).distinct().toList();
        first.forEach(intimation -> intimation.setEmailedAt(now));
        intimations.saveAll(first);
        return report(notice);
    }

    public BreachReport report(String noticeId) {
        return report(found(noticeId));
    }

    /** The user's notices, newest first; the first read of each is recorded. */
    public List<UserBreachNotice> noticesFor(String email) {
        LocalDateTime now = now();
        List<BreachIntimation> mine = intimations.findByEmailOrderByPostedAtDesc(email);
        List<BreachIntimation> firstRead = mine.stream().filter(intimation -> intimation.getReadAt() == null).toList();
        firstRead.forEach(intimation -> intimation.setReadAt(now));
        intimations.saveAll(firstRead);
        return mine.stream().map(intimation -> found(intimation.getNoticeId()))
                .map(notice -> new UserBreachNotice(notice.getId(), text(notice), notice.getPostedAt(), privacy.contact()))
                .toList();
    }

    private List<String> affected(BreachNoticeRequest request) {
        if (request.scope() == null) {
            throw new BadRequestException("Who is affected is required: LISTED or EVERY_REGISTERED_USER");
        }
        Stream<String> emails = request.scope() == BreachScope.EVERY_REGISTERED_USER
                ? registeredUsers.emails().stream() : request.emails().stream();
        List<String> affected = emails.filter(email -> !isBlank(email)).distinct().sorted().toList();
        if (affected.isEmpty()) {
            throw new BadRequestException("A breach notice must reach at least one affected user");
        }
        return affected;
    }

    private static BreachText required(BreachText text) {
        if (text == null || Stream.of(text.description(), text.consequences(), text.mitigation(), text.safetyMeasures())
                .anyMatch(BreachNoticeService::isBlank)) {
            throw new BadRequestException("A breach notice states the breach, its consequences, the mitigation and the"
                    + " safety measures a user may take (DPDP Rules r.7(1)(a) – (d))");
        }
        return text;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static BreachIntimation posted(String noticeId, String email, LocalDateTime now) {
        BreachIntimation intimation = new BreachIntimation();
        intimation.setNoticeId(noticeId);
        intimation.setEmail(email);
        intimation.setPostedAt(now);
        return intimation;
    }

    private BreachNotice found(String noticeId) {
        return notices.findById(noticeId).orElseThrow(() -> new BadRequestException("No breach notice " + noticeId));
    }

    private BreachReport report(BreachNotice notice) {
        List<BreachIntimationLine> lines = intimations.findByNoticeIdOrderByEmailAsc(notice.getId()).stream()
                .map(intimation -> new BreachIntimationLine(intimation.getEmail(), intimation.getPostedAt(),
                        Optional.ofNullable(intimation.getReadAt()), Optional.ofNullable(intimation.getEmailedAt())))
                .toList();
        return new BreachReport(breachCase(notice), lines);
    }

    private static BreachText text(BreachNotice notice) {
        return new BreachText(notice.getDescription(), notice.getConsequences(), notice.getMitigation(),
                notice.getSafetyMeasures());
    }

    private static BreachCase breachCase(BreachNotice notice) {
        BoardSteps board = new BoardSteps(notice.getBoardReportDueAt(),
                Optional.ofNullable(notice.getBoardExtensionReference()),
                Optional.ofNullable(notice.getBoardFirstIntimatedAt()), Optional.ofNullable(notice.getBoardReportSentAt()));
        return new BreachCase(notice.getId(), text(notice), notice.getBecameAwareAt(), notice.getScope(),
                notice.getAffected(), notice.getPostedAt(), board);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }
}
