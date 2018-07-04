package eu.h2020.symbiote.subman.controller;

import java.util.List;
import java.util.stream.Collectors;

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
import eu.h2020.symbiote.cloud.model.internal.Subscription;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.subman.messaging.RabbitManager;
import eu.h2020.symbiote.subman.repositories.FederatedResourceRepository;
import eu.h2020.symbiote.subman.repositories.FederationRepository;
import eu.h2020.symbiote.subman.repositories.SubscriptionRepository;

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
	
	@Value("${platform.id}")
	private String platformId;
	
	@Value("${rabbit.exchange.platformRegistry.name}")
	private String PRexchange;
	
	@Value("${rabbit.routingKey.platformRegistry.addOrUpdateFederatedResources}")
	private String PRaddedOrUpdatedFedResRK;
	
	@Value("${rabbit.routingKey.platformRegistry.removeFederatedResources}")
	private String PRremovedFedResRK;
	
	private FederationRepository fedRepo;

	private FederatedResourceRepository fedResRepo;
	
	private SubscriptionRepository subRepo;
	
	public static ObjectMapper om = new ObjectMapper();
	
	@Autowired
    public RestInterface(RabbitManager rabbitManager, SecurityManager securityManager, FederationRepository fedRepo, FederatedResourceRepository fedResRepo, SubscriptionRepository subscriptionRepository) {
        this.rabbitManager = rabbitManager;
        this.securityManager = securityManager;
        this.fedRepo = fedRepo;
        this.fedResRepo = fedResRepo;
        this.subRepo = subscriptionRepository;
    }

	/**
	 * Endpoint for communication of federated SMs, when new resources are shared.
	 * Method checks that sender platform is in federations where resource is being shared,
	 * it validates security headers and if everything is ok, it forwards received data
	 * to PR component.
	 * 
	 * @param httpHeaders
	 * @param receivedJson
	 * @return
	 */
	@RequestMapping(value = "/subscriptionManager/addOrUpdate", method = RequestMethod.POST)
	public ResponseEntity<?> resourcesAddedOrUpdated(@RequestHeader HttpHeaders httpHeaders,
			@RequestBody String receivedJson) {

		logger.info("resourcesAddedOrUpdated HTTP-POST request received.");
		ResourcesAddedOrUpdatedMessage receivedMessage;
		try {
			receivedMessage = om.readValue(receivedJson, ResourcesAddedOrUpdatedMessage.class);
		} catch (Exception e) {
			logger.info("Exception trying to map received json to ResourcesAddedOrUpdatedMessage object!");
			return new ResponseEntity<>("Received JSON message cannot be mapped to ResourcesAddedOrUpdatedMessage!",HttpStatus.BAD_REQUEST);
		}
		
		String senderPlatformId = receivedMessage.getNewFederatedResources().get(0).getPlatformId();
		//for every FedRes check if sender platformId is in federations where fedRes is being shared
		if(!checkPlatformIdInFederationsCondition1(senderPlatformId, receivedMessage))
			return new ResponseEntity<>("Sender not allowed to share all received federated resources, because sender platform is not member of all federations where resource is being shared!",HttpStatus.BAD_REQUEST);

		ResponseEntity<?> securityResponse = AuthorizationServiceHelper
				.checkSecurityRequestAndCreateServiceResponse(securityManager, httpHeaders, senderPlatformId);
		if (securityResponse.getStatusCode() != HttpStatus.OK) {
			logger.info("Request failed authorization check!");
			return securityResponse;
		}

		//forward message to PR via RMQ
		rabbitManager.sendAsyncMessageJSON(PRexchange, PRaddedOrUpdatedFedResRK, receivedMessage);

		logger.info("ResourcesAddedOrUpdated request succesfully processed!");
		return AuthorizationServiceHelper.addSecurityService(new HttpHeaders(), HttpStatus.OK,
				(String) securityResponse.getBody());
	}

	/**
	 * Endpoint for communication of federated SMs, when shared resources are removed.
	 * Method checks that all federated resources exist, and that the platform that shared
	 * them is allowed to delete them. It also validates security headers and if everything is ok,
	 * received data is forwarded to PR component.
	 * 
	 * @param httpHeaders
	 * @param receivedJson
	 * @return
	 */
	@RequestMapping(value = "/subscriptionManager/delete", method = RequestMethod.POST)
	public ResponseEntity<?> resourcesDeleted(@RequestHeader HttpHeaders httpHeaders, @RequestBody String receivedJson) {

		logger.info("resourcesDeleted HTTP-POST request received.");
		ResourcesDeletedMessage receivedMessage;
		
		try {
			receivedMessage = om.readValue(receivedJson, ResourcesDeletedMessage.class);
		} catch (Exception e) {
			logger.info("Exception trying to map received json to ResourcesDeletedMessage object!");
			return new ResponseEntity<>("Received JSON message cannot be mapped to ResourcesDeletedMessage!", HttpStatus.BAD_REQUEST);
		}

        //check that platform that shared received fedRes is in federations where the resource is being unshared
		if(!checkPlatformIdInFederationsCondition2(receivedMessage))
		    return new ResponseEntity<>("The platform that shared the resource is not in the federation",
                    HttpStatus.BAD_REQUEST);

        //get sender platformId
		String [] symbioteIdParts = receivedMessage.getDeletedFederatedResources().iterator().next().split("@"); //nonce@platformId@federationId

        ResponseEntity<?> securityResponse = AuthorizationServiceHelper
				.checkSecurityRequestAndCreateServiceResponse(securityManager, httpHeaders, symbioteIdParts[1]);
		if (securityResponse.getStatusCode() != HttpStatus.OK) {
			logger.info("Request failed authorization check!");
			return securityResponse;
		}

        //forward message to PR via RMQ (check if single or list)
		rabbitManager.sendAsyncMessageJSON(PRexchange, PRremovedFedResRK, receivedMessage);

		logger.info("ResourcesDeleted request succesfully processed!");
		return AuthorizationServiceHelper.addSecurityService(new HttpHeaders(), HttpStatus.OK,
				(String) securityResponse.getBody());
	}
	
	/**
	 * Method receives subscription definition from platform owner.
	 * platformId of defined subscription must match this platformId,
	 * and then subscription is stored to mongoDB.
	 * 
	 * @param httpHeaders
	 * @param receivedJson
	 * @return
	 */
	@RequestMapping(value = "/subscriptionManager/subscribe", method = RequestMethod.POST)
	public ResponseEntity<?> subscriptionDefinition(@RequestHeader HttpHeaders httpHeaders, @RequestBody String receivedJson) {

		logger.info("Platform owner subscription definition HTTP-POST request received.");
		Subscription subscription;

		try {
			subscription = om.readValue(receivedJson, Subscription.class);
		} catch (Exception e) {
			logger.info("Exception trying to map received json to Subscription object!");
			return new ResponseEntity<>("Received JSON message cannot be mapped to Subscription!", HttpStatus.BAD_REQUEST);
		}
		
		//check that platformId matches the one of the platform
		if(!subscription.getPlatformId().equals(platformId)) {
			logger.info("Matching failure of received platformId and this platformId!");
			return new ResponseEntity<>("PlatformId check failed!", HttpStatus.BAD_REQUEST);
		}

		//if everything is ok, update platform subscription in mongoDB
		subRepo.save(subscription);
		logger.info("Subscribe request succesfully processed!");
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	/**
	 * Method receives subscription from federated platform.
	 * After security headers verification, it is also verified that
	 * sender platform and this platform are federated. If true, subscription is 
	 * stored to mongoDB and HTTP OK response is sent.
	 * 
	 * @param httpHeaders
	 * @param receivedJson
	 * @return
	 */
	@RequestMapping(value = "/subscriptionManager/subscription", method = RequestMethod.POST)
	public ResponseEntity<?> foreignSubscriptionDefinition(@RequestHeader HttpHeaders httpHeaders, @RequestBody String receivedJson) {
		
		logger.info("Subscription definition HTTP-POST request received.");
		Subscription subscription;
		
		try {
			subscription = om.readValue(receivedJson, Subscription.class);
		} catch (Exception e) {
			logger.info("Exception trying to map received json to Subscription object!");
			return new ResponseEntity<>("Received JSON message cannot be mapped to Subscription!", HttpStatus.BAD_REQUEST);
		}
		
		//check that sender platform id and this platform id are in federation
		if(!checkPlatformIdInFederationsCondition3(subscription.getPlatformId()))
			return new ResponseEntity<>("Sender platform and receiving platfrom are not federated!", HttpStatus.BAD_REQUEST);
		
		//verify security headers
		ResponseEntity<?> securityResponse = AuthorizationServiceHelper
				.checkSecurityRequestAndCreateServiceResponse(securityManager, httpHeaders, subscription.getPlatformId());
		if (securityResponse.getStatusCode() != HttpStatus.OK) {
			logger.info("Request failed authorization check!");
			return securityResponse;
		}
		
		subRepo.save(subscription);
		logger.info("Subscription request succesfully processed!");
		
		return AuthorizationServiceHelper.addSecurityService(new HttpHeaders(), HttpStatus.OK,
				(String) securityResponse.getBody());
	}
	
	/**
	 * Method checks if senders PlatformId is in all federations where federated resource is being shared.
	 * Returns true, if sender id is in federations where resource is shared, or false if not.
	 * 
	 * @param senderPlatformId
	 * @param receivedMessage
	 * @return
	 */
	public boolean checkPlatformIdInFederationsCondition1(String senderPlatformId, ResourcesAddedOrUpdatedMessage receivedMessage){
		
		boolean requestOk = false;
		for(FederatedResource fedRes : receivedMessage.getNewFederatedResources()){
			
			for(String federationId : fedRes.getFederatedResourceInfoMap().keySet()){
				Federation current = fedRepo.findOne(federationId);
				if(current == null) return false;
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
	
	/**
	 * Method checks that  platform which shared federated resource, is in all
	 * federations where it is being deleted. It returns true if ok, and false if 
	 * there are non-existing federated resources or federations, or if federated resource 
	 * is shared by a platform that is not in federations where it is being deleted.
	 * 
	 * @param rdm
	 * @return
	 */
	public boolean checkPlatformIdInFederationsCondition2(ResourcesDeletedMessage rdm){

		boolean requestOk = false;
		//for every received federatedResourceId
		for (String deletedSymbioteId : rdm.getDeletedFederatedResources()) {
			String [] symbioteIdParts = deletedSymbioteId.split("@"); // nonce@platformId@federationId
			String aggregatedId = symbioteIdParts[0] + "@" + symbioteIdParts[1];
			
			//check that federated resource exists in mongoDB
			FederatedResource fedRes = fedResRepo.findOne(aggregatedId);
			if(fedRes == null) return false;
			
			//check that federation exists
			Federation fed = fedRepo.findOne(symbioteIdParts[2]);
			if(fed == null) return false;
			
			requestOk = false;
			for(FederationMember fm : fed.getMembers()){
				if(fm.getPlatformId().equals(symbioteIdParts[1])){
					requestOk = true;
					break;
				}
			}
			if(!requestOk) return false;
		}
		return true;
	}
	
	/**
	 * Method checks if received senderPlatformId and this platformId are in federation.
	 * 
	 * @param senderPlatformId
	 * @return
	 */
	public boolean checkPlatformIdInFederationsCondition3(String senderPlatformId) {
		
		for(Federation federation : fedRepo.findAll()) {			
			//if this platform and sender platform are in federation...
			List<String> memberIds = federation.getMembers().stream().map(FederationMember::getPlatformId).collect(Collectors.toList());
			if(memberIds.contains(platformId) && memberIds.contains(senderPlatformId)) return true;			
		}
		return false;
	}
	
}
