package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grievance redressal (Digital Personal Data Protection Act, 2023 s.8(10), s.13; Rules r.14(3); W-237b): a user raises a
 * grievance and is told at once the date by which it will be answered; the owner answers from a queue ordered by that date,
 * which is the measure that keeps answers within the published period.
 */
@Service
@Log4j2
@Transactional
@RequiredArgsConstructor
public class GrievanceService {

    private final GrievanceRepository repository;
    private final PrivacySettings privacy;
    private final Clock clock;

    public GrievanceReceipt raise(String email, GrievanceStatement statement) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        Grievance grievance = new Grievance();
        grievance.setEmail(email);
        grievance.setText(required(statement, "a grievance"));
        grievance.setReceivedAt(now);
        grievance.setRespondBy(now.plusDays(privacy.grievance().responseDays()));
        Grievance saved = repository.save(grievance);
        log.info("Grievance {} received, to be answered by {}", saved.getId(), saved.getRespondBy());
        return receipt(saved);
    }

    public List<GrievanceReceipt> raisedBy(String email) {
        return repository.findByEmailOrderByReceivedAtDesc(email).stream().map(this::receipt).toList();
    }

    /** Unanswered grievances, the one due soonest first. */
    public List<GrievanceReceipt> unanswered() {
        return repository.findByAnsweredAtIsNullOrderByRespondByAsc().stream().map(this::receipt).toList();
    }

    public GrievanceReceipt answer(String grievanceId, GrievanceStatement statement) {
        Grievance grievance = repository.findById(grievanceId)
                .orElseThrow(() -> new BadRequestException("No grievance " + grievanceId));
        if (grievance.getAnsweredAt() != null) {
            throw new BadRequestException("Grievance " + grievanceId + " was answered at " + grievance.getAnsweredAt());
        }
        grievance.setAnswer(required(statement, "an answer"));
        grievance.setAnsweredAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        Grievance saved = repository.save(grievance);
        log.info("Grievance {} answered at {}", saved.getId(), saved.getAnsweredAt());
        return receipt(saved);
    }

    private static String required(GrievanceStatement statement, String what) {
        if (statement == null || statement.text() == null || statement.text().isBlank()) {
            throw new BadRequestException("The text of " + what + " is required");
        }
        return statement.text();
    }

    private GrievanceReceipt receipt(Grievance grievance) {
        Optional<GrievanceAnswer> answer = Optional.ofNullable(grievance.getAnsweredAt())
                .map(answeredAt -> new GrievanceAnswer(grievance.getAnswer(), answeredAt));
        return new GrievanceReceipt(grievance.getId(), grievance.getEmail(), grievance.getText(), grievance.getReceivedAt(),
                grievance.getRespondBy(), answer, privacy.contact());
    }
}
