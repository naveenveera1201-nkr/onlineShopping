package com.constant;

public enum MutualFundContant {

	P100("p100"), P101("p101"), INVESTOR_PROFILE_REGISTRATION("p102"), INVESTOR_BANKDETAILS_REGISTRATION("p103"),
	INVESTOR_KYC_REGISTRATION("p104"), INVESTOR_NOMINEE_REGISTRATION("p105"), INVESTOR_LIST("p106"),
	GET_INVESTOR_KYC("p107"), GET_FUND_DETAILS("p200"), GET_FUND_ALL_CATEGORIES("p201"), GET_FUND_ID_DETAILS("p203");

	private final String code;

	MutualFundContant(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public boolean equals(String value) {
		return code.equalsIgnoreCase(value);
	}

}
