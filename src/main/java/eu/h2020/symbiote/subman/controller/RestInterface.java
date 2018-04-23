package eu.h2020.symbiote.subman.controller;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.FederatedResource;
import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.subman.messaging.RabbitManager;
import eu.h2020.symbiote.subman.repositories.FederatedResourceRepository;
import eu.h2020.symbiote.subman.repositories.FederationRepository;

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
	
	@Value("${rabbit.exchange.platformRegistry.name}")
	private String PRexchange;
	
	@Value("${rabbit.routingKey.platformRegistry.addOrUpdateFederatedResources}")
	private String PRaddedOrUpdatedFedResRK;
	
	@Value("${rabbit.routingKey.platformRegistry.removeFederatedResources}")
	private String PRremovedFedResRK;
	
	private FederationRepository fedRepo;

	private FederatedResourceRepository fedResRepo;
	
	public static ObjectMapper om = new ObjectMapper();
	
	@Autowired
    public RestInterface(RabbitManager rabbitManager, SecurityManager securityManager, FederationRepository fedRepo, FederatedResourceRepository fedResRepo) {
        this.rabbitManager = rabbitManager;
        this.securityManager = securityManager;
        this.fedRepo = fedRepo;
        this.fedResRepo = fedResRepo;
    }

	/**
	 * Endpoint for communication of federated SMs, when new resources are shared.
	 * @param httpHeaders
	 * @param receivedJson
	 * @return
	 */
	@RequestMapping(value = "/subscriptionManager/addOrUpdate", method = RequestMethod.POST)
	public ResponseEntity<?> resourcesAddedOrUpdated(@RequestHeader HttpHeaders httpHeaders,
			@RequestBody String receivedJson) {

		logger.info("resourcesAddedOrUpdated HTTP-POST request received.");
		ResourcesAddedOrUpdatedMessage receivedMessage = mapAddedOrUpdatedMessage(receivedJson);
		if(receivedMessage == null) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		
		//assuming all received federatedResources are from the same request-sender platform
		String senderPlatformId = receivedMessage.getNewFederatedResources().get(0).getPlatformId();
		//for every FedRes check if sender platformId is in federation with federationId
		if(!checkPlatformIdInFederationsCondition1(senderPlatformId, receivedMessage))return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

		ResponseEntity<?> securityResponse = AuthorizationServiceHelper
				.checkSecurityRequestAndCreateServiceResponse(securityManager, httpHeaders, senderPlatformId);
		if (securityResponse.getStatusCode() != HttpStatus.OK) {
			logger.info("Request failed authorization check!");
			return securityResponse;
		}

		//forward message to PR via RMQ
		rabbitManager.sendAsyncMessageJSON(PRexchange, PRaddedOrUpdatedFedResRK, receivedMessage);

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
	@RequestMapping(value = "/subscriptionManager/delete", method = RequestMethod.POST)
	public ResponseEntity<?> resourcesDeleted(@RequestHeader HttpHeaders httpHeaders, @RequestBody String receivedJson) {

		logger.info("resourcesDeleted HTTP-POST request received.");
		ResourcesDeletedMessage receivedMessage = mapDeletedMessage(receivedJson);

		if(receivedMessage == null) return new ResponseEntity<>("Received message is null", HttpStatus.BAD_REQUEST);

        //check that platform that shared received fedRes is in federations where the resource is being unshared(received in request)
		if(!checkPlatformIdInFederationsCondition2(receivedMessage))
		    return new ResponseEntity<>("The platform that shared the resource is not in the federation",
                    HttpStatus.BAD_REQUEST);

        //fetch any fedRes on its id and get platfromId
		String senderPlatformId = fedResRepo.findOne(receivedMessage.getDeletedFederatedResourcesMap().keySet().iterator().next()).getPlatformId();

        ResponseEntity<?> securityResponse = AuthorizationServiceHelper
				.checkSecurityRequestAndCreateServiceResponse(securityManager, httpHeaders, senderPlatformId);
		if (securityResponse.getStatusCode() != HttpStatus.OK) {
			logger.info("Request failed authorization check!");
			return securityResponse;
		}

        logger.info("5");

        //forward message to PR via RMQ (check if single or list)
		rabbitManager.sendAsyncMessageJSON(PRexchange, PRremovedFedResRK, receivedMessage);

		logger.info("Request succesfully executed!");
		return AuthorizationServiceHelper.addSecurityService(new HttpHeaders(), HttpStatus.OK,
				(String) securityResponse.getBody());
	}
	
	@RequestMapping(value = "/subscriptionManager/subscribe", method = RequestMethod.POST)
	public ResponseEntity<?> subscriptionDefinition(@RequestHeader HttpHeaders httpHeaders, @RequestBody String receivedJson) {
		//TODO implement subscription model and receiving definition request
		return new ResponseEntity<>(httpHeaders, HttpStatus.LOCKED);
	}
	
	@RequestMapping(value = "/subscriptionManager/unsubscribe", method = RequestMethod.POST)
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
	
	public boolean checkPlatformIdInFederationsCondition1(String senderPlatformId, ResourcesAddedOrUpdatedMessage receivedMessage){
		
		boolean requestOk = false;
		for(FederatedResource fedRes : receivedMessage.getNewFederatedResources()){
			
			for(String federationId : fedRes.getCloudResource().getFederationInfo().getSharingInformation().keySet()){
				Federation current = fedRepo.findOne(federationId);
				if(current == null)return false;
				requestOk = false;
				for (FederationMember fedMem : current.getMembers()){
					if(fedMem.getPlatformId().equals(senderPlatformId)) {
						requestOk = true;
						break;
					}
				}
				if(!requestOk)return false;
			}			
		}
		return true;
	}
	
	public boolean checkPlatformIdInFederationsCondition2(ResourcesDeletedMessage rdm){

	    boolean requestOk = false;
		//for every received federatedResourceId
		for (String fedResId : rdm.getDeletedFederatedResourcesMap().keySet()) {
		    logger.debug(fedResId);
			FederatedResource fedRes = fedResRepo.findOne(fedResId);
			if(fedRes == null) return false;

			//fetch platformId of platform that shared FederatedResource
			String platformIdToCheck = fedRes.getPlatformId();
			for(String fedId : rdm.getDeletedFederatedResourcesMap().get(fedResId)){
				Federation fed = fedRepo.findOne(fedId);
				if(fed == null) return false;

                requestOk = false;
				//check that every received federation where fedRes is unshared contains platform that shared the resource
				for(FederationMember fm : fed.getMembers()){
					if(fm.getPlatformId().equals(platformIdToCheck)){
						requestOk = true;
						break;
					}
				}

				if(!requestOk) return false;
			}			
		}
		return true;
	}
	
}
