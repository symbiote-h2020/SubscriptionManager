package eu.h2020.symbiote.subman.controller;

import eu.h2020.symbiote.security.commons.SecurityConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

/**
 * @author Petar Krivic 09/03/2018.
 * 
 */
public class AuthorizationServiceHelper {

	public static ResponseEntity<?> checkSecurityRequestAndCreateServiceResponse(SecurityManager securityManager,
			HttpHeaders httpHeaders) {
		
		// Create the service response. If it fails, return appropriate error since there is no need to continue
		ResponseEntity<?> serviceResponseResult = securityManager.generateServiceResponse();
		if (serviceResponseResult.getStatusCode() != HttpStatus.valueOf(200))
			return serviceResponseResult;

		// Check the proper security headers. If the check fails, return appropriate error since there is no need to continue
		ResponseEntity<?> checkRequestValidity = securityManager.checkRequest(httpHeaders,
				(String) serviceResponseResult.getBody());

		return checkRequestValidity.getStatusCode() != HttpStatus.OK ? checkRequestValidity : serviceResponseResult;
	}

	public static ResponseEntity<?> addSecurityService(HttpHeaders httpHeaders, HttpStatus httpStatus,
			String serviceResponse) {
		httpHeaders.put(SecurityConstants.SECURITY_RESPONSE_HEADER, Collections.singletonList(serviceResponse));
		return new ResponseEntity<>(httpHeaders, httpStatus);
	}

	public static ResponseEntity<?> addSecurityService(Object response, HttpHeaders httpHeaders, HttpStatus httpStatus,
			String serviceResponse) {
		httpHeaders.put(SecurityConstants.SECURITY_RESPONSE_HEADER, Collections.singletonList(serviceResponse));
		return new ResponseEntity<>(response, httpHeaders, httpStatus);
	}
}
