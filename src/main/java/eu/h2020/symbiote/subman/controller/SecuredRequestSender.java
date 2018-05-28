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

import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;

public class SecuredRequestSender {
	
	private static final Logger logger = LoggerFactory.getLogger(RestInterface.class);
	
	private static RestTemplate restTemplate = new RestTemplate();
	
	/**
	 * Method sends HTTP POST request with corresponding security headers that contains given JSON string
	 * to the given url. It returns received response entity.
	 * 
	 * @param securityRequest
	 * @param objectJson
	 * @param completeRequestUrl
	 * @return
	 */
	public static ResponseEntity<?> sendSecuredRequest(SecurityRequest securityRequest, String objectJson, String completeRequestUrl) {
		
		Map<String, String> securityRequestHeaders;
		HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		try {
			securityRequestHeaders = securityRequest.getSecurityRequestHeaderParams();			
			for (Map.Entry<String, String> entry : securityRequestHeaders.entrySet()) {
	            httpHeaders.add(entry.getKey(), entry.getValue());
	        }
	        logger.debug("request headers: " + httpHeaders);
	        
	        HttpEntity<String> httpEntity = new HttpEntity<>(objectJson, httpHeaders);
	        ResponseEntity<?> responseEntity = null;
	        try {
	        	logger.debug("body = " + completeRequestUrl);
				logger.debug("url = " + completeRequestUrl);

				responseEntity = restTemplate.exchange(completeRequestUrl, HttpMethod.POST, httpEntity, Object.class);

	            logger.debug("response = " + responseEntity);
	            logger.debug("headers = " + responseEntity.getHeaders());
	            logger.debug("body = " + responseEntity.getBody());
	            
	            return responseEntity;
	        }  catch (Exception e) {
	            logger.warn("Error executing HTTP POST request!");
	            return new ResponseEntity<>(e.getMessage(), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
	        }
	        
		} catch (JsonProcessingException e) {
			logger.warn("Error parsing securityRequest headers!");
			return new ResponseEntity<>(e.getMessage(), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
}
