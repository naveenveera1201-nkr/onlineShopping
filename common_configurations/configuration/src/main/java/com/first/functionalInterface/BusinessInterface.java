package com.first.functionalInterface;

import java.util.Map;

@FunctionalInterface
public interface BusinessInterface {
	public void execute(Map<String, Object> params);
}
