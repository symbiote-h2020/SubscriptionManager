package eu.h2020.symbiote.subman.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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
	
	@Autowired
    public RestInterface(RabbitManager rabbitManager, SecurityManager securityManager) {
        this.rabbitManager = rabbitManager;
        this.securityManager = securityManager;
    }

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
}
