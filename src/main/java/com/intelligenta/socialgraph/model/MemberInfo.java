package com.intelligenta.socialgraph.model;

/**
 * DTO for user member information (followers, following, etc.)
 */
public class MemberInfo {

    private String uid;
    private String username;
    private String fullname;

    public MemberInfo() {}

    public MemberInfo(String uid, String username, String fullname) {
        this.uid = uid;
        this.username = username;
        this.fullname = fullname;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullname() {
        return fullname;
    }

    public void setFullname(String fullname) {
        this.fullname = fullname;
    }
}
