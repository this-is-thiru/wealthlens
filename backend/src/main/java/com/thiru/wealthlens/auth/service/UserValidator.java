package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.collection.TStringUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class UserValidator {
    public static boolean isRestrictedUser(UserDetails user) {
        if(isSuperUser(user)) {
            return false;
        }
        String username = user.getUsername();
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String email = extractUsernameFromPath(request);
        return email != null && !username.equals(email);
    }

    public static boolean isRestrictedUser(String username, List<GrantedAuthority> authorities) {
        if(isSuperUser(authorities)) {
            return false;
        }
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String email = extractUsernameFromPath(request);
        return email != null && !username.equals(email);
    }

    private static boolean isSuperUser(List<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).anyMatch(authority -> authority.equals("ROLE_SUPER_USER"));
    }

    private static boolean isSuperUser(UserDetails user) {
        return user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_USER"));
    }

    private static String extractUsernameFromPath(HttpServletRequest request) {
        String path = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        return extractUsernameFromPath(path);
    }

    private static String extractUsernameFromPath(String path) {
        if (!path.contains("/user/")) {
            return null;
        } else {
            List<String> argumentList = TStringUtil.splitSafeTrimmed(path, "/");
            return TCollectionUtil.getElementSafe(argumentList, argumentList.indexOf("user") + 1);
        }
    }
}
