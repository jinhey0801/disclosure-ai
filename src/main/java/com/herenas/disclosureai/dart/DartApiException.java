package com.herenas.disclosureai.dart;

public class DartApiException extends RuntimeException {

	public DartApiException(String status, String message) {
		super("DART API error [" + status + "] " + message);
	}
}
