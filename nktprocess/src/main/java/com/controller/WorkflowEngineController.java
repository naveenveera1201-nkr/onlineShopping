package com.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ProcessFlowEngine;
import com.constant.MutualFundContant;
import com.exceptions.ProcessFlowException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resource.WorkflowEngineResource;
import com.service.NktCoreService;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class WorkflowEngineController implements WorkflowEngineResource {

	@Autowired
	private ProcessFlowEngine processFlowEngine;

	@Autowired
	private NktCoreService nktCoreService;

	@Override
	public String process(@RequestParam("data") String data, @RequestParam("code") String code) {

		log.info("ProcessWorkflowController method called: code={}", code);
		String result = "";

		try {
			ObjectMapper mapper = new ObjectMapper();

			Map<String, Object> dataMap = mapper.readValue(data, new TypeReference<Map<String, Object>>() {
			});

			// ── NKT no-code platform: all "nkt.*" codes → NktCoreService ─────
			if (code != null && code.startsWith("nkt.")) {
				return nktCoreService.process(code, dataMap);
			}

//			// ── Mutual-Fund / Process-Flow Engine routing ─────────────────────
//			String processCode = processFlowEngine.getProcessRegistry().get(code).getCode();
//
//			if (MutualFundContant.P100.equals(processCode) && Objects.isNull(dataMap.get("action"))) {
//				result = processFlowEngine.createProcessInstance(processCode, "naveen", dataMap);
//			} else if (MutualFundContant.P100.equals(processCode)) {
//				result = processFlowEngine.performAction(dataMap.get("id").toString(), dataMap.get("action").toString(),
//						"naveen", "naveen");
//			} else if (MutualFundContant.INVESTOR_PROFILE_REGISTRATION.equals(processCode)
//					|| MutualFundContant.INVESTOR_BANKDETAILS_REGISTRATION.equals(processCode)
//					|| MutualFundContant.INVESTOR_NOMINEE_REGISTRATION.equals(processCode)
//					|| MutualFundContant.INVESTOR_KYC_REGISTRATION.equals(processCode)) {
//				result = processFlowEngine.registration(dataMap, processCode);
//			} else if (MutualFundContant.INVESTOR_LIST.equals(processCode)) {
//				result = processFlowEngine.getInvesterProfile(processCode);
//			} else if (MutualFundContant.GET_INVESTOR_KYC.equals(processCode)) {
//				result = processFlowEngine.getProfileDetails(dataMap.get("id").toString(), processCode);
//			} else if (MutualFundContant.GET_FUND_DETAILS.equals(processCode)) {
//				result = processFlowEngine.getAllFunds(dataMap, processCode);
//			} else if (MutualFundContant.GET_FUND_ALL_CATEGORIES.equals(processCode)) {
//				result = processFlowEngine.getFundCategories(dataMap, processCode);
//			} else if (MutualFundContant.GET_FUND_ID_DETAILS.equals(processCode)) {
//				result = processFlowEngine.getFundById(dataMap, processCode);
//			} else {
//				processFlowEngine.getProcessInstance(dataMap.get("id").toString());
//			}

		} catch (JsonProcessingException e) {
			log.error("JSON parse error: {}", e.getMessage(), e);
		}

		return result;
	}
}
