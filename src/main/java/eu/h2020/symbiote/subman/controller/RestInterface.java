package eu.h2020.symbiote.subman.controller;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.subman.messaging.RabbitManager;

/**
 * SubscriptionManager REST interface.
 * 
 * @author Petar Krivic 30/01/2018.
 */
@RestController
public class RestInterface {

	private static final Logger logger = LoggerFactory.getLogger(RestInterface.class);

	private RabbitManager rabbitManager;

	private SecurityManager securityManager;
	
	public static ObjectMapper om = new ObjectMapper();
	
	@Autowired
    public RestInterface(RabbitManager rabbitManager, SecurityManager securityManager) {
        this.rabbitManager = rabbitManager;
        this.securityManager = securityManager;
    }

	/**
	 * Endpoint for communication of federated SMs, when new resources are shared.
	 * @param httpHeaders
	 * @param receivedJson
	 * @return
	 */
	@RequestMapping(value = "/sm/add", method = RequestMethod.POST)
	public ResponseEntity<?> resourcesAddedOrUpdated(@RequestHeader HttpHeaders httpHeaders,
			@RequestBody String receivedJson) {

		logger.info("resourcesAddedOrUpdated HTTP-POST request received.");
		
		//TODO check if received platformId belongs to received federationId
		
		String senderPlatformId = null; //will receive in federatedResourceId
		ResponseEntity<?> securityResponse = AuthorizationServiceHelper
				.checkSecurityRequestAndCreateServiceResponse(securityManager, httpHeaders, senderPlatformId);
		if (securityResponse.getStatusCode() != HttpStatus.OK) {
			logger.info("Request failed authorization check!");
			return securityResponse;
		}

		// TODO forward message to PR via RMQ (check if single or list)

		logger.info("Request succesfully executed!");
		return AuthorizationServiceHelper.addSecurityService(new HttpHeaders(), HttpStatus.OK,
				(String) securityResponse.getBody());

	}

	/**
	 * Endpoint for communication of federated SMs, when shared resources are removed.
	 * @param httpHeaders
	 * @param receivedJson
	 * @return
	 */
	@RequestMapping(value = "/sm/delete", method = RequestMethod.POST)
	public ResponseEntity<?> resourcesDeleted(@RequestHeader HttpHeaders httpHeaders, @RequestBody String receivedJson) {

		logger.info("resourcesDeleted HTTP-POST request received.");
		//TODO check if received platformId belongs to received federationId
		
		String senderPlatformId = null; //will receive in federatedResourceId
		ResponseEntity<?> securityResponse = AuthorizationServiceHelper
				.checkSecurityRequestAndCreateServiceResponse(securityManager, httpHeaders, senderPlatformId);
		if (securityResponse.getStatusCode() != HttpStatus.OK) {
			logger.info("Request failed authorization check!");
			return securityResponse;
		}

		// TODO forward message to PR via RMQ (check if single or list)

		logger.info("Request succesfully executed!");
		return AuthorizationServiceHelper.addSecurityService(new HttpHeaders(), HttpStatus.OK,
				(String) securityResponse.getBody());
	}
	
	@RequestMapping(value = "/sm/subscribe", method = RequestMethod.POST)
	public ResponseEntity<?> subscriptionDefinition(@RequestHeader HttpHeaders httpHeaders, @RequestBody String receivedJson) {
		//TODO implement subscription model and receiving definition request
		return new ResponseEntity<>(httpHeaders, HttpStatus.LOCKED);
	}
	
	@RequestMapping(value = "/sm/unsubscribe", method = RequestMethod.POST)
	public ResponseEntity<?> subscriptionRemoval(@RequestHeader HttpHeaders httpHeaders, @RequestBody String receivedJson) {
		//TODO implement subscription model and receiving definition request
		return new ResponseEntity<>(httpHeaders, HttpStatus.LOCKED);
	}
	
	public static ResourcesAddedOrUpdatedMessage mapAddedOrUpdatedMessage(String json){
		
		ResourcesAddedOrUpdatedMessage received;
		try {
			received = om.readValue(json, ResourcesAddedOrUpdatedMessage.class);
		} catch (IOException e) {
			logger.info("Exception trying to map received ResourcesAddedOrUpdatedMessage json to object!");
			return null;
		}
		return received;
	}
	
	public static ResourcesDeletedMessage mapDeletedMessage(String json){
		
		ResourcesDeletedMessage received;
		try {
			received = om.readValue(json, ResourcesDeletedMessage.class);
		} catch (IOException e) {
			logger.info("Exception trying to map received ResourcesDeletedMessage json to object!");
			return null;
		}
		return received;
	}
}
