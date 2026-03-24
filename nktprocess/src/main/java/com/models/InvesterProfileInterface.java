package com.models;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;


public interface InvesterProfileInterface {

	public ObjectId getId();

	public String getProcessCode();

	public Integer getCurrentStatus();

	public LocalDateTime getCreatedDate();

	public LocalDateTime getModifiedDate();

	public String getCreatedBy();

	public String getModifiedBy();

	public List<InvesterProfileInterface> getHistory();

	public Map<String, Object> getData();

	public Integer getVersion();

	public String getCurrentStatusDesc();

	public String getStatusCode();

	public String getStatusDesc();

}