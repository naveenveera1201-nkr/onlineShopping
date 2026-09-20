package com.first.functionalInterface;

import java.util.Map;

@FunctionalInterface
public interface ProcessFlowInterface {
	public String execute( Map<String, Object> data, String code);
}
