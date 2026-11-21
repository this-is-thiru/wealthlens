package com.thiru.wealthlens.auth.dto;

public class AuthHelper {
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
}
