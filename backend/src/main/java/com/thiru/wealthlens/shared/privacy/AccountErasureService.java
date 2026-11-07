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
 * Erasure of a whole account (Digital Personal Data Protection Act, 2023 s.8(7)(a), s.12(3); W-237d). A request holds the
 * account at once; once Rules r.8(3)'s year from it has passed, every {@link PersonalDataHolder} erases what it stores. Nothing
 * is kept for another law: none binds WealthLens to keep a person's records (W-237d C5a).
 */
@Service
@Log4j2
@Transactional
@RequiredArgsConstructor
public class AccountErasureService {

    private final AccountErasureRequestRepository requests;
    private final List<PersonalDataHolder> holders;
    private final PrivacySettings privacy;
    private final Clock clock;

    /** Holds the account; asking again while held returns the same receipt. */
    public AccountErasureReceipt request(String email) {
        Optional<AccountErasureRequest> open = requests.findByEmailAndErasedAtIsNull(email);
        if (open.isPresent()) {
            return receipt(open.get());
        }
        AccountErasureRequest request = new AccountErasureRequest();
        request.setEmail(email);
        request.setRequestedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        AccountErasureRequest saved = requests.save(request);
        holders.forEach(holder -> holder.hold(email, saved.getRequestedAt()));
        log.info("Account erasure {} requested; held until {}", saved.getId(), erasedFrom(saved));
        return receipt(saved);
    }

    /** The latest request for the account. */
    public AccountErasureReceipt receipt(String email) {
        return requests.findFirstByEmailOrderByRequestedAtDesc(email).map(this::receipt)
                .orElseThrow(() -> new BadRequestException("No erasure was requested for " + email));
    }

    /** Requests whose hold has passed by {@code nowUtc}, not yet erased. */
    public List<AccountErasureRequest> due(LocalDateTime nowUtc) {
        return requests.findByErasedAtIsNullAndRequestedAtLessThanEqual(nowUtc.minus(privacy.erasureHold()));
    }

    public void erase(AccountErasureRequest request, LocalDateTime nowUtc) {
        holders.forEach(holder -> holder.erase(request.getEmail()));
        request.setErasedAt(nowUtc);
        requests.save(request);
        log.info("Account erasure {} done", request.getId());
    }

    /**
     * Applies again, after a restore, what the restored snapshot does not hold (W-237f C2a′): each account erased at or after
     * {@code snapshotTakenAt} is erased again, keeping the first {@code erased_at}; each asked for then and still held is held
     * again; and each module applies its own part-erasures. An erasure before the snapshot is left alone: the snapshot already
     * reflects it, and the email may since belong to a new account. A second call changes nothing.
     */
    public ReappliedErasures reapply(ErasureReapplication reapplication) {
        LocalDateTime snapshotTakenAt = Optional.ofNullable(reapplication).map(ErasureReapplication::snapshotTakenAt)
                .orElseThrow(() -> new BadRequestException("Give the restored snapshot's time, snapshotTakenAt"));
        if (snapshotTakenAt.isAfter(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)))) {
            throw new BadRequestException("The snapshot's time " + snapshotTakenAt + " is in the future");
        }
        List<String> erased = requests.findByErasedAtGreaterThanEqual(snapshotTakenAt).stream()
                .map(AccountErasureRequest::getEmail).distinct().toList();
        erased.forEach(email -> holders.forEach(holder -> holder.erase(email)));
        List<AccountErasureRequest> open = requests.findByErasedAtIsNullAndRequestedAtGreaterThanEqual(snapshotTakenAt);
        open.forEach(request -> holders.forEach(holder -> holder.hold(request.getEmail(), request.getRequestedAt())));
        List<String> parts = holders.stream().flatMap(holder -> holder.reapply(snapshotTakenAt).stream()).toList();
        log.info("Erasures since {} applied again: {} accounts erased, {} held, {} parts", snapshotTakenAt, erased.size(),
                open.size(), parts.size());
        return new ReappliedErasures(snapshotTakenAt, erased, open.stream().map(AccountErasureRequest::getEmail).toList(),
                parts);
    }

    private LocalDateTime erasedFrom(AccountErasureRequest request) {
        return request.getRequestedAt().plus(privacy.erasureHold());
    }

    private AccountErasureReceipt receipt(AccountErasureRequest request) {
        return new AccountErasureReceipt(request.getEmail(), request.getRequestedAt(), erasedFrom(request),
                Optional.ofNullable(request.getErasedAt()), privacy.contact());
    }
}
