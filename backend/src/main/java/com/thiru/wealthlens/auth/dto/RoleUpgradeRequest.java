package com.thiru.wealthlens.auth.dto;

import java.util.List;
import lombok.Data;

@Data
public class RoleUpgradeRequest {
    private String email;
    private List<AuthHelper.Role> role;
}
