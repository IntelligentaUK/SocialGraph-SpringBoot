package com.intelligenta.socialgraph.model;

import com.intelligenta.socialgraph.Verbs;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Response DTO for action list operations.
 */
public class ActionResponse {

    @JsonIgnore
    private Verbs.Action action;
    
    private String actionType;
    private String object;
    private List<ActionActor> actors;
    private int count;
    private long duration;

    public ActionResponse() {}

    public ActionResponse(Verbs.Action action, String object, List<ActionActor> actors, int count, long duration) {
        this.action = action;
        this.actionType = action.plural();
        this.object = object;
        this.actors = actors;
        this.count = count;
        this.duration = duration;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<ActionActor> getActors() {
        return actors;
    }

    public void setActors(List<ActionActor> actors) {
        this.actors = actors;
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
