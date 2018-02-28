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

import eu.h2020.symbiote.model.cim.Resource;
import eu.h2020.symbiote.subman.messaging.RabbitManager;

/**
 * SubscriptionManager REST interface. Created by Petar Krivic on
 * 30/01/2018.
 */
@RestController
public class RestInterface {

	private static final Logger logger = LoggerFactory.getLogger(RestInterface.class);
	
	@Autowired
	RabbitManager rabbitManager;
	
	@Autowired
	SecurityManager securityManager;
	
	@RequestMapping(value = "/sm", method = RequestMethod.POST)
	public ResponseEntity resourceAddedOrUpdated(@RequestBody Resource resource, @RequestHeader HttpHeaders httpHeaders) {
		
		ResponseEntity serviceResponseResult = securityManager.generateServiceResponse();
        if (serviceResponseResult.getStatusCode() != HttpStatus.valueOf(200))
            return serviceResponseResult;
        
        ResponseEntity checkListResourcesRequestValidity = securityManager
                .checkListResourcesRequest(httpHeaders, (String) serviceResponseResult.getBody());
        
        if(checkListResourcesRequestValidity.getStatusCode() != HttpStatus.OK) return checkListResourcesRequestValidity;

        //TODO forward message to PR and return HTTP 200 OK
        
        return securityManager.addSecurityService("OK", new HttpHeaders(),
                HttpStatus.OK, (String) serviceResponseResult.getBody());
		
	}
}
