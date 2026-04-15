package com.intelligenta.socialgraph.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for an actor in an action response.
 */
public class ActionActor {

    @JsonProperty("@type")
    private String type = "person";
    
    private String username;
    private String displayName;
    private String uuid;

    public ActionActor() {}

    public ActionActor(String uuid, String username, String displayName) {
        this.uuid = uuid;
        this.username = username;
        this.displayName = displayName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
