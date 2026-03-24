package com.models;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class InvesterProfile //implements InvesterProfileInterface 
{

    private ObjectId id;
    private String processCode;
    private Integer currentStatus;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    private String createdBy;
	private String modifiedBy;
    private List<InvesterProfile> history;
    private Map<String, Object> data;
    private Integer version;
    private String currentStatusDesc;
    private String statusCode;
	private String statusDesc;
	private List<InvesterProfile> profileList;
	
//	public ObjectId getId() {
//		return id;
//	}
//	public String getProcessCode() {
//		return processCode;
//	}
//	public Integer getCurrentStatus() {
//		return currentStatus;
//	}
//	public LocalDateTime getCreatedDate() {
//		return createdDate;
//	}
//	public LocalDateTime getModifiedDate() {
//		return modifiedDate;
//	}
//	public String getCreatedBy() {
//		return createdBy;
//	}
//	public String getModifiedBy() {
//		return modifiedBy;
//	}
//	public List<InvesterProfileInterface> getHistory() {
//		return null;
//	}
//	public Map<String, Object> getData() {
//		return data;
//	}
//	public Integer getVersion() {
//		return version;
//	}
//	public String getCurrentStatusDesc() {
//		return currentStatusDesc;
//	}
//	public String getStatusCode() {
//		return statusCode;
//	}
//	public String getStatusDesc() {
//		return statusDesc;
//	}


    public InvesterProfile() {
        this.createdDate = LocalDateTime.now();
        this.modifiedDate = LocalDateTime.now();
        this.version = 1;
    }


}