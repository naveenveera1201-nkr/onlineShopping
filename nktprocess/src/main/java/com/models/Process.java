package com.models;

import java.util.List;
import java.util.Objects;

public class Process {

    private String code;
    private String shortDesc;
    private String description;
    private Integer initialStatus;
    private Boolean approvalFlow;
    private Integer noOfLevels;
    private List<ApprovalStep> approvalSteps;
    private String collectionName;
    private String objectName;

   
	public Process() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getShortDesc() {
        return shortDesc;
    }

    public void setShortDesc(String shortDesc) {
        this.shortDesc = shortDesc;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getInitialStatus() {
        return initialStatus;
    }

    public void setInitialStatus(Integer initialStatus) {
        this.initialStatus = initialStatus;
    }

    public Boolean getApprovalFlow() {
        return approvalFlow;
    }

    public void setApprovalFlow(Boolean approvalFlow) {
        this.approvalFlow = approvalFlow;
    }

    public Integer getNoOfLevels() {
        return noOfLevels;
    }

    public void setNoOfLevels(Integer noOfLevels) {
        this.noOfLevels = noOfLevels;
    }

    public List<ApprovalStep> getApprovalSteps() {
        return approvalSteps;
    }

    public void setApprovalSteps(List<ApprovalStep> approvalSteps) {
        this.approvalSteps = approvalSteps;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Process process = (Process) o;
        return Objects.equals(code, process.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
    
    public String getCollectionName() {
		return collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public String getObjectName() {
		return objectName;
	}

	public void setObjectName(String objectName) {
		this.objectName = objectName;
	}

}