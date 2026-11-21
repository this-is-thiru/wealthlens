package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.collection.TStringUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

/** Whether a caller asks for another user's {@code /…/user/{email}/…} path; asked by {@code AuthConfig}'s access rule until W-358c's {@code /me}. */
public class UserValidator {

    public static boolean isRestrictedUser(String username, Collection<? extends GrantedAuthority> authorities, HttpServletRequest request) {
        if (isSuperUser(authorities)) {
            return false;
        }
        String email = extractUsernameFromPath(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        return email != null && !username.equals(email);
    }

    private static boolean isSuperUser(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).anyMatch(authority -> authority.equals("ROLE_SUPER_USER"));
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
