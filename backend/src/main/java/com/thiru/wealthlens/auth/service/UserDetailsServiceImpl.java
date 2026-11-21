package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.auth.repository.UserDetailsRepository;
import com.thiru.wealthlens.shared.privacy.HeldAccounts;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component("userDetailsService")
@AllArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService, UserDetailsPasswordService {
    private final UserDetailsRepository userDetailsRepo;
    private final HeldAccounts heldAccounts;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // A held account signs in no more from the moment of the request (W-237d C3a); it reads as unknown, as a wrong
        // password does, so the answer says nothing about the account.
        Optional<UserDetail> optionalUserDetails = userDetailsRepo.findByEmail(username)
                .filter(user -> !heldAccounts.contains(username));
        return optionalUserDetails.map(UserDetailsImpl::new)
                .orElseThrow(() -> new UsernameNotFoundException("user not found " + username));
    }

    /**
     * Stores the hash Spring rewrote at a successful sign-in, when the stored one was older than the encoder's own (W-358d3): the
     * password is known only at a sign-in, so that is when a stored hash can move to Argon2id.
     */
    @Override
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        UserDetail login = userDetailsRepo.findByEmail(user.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("user not found " + user.getUsername()));
        login.setPassword(newPassword);
        return new UserDetailsImpl(userDetailsRepo.save(login));
    }
}
