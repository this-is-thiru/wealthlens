package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.auth.repository.UserDetailsRepository;
import com.thiru.wealthlens.shared.privacy.HeldAccounts;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component("userDetailsService")
@AllArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserDetailsRepository userDetailsRepo;
    private final HeldAccounts heldAccounts;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // A held account signs in no more from the moment of the request (W-237d C3a); it reads as unknown, as a wrong
        // password does, so the answer says nothing about the account.
        Optional<UserDetail> optionalUserDetails = userDetailsRepo.findById(username)
                .filter(user -> !heldAccounts.contains(username));
        return optionalUserDetails.map(UserDetailsImpl::new)
                .orElseThrow(() -> new UsernameNotFoundException("user not found " + username));
    }
}
