package com.models;

import java.time.Instant;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class ProcessHistory {

    private Integer level;
    private String action;
    private Integer fromStatus;
    private Integer toStatus;
    private String performedBy;
    private Instant performedDate;
    private String comments;
    private Long durationMs; 
    private Map<String, Object> data; 
    // Time spent in previous status

    public ProcessHistory() {
        this.performedDate = Instant.now();
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(Integer fromStatus) {
        this.fromStatus = fromStatus;
    }

    public Integer getToStatus() {
        return toStatus;
    }

    public void setToStatus(Integer toStatus) {
        this.toStatus = toStatus;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public Instant getPerformedDate() {
        return performedDate;
    }

    public void setPerformedDate(Instant performedDate) {
        this.performedDate = performedDate;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}