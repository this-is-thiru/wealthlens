package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.auth.dto.AuthHelper;
import com.thiru.wealthlens.auth.dto.LoginResponse;
import com.thiru.wealthlens.auth.dto.RefreshResponse;
import com.thiru.wealthlens.auth.dto.RegistrationRequest;
import com.thiru.wealthlens.auth.entity.RefreshToken;
import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.auth.repository.UserDetailsRepository;
import com.thiru.wealthlens.shared.privacy.HeldAccounts;
import com.thiru.wealthlens.shared.privacy.PrivacyConsents;
import com.thiru.wealthlens.shared.privacy.PrivacyNotice;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@AllArgsConstructor
public class AuthService {

    private final UserDetailsRepository userDetailsRepo;
    private final PasswordEncoder passwordEncoder;
    private final HeldAccounts heldAccounts;
    private final RegisteredUserDirectory signIns;
    private final PrivacyConsents privacyConsents;
    private final Clock clock;
    private final AccessTokens accessTokens;
    private final RefreshTokens refreshTokens;

    public String addUser(RegistrationRequest request) {

        // The notice first, and one answer for a taken and a held address naming neither (W-358d2, D-884 C2a): registration should not
        // tell a stranger whether an address has an account. Without mail to confirm an address (W-363) a free one still succeeds.
        privacyConsents.requireCurrent(request.getPrivacyNoticeVersion());
        // The login is erased last; until the erasure is recorded done, the email is not free (W-237d).
        if (userDetailsRepo.findByEmail(request.getEmail()).isPresent() || heldAccounts.contains(request.getEmail())) {
            throw new IllegalArgumentException("This email cannot be registered");
        }

        UserDetail userEntity = new UserDetail();
        userEntity.setEmail(request.getEmail());
        userEntity.setPassword(passwordEncoder.encode(request.getPassword()));
        // A role above USER is granted by the owner, never chosen at registration (W-255; runbook §0.1.3).
        userEntity.setRoles(AuthHelper.Role.USER.name());
        LocalDateTime registeredAt = nowUtc();
        userEntity.setLastSignedInAt(registeredAt);
        userDetailsRepo.save(userEntity);
        privacyConsents.recordConsent(request.getEmail(), request.getPrivacyNoticeVersion(), registeredAt);
        return "user with username " + request.getEmail() + " added to system ";
    }


    public String changePassword(String email, RegistrationRequest request) {

        if (!Objects.equals(email, request.getEmail())) {
            throw new IllegalArgumentException("Email mismatch");
        }

        if (Objects.equals(request.getPassword(), request.getNewPassword())) {
            throw new IllegalArgumentException("Old password cannot be same as new password");
        }

        Optional<UserDetail> optionalUserDetails = userDetailsRepo.findByEmail(email);
        if (optionalUserDetails.isEmpty()) {
            throw new UsernameNotFoundException("User with email " + email + " not found");
        }

        UserDetail userEntity = optionalUserDetails.get();
        if (!passwordEncoder.matches(request.getPassword(), userEntity.getPassword())) {
            throw new IllegalArgumentException("Invalid old password");
        }
        signIns.storePassword(email, passwordEncoder.encode(request.getNewPassword()));
        // Whoever held a refresh token signs in again with the new password (W-358b).
        refreshTokens.endAll(email);
        return email + "'s password changed successfully";
    }

    /**
     * Called only after a successful sign-in, which is what keeps the account's purpose served (W-249), and which gives an
     * account registered before W-250 the notice it was never shown (Act s.5(2)(a)).
     */
    public LoginResponse generateToken(String username, Authentication authentication) {
        LocalDateTime signedInAt = nowUtc();
        signIns.recordSignIn(username, signedInAt);
        String noticeVersion = privacyConsents.recordNotice(username, signedInAt);
        return createToken(username, authentication, noticeVersion);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }

    private LoginResponse createToken(String username, Authentication authentication, String noticeVersion) {
        List<String> authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        List<String> factors = authentication.getAuthorities().stream().filter(FactorGrantedAuthority.class::isInstance)
                .map(GrantedAuthority::getAuthority).toList();
        String accessToken = accessTokens.issue(username, authorities);
        String refreshToken = refreshTokens.start(username, factors);
        return LoginResponse.from(accessToken, (int) AccessTokens.LIFETIME.toSeconds(), refreshToken, PrivacyNotice.PAGE, noticeVersion);
    }

    /**
     * Rotates a refresh token (W-358b). The roles are read again, so a role granted or withdrawn holds from the next refresh; an
     * account held or erased since the sign-in is refused, and its family ended.
     */
    public RefreshResponse refresh(String presented) {
        RefreshToken spent = refreshTokens.spend(presented);
        UserDetailsImpl user = userDetailsRepo.findByEmail(spent.getEmail()).filter(login -> !heldAccounts.contains(login.getEmail()))
                .map(UserDetailsImpl::new).orElseThrow(() -> {
                    refreshTokens.endFamily(spent.getFamilyId());
                    return RefreshTokens.refused();
                });
        List<String> authorities = Stream.concat(user.getAuthorities().stream().map(GrantedAuthority::getAuthority), spent.getFactors().stream())
                .toList();
        String accessToken = accessTokens.issue(user.getUsername(), authorities);
        return new RefreshResponse(accessToken, AccessTokens.LIFETIME.toSeconds(), refreshTokens.succeed(spent));
    }

    public void logout(String presented) {
        refreshTokens.end(presented);
    }
}
