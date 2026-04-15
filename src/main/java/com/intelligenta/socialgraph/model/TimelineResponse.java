package com.intelligenta.socialgraph.model;

import java.util.List;

/**
 * Response DTO for timeline operations.
 */
public class TimelineResponse {

    private List<TimelineEntry> entities;
    private int count;
    private long duration;

    public TimelineResponse() {}

    public TimelineResponse(List<TimelineEntry> entities, int count, long duration) {
        this.entities = entities;
        this.count = count;
        this.duration = duration;
    }

    public List<TimelineEntry> getEntities() {
        return entities;
    }

    public void setEntities(List<TimelineEntry> entities) {
        this.entities = entities;
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
