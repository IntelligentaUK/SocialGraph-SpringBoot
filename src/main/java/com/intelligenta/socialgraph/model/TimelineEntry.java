package com.intelligenta.socialgraph.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for a single timeline entry.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelineEntry {

    // Activity fields
    private String uuid;
    private String type;
    private String content;
    private String url;
    private String created;
    private String updated;
    private String parentUuid;
    private String sharedPostUuid;

    // Actor fields
    private String actorUid;
    private String actorUsername;
    private String actorFullname;

    public TimelineEntry() {}

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public String getParentUuid() {
        return parentUuid;
    }

    public void setParentUuid(String parentUuid) {
        this.parentUuid = parentUuid;
    }

    public String getSharedPostUuid() {
        return sharedPostUuid;
    }

    public void setSharedPostUuid(String sharedPostUuid) {
        this.sharedPostUuid = sharedPostUuid;
    }

    public String getActorUid() {
        return actorUid;
    }

    public void setActorUid(String actorUid) {
        this.actorUid = actorUid;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getActorFullname() {
        return actorFullname;
    }

    public void setActorFullname(String actorFullname) {
        this.actorFullname = actorFullname;
    }
}
