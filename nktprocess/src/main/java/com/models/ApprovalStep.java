package com.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Represents an approval step in the workflow process.
 */

@Setter
@Getter
@NoArgsConstructor
public class ApprovalStep {

    private Integer code;
    private Integer level;
    private String action;
    private Integer prevStatus;
    private Integer nextStatus;
    private Boolean isTerminal;

//    public ApprovalStep() {
//    }
//
//    public Integer getCode() {
//        return code;
//    }
//
//    public void setCode(Integer code) {
//        this.code = code;
//    }
//
//    public Integer getLevel() {
//        return level;
//    }
//
//    public void setLevel(Integer level) {
//        this.level = level;
//    }
//
//    public String getAction() {
//        return action;
//    }
//
//    public void setAction(String action) {
//        this.action = action;
//    }
//
//    public Integer getPrevStatus() {
//        return prevStatus;
//    }
//
//    public void setPrevStatus(Integer prevStatus) {
//        this.prevStatus = prevStatus;
//    }
//
//    public Integer getNextStatus() {
//        return nextStatus;
//    }
//
//    public void setNextStatus(Integer nextStatus) {
//        this.nextStatus = nextStatus;
//    }
//
//    public Boolean getTerminal() {
//        return isTerminal;
//    }
//
//    public void setTerminal(Boolean terminal) {
//        isTerminal = terminal;
//    }
}