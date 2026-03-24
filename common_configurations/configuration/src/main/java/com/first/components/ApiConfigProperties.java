package com.first.components;


import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.first.dto.ApiDefinition;

@ConfigurationProperties(prefix = "apis")
public class ApiConfigProperties {

	public List<ApiDefinition> apis;
	
	public List<ApiDefinition> getApis() {
		return apis;
	}

	public void setApis(List<ApiDefinition> apis) {
		this.apis = apis;
	}
	
//	public List<ApiDefinition> list;
//
//	public List<ApiDefinition> getList() {
//		return list;
//	}
//
//	public void setList(List<ApiDefinition> list) {
//		this.list = list;
//	}

}