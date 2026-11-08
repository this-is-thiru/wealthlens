package com.thiru.wealthlens.auth.dto;

import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import io.jsonwebtoken.Claims;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class AuthHelper {
    private static final String AUTHORITIES_KEY = "authorities";

    public enum Role {
        SUPER_USER,
        ADMIN,
        MANAGER,
        EDITOR,
        AUTHOR,
        MODERATOR,
        USER,
        GUEST,
        TEST_USER
    }

    public static String getRole(Role role) {
        return role.name();
    }

    public static List<GrantedAuthority> getAuthorities(Claims claims) {

        List<String> roles = TJsonMapper.readAsList(claims.get(AUTHORITIES_KEY), String.class);
        return roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    public static void setRoles(Map<String, Object> claims, Collection<? extends GrantedAuthority> authorities) {

        var userAuthorities = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        claims.put(AUTHORITIES_KEY, userAuthorities);
    }
}
