package eu.h2020.symbiote.subman.controller;

import org.springframework.http.ResponseEntity;

import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;

public class SecuredRequestSender {
	
	public static ResponseEntity<?> sendSecuredResourcesAddedOrUpdated(SecurityRequest securityRequest, ResourcesAddedOrUpdatedMessage rsMsg, String url) {
		//TODO implement sending of HTTPS request..
		
		return null;
	}
	
	public static ResponseEntity<?> sendSecuredResourcesDeleted(SecurityRequest securityRequest, ResourcesDeletedMessage rsMsg, String url) {
		//TODO implement sending of HTTPS request..
		
		return null;
	}

}
