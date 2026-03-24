package com.models;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

public class ProcessInstance {

    private ObjectId id;
    private String processCode;
    private Integer currentStatus;
    private Integer currentLevel;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    private String createdBy;
	private String modifiedBy;
    private List<ProcessHistory> history;
    private Map<String, Object> data; // Extensible data
    private Integer version;
    private String currentStatusDesc;
    private String statusCode;
	private String statusDesc;


    public ProcessInstance() {
        this.createdDate = LocalDateTime.now();
        this.modifiedDate = LocalDateTime.now();
        this.version = 1;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public Integer getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(Integer currentStatus) {
        this.currentStatus = currentStatus;
    }

    public Integer getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(Integer currentLevel) {
        this.currentLevel = currentLevel;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(LocalDateTime modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public List<ProcessHistory> getHistory() {
        return history;
    }

    public void setHistory(List<ProcessHistory> history) {
        this.history = history;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
    public String getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(String statusCode) {
		this.statusCode = statusCode;
	}

	public String getStatusDesc() {
		return statusDesc;
	}

	public void setStatusDesc(String statusDesc) {
		this.statusDesc = statusDesc;
	}
	
    public String getCurrentStatusDesc() {
		return currentStatusDesc;
	}

	public void setCurrentStatusDesc(String currentStatusDesc) {
		this.currentStatusDesc = currentStatusDesc;
	}


}