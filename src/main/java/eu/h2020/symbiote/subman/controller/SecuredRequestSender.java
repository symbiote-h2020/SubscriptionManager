package eu.h2020.symbiote.subman.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;

public class SecuredRequestSender {
	
	private static final Logger logger = LoggerFactory.getLogger(RestInterface.class);
	
	private static ObjectMapper om = new ObjectMapper();
	
	private static RestTemplate restTemplate = new RestTemplate();
	
	public static ResponseEntity<?> sendSecuredResourcesAddedOrUpdated(SecurityRequest securityRequest, ResourcesAddedOrUpdatedMessage rsMsg, String interworkingServiceUrl) {

		String url = interworkingServiceUrl+"/subscriptionManager"+"/addOrUpdate";
		Map<String, String> securityRequestHeaders;
		HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		try {
			securityRequestHeaders = securityRequest.getSecurityRequestHeaderParams();			
			for (Map.Entry<String, String> entry : securityRequestHeaders.entrySet()) {
	            httpHeaders.add(entry.getKey(), entry.getValue());
	        }
	        logger.info("request headers: " + httpHeaders);
	        
	        HttpEntity<String> httpEntity = new HttpEntity<>(om.writeValueAsString(rsMsg), httpHeaders);
	        ResponseEntity<?> responseEntity = null;
	        try{
	            responseEntity = restTemplate.exchange(url, HttpMethod.POST, httpEntity, Object.class);
	            
	            logger.info("response = " + responseEntity);
	            logger.info("headers = " + responseEntity.getHeaders());
	            logger.info("body = " + responseEntity.getBody());
	            
	            return responseEntity;
	        }  catch (Exception e) {
	            logger.info("Error executing HTTP POST request!");
	            return new ResponseEntity<>(e.getMessage(), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
	        }
	        
		} catch (JsonProcessingException e) {
			logger.info("Error parsing securityRequest headers!");
			return new ResponseEntity<>(e.getMessage(), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	public static ResponseEntity<?> sendSecuredResourcesDeleted(SecurityRequest securityRequest, ResourcesDeletedMessage rsMsg, String interworkingServiceUrl) {

		String url = interworkingServiceUrl+"/subscriptionManager"+"/delete";
		Map<String, String> securityRequestHeaders;
		HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		try {
			securityRequestHeaders = securityRequest.getSecurityRequestHeaderParams();			
			for (Map.Entry<String, String> entry : securityRequestHeaders.entrySet()) {
	            httpHeaders.add(entry.getKey(), entry.getValue());
	        }
	        logger.info("request headers: " + httpHeaders);
	        
	        HttpEntity<String> httpEntity = new HttpEntity<>(om.writeValueAsString(rsMsg), httpHeaders);
	        ResponseEntity<?> responseEntity = null;
	        try{
	            responseEntity = restTemplate.exchange(url, HttpMethod.POST, httpEntity, Object.class);
	            
	            logger.info("response = " + responseEntity);
	            logger.info("headers = " + responseEntity.getHeaders());
	            logger.info("body = " + responseEntity.getBody());
	            
	            return responseEntity;
	        }  catch (Exception e) {
	            logger.info("Error executing HTTP POST request!");
	            return new ResponseEntity<>(e.getMessage(), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
	        }
	        
		} catch (JsonProcessingException e) {
			logger.info("Error parsing securityRequest headers!");
			return new ResponseEntity<>(e.getMessage(), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

}
