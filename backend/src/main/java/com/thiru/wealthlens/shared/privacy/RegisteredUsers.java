package com.thiru.wealthlens.shared.privacy;

import java.util.List;

/**
 * Every registered user's email, the one mode of communication a user registers (W-237c). Declared here and implemented
 * in {@code auth}, because {@code auth} depends on {@code shared} and the reverse would be a cycle.
 */
public interface RegisteredUsers {

    List<String> emails();
}
