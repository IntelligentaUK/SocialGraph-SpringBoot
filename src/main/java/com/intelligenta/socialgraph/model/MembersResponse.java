package com.intelligenta.socialgraph.model;

import java.util.List;

/**
 * Response DTO for member list operations.
 */
public class MembersResponse {

    private String setType;
    private List<MemberInfo> members;
    private int count;
    private long duration;

    public MembersResponse() {}

    public MembersResponse(String setType, List<MemberInfo> members, long duration) {
        this.setType = setType;
        this.members = members;
        this.count = members.size();
        this.duration = duration;
    }

    public String getSetType() {
        return setType;
    }

    public void setSetType(String setType) {
        this.setType = setType;
    }

    public List<MemberInfo> getMembers() {
        return members;
    }

    public void setMembers(List<MemberInfo> members) {
        this.members = members;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}
