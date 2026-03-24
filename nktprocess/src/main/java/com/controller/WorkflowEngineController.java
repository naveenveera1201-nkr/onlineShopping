package com.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ProcessFlowEngine;
import com.configs.registries.process.ProcessRegistry;
import com.constant.MutualFundContant;
import com.exceptions.ProcessFlowException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resource.WorkflowEngineResource;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class WorkflowEngineController implements WorkflowEngineResource {

	@Autowired
	private ProcessFlowEngine processFlowEngine;
	
	@Override
	public String process(@RequestParam("data") String data, @RequestParam("code") String code) {

		log.info("ProcessWorkflowController method called");
		String result = "";

		try {

			ObjectMapper mapper = new ObjectMapper();
			
			String processCode = processFlowEngine.getProcessRegistry().get(code).getCode();

			Map<String, Object> dataMap = mapper.readValue(data, new TypeReference<Map<String, Object>>() {
			});

			if (MutualFundContant.P100.equals(processCode) && Objects.isNull(dataMap.get("action"))) {
				result = processFlowEngine.createProcessInstance(processCode, "naveen", dataMap);
			} else if (MutualFundContant.P100.equals(processCode)) {
				result = processFlowEngine.performAction(dataMap.get("id").toString(), dataMap.get("action").toString(),
						"naveen", "naveen");
			} else if (MutualFundContant.INVESTOR_PROFILE_REGISTRATION.equals(processCode)
					|| MutualFundContant.INVESTOR_BANKDETAILS_REGISTRATION.equals(processCode)
					|| MutualFundContant.INVESTOR_NOMINEE_REGISTRATION.equals(processCode)
					|| MutualFundContant.INVESTOR_KYC_REGISTRATION.equals(processCode)) {
				result = processFlowEngine.registration(dataMap, processCode);
			} else if (MutualFundContant.INVESTOR_LIST.equals(processCode)
//					|| MutualFundContant.GET_FUND_DETAILS.equals(processCode)
					) {
				result = processFlowEngine.getInvesterProfile(processCode);
			} else if (MutualFundContant.GET_INVESTOR_KYC.equals(processCode)) {
				result = processFlowEngine.getProfileDetails(dataMap.get("id").toString(),processCode);
			} else if (MutualFundContant.GET_FUND_DETAILS.equals(processCode)) {
				result = processFlowEngine.getAllFunds(dataMap,processCode);
			} else if (MutualFundContant.GET_FUND_ALL_CATEGORIES.equals(processCode)) {
				result = processFlowEngine.getFundCategories(dataMap,processCode);
			}else if (MutualFundContant.GET_FUND_ID_DETAILS.equals(processCode)) {
				result = processFlowEngine.getFundById(dataMap,processCode);
			} else {
				processFlowEngine.getProcessInstance(dataMap.get("id").toString());
			}
		} catch (ProcessFlowException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}

}
