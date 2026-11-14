package com.thiru.wealthlens.taxplanning.profile;

/** A record that belongs to one taxpayer profile. */
interface ProfileScoped {

    String getId();

    void setId(String id);

    String getProfileId();

    void setProfileId(String profileId);
}
