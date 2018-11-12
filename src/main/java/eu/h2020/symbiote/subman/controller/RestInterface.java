package eu.h2020.symbiote.subman.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.FederatedResource;
import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.cloud.model.internal.Subscription;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;
import eu.h2020.symbiote.subman.messaging.Consumers;
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
	
	private static FederationRepository fedRepo;

	private FederatedResourceRepository fedResRepo;
	
	private SubscriptionRepository subscriptionRepo;
	
	public static ObjectMapper mapper = new ObjectMapper();
	
	@Autowired
    public RestInterface(RabbitManager rabbitManager, SecurityManager securityManager, FederationRepository fedRepo, FederatedResourceRepository fedResRepo, SubscriptionRepository subscriptionRepository) {
        this.rabbitManager = rabbitManager;
        this.securityManager = securityManager;
        RestInterface.fedRepo = fedRepo;
        this.fedResRepo = fedResRepo;
        this.subscriptionRepo = subscriptionRepository;
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

		logger.info("resourcesAddedOrUpdated HTTP-POST request received: " + receivedJson);
		ResourcesAddedOrUpdatedMessage receivedMessage;
		try {
			receivedMessage = mapper.readValue(receivedJson, ResourcesAddedOrUpdatedMessage.class);
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

		//store received federatedResources to mongoDB
		for (FederatedResource fr : receivedMessage.getNewFederatedResources()) {
			fedResRepo.save(fr);
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

		logger.info("resourcesDeleted HTTP-POST request received: " + receivedJson);
		ResourcesDeletedMessage receivedMessage;
		
		try {
			receivedMessage = mapper.readValue(receivedJson, ResourcesDeletedMessage.class);
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

		//delete federatedResources from given federations, or delete from mongoDB if removed from all federations
		for (String symbioteId : receivedMessage.getDeletedFederatedResources()) {
			String [] splitSymbioteId = symbioteId.split("@");
			FederatedResource current = fedResRepo.findOne(splitSymbioteId[0]+"@"+splitSymbioteId[1]);
			if(current != null) {
				current.unshareFromFederation(splitSymbioteId[2]);
				if(current.getFederatedResourceInfoMap().size() > 0) fedResRepo.save(current); // if fedRes is shared in another federations save it without deleted one
				else fedResRepo.delete(splitSymbioteId[0]+"@"+splitSymbioteId[1]);
			}
			else continue;
		}
		
        //forward message to PR via RMQ (check if single or list)
		rabbitManager.sendAsyncMessageJSON(PRexchange, PRremovedFedResRK, receivedMessage);

		logger.info("ResourcesDeleted request succesfully processed!");
		return AuthorizationServiceHelper.addSecurityService(new HttpHeaders(), HttpStatus.OK,
				(String) securityResponse.getBody());
	}
	
	/**
	 * Method receives subscription definition update from platform owner.
	 * platformId of defined subscription must match this platformId,
	 * and then subscription is stored to mongoDB. After that federatedResource repository
	 * is iterated and fedRes that do not match the new subscription are removed and PR is
	 * notified about it.
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
			subscription = mapper.readValue(receivedJson, Subscription.class);
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
		subscriptionRepo.save(subscription);
		
		//send this new subscription to all platforms that are federated with home platform
		for(String federatedPlatformId : Consumers.numberOfCommonFederations.keySet()) {
			sendOwnSubscription(federatedPlatformId);
		}
		
		clearUnsubscribedFederatedResourcesAndNotifyPR();
		
		logger.info("Subscribe request succesfully processed and subscription is updated!");
		
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
			subscription = mapper.readValue(receivedJson, Subscription.class);
		} catch (Exception e) {
			logger.info("Exception trying to map received json to Subscription object!");
			return new ResponseEntity<>("Received JSON message cannot be mapped to Subscription!", HttpStatus.BAD_REQUEST);
		}
		
		//fetch common federationIDs with platform that sent the subscription
		List<String> federatedHomeAndReceived = findCommonFederations(subscription.getPlatformId(), platformId);
		
		if(federatedHomeAndReceived.size() == 0)
			return new ResponseEntity<>("Sender platform and receiving platfrom are not federated!", HttpStatus.BAD_REQUEST);
		
		//verify security headers
		ResponseEntity<?> securityResponse = AuthorizationServiceHelper
				.checkSecurityRequestAndCreateServiceResponse(securityManager, httpHeaders, subscription.getPlatformId());
		if (securityResponse.getStatusCode() != HttpStatus.OK) {
			logger.info("Request failed authorization check!");
			return securityResponse;
		}
		
		subscriptionRepo.save(subscription);
		logger.info("Subscription request succesfully processed!");
		
		Consumers.processSendingExistingFederatedResources(subscription.getPlatformId(), federatedHomeAndReceived, platformId);
		
		return AuthorizationServiceHelper.addSecurityService(new HttpHeaders(), HttpStatus.OK,
				(String) securityResponse.getBody());
	}
	
	/**
	 * Endpoint for fetching all existing subscriptions. Method receives HTTP GET request on
	 * /subscriptionManager/subscriptions path and returns all available subscriptions from
	 * subscription repository.
	 * 
	 * @param httpHeaders
	 * @return
	 * @throws JsonProcessingException
	 */
	@RequestMapping(value = "/subscriptionManager/subscriptions", method = RequestMethod.GET)
	public ResponseEntity<?> getAllSubscriptions(@RequestHeader HttpHeaders httpHeaders) throws JsonProcessingException {
		
		logger.info("HTTP-GET request received for fetching all existing subscriptions.");
		List<Subscription> allSubscriptions = subscriptionRepo.findAll();
		
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		return new ResponseEntity<>(mapper.writeValueAsString(allSubscriptions), headers,  HttpStatus.OK);
	}
	
	/**
	 * Endpoint for fetching Subscription with specified platformId.
	 * Returns 404 if specified subscription is not found, and 200 with JSON subscription if it exists.
	 * 
	 * @param httpHeaders
	 * @param id
	 * @return
	 * @throws JsonProcessingException
	 */
	@RequestMapping(value = "/subscriptionManager/subscription/{platformId}", method = RequestMethod.GET)
	public ResponseEntity<?> getSpecificSubscription(@RequestHeader HttpHeaders httpHeaders,@PathVariable(value="platformId") String id) throws JsonProcessingException {
		
		logger.info("HTTP-GET request received for fetching subscription with id:" + id + " received.");
		Subscription sub = subscriptionRepo.findOne(id);
		
		if(sub == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		else {	
			HttpHeaders headers = new HttpHeaders();
			headers.add("Content-Type", "application/json");
			return new ResponseEntity<>(mapper.writeValueAsString(sub), headers, HttpStatus.OK);
		}
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
	 * Method creates securityRequest and sends this platform subscription object to
	 * the given federated platform.
	 * @param federatedPlatformId
	 */
	private void sendOwnSubscription (String federatedPlatformId) {
		// Wrap in try/catch to avoid requeuing
        try {
		//send HTTP-POST of own subscription
		SecurityRequest securityRequest = securityManager.generateSecurityRequest();
        if (securityRequest != null) {
            logger.debug("Security Request created successfully!");

            // if the creation of securityRequest is successful send it to the federated platform  
			Consumers.sendSecurityRequestAndVerifyResponse(securityRequest,
					mapper.writeValueAsString(subscriptionRepo.findOne(platformId)),
					Consumers.addressBook.get(federatedPlatformId).replaceAll("/+$", "") + "/subscriptionManager" + "/subscription",
					federatedPlatformId);       
        } else
            logger.info(
                    "Failed to send own subscription message due to securityRequest creation failure!");
        } catch (Exception e) {
            logger.warn("Exception thrown during processing federationMember addition.", e);
        }
	}
	
	/**
	 * Method removes all federated resources from local repository that are not shared by this platform
	 * and that do not fit the new subscription of home platform.
	 * It then sends a notification to Platform Registry with symbioteIds of deleted federated resources.
	 */
	private void clearUnsubscribedFederatedResourcesAndNotifyPR() {
		Set<String> PRnotification = new HashSet<>();
		
		List<FederatedResource> allFederatedResources = fedResRepo.findAll();
		for(FederatedResource fr : allFederatedResources) {
			//if federatedResource is not shared by this platform
			if(!fr.getPlatformId().equals(platformId)) {
				//if this platform is not subscribed to this federated resource anymore...
				if(!Consumers.isSubscribed(subscriptionRepo.findOne(platformId), fr)) {
					//delete from fedRes repository
					fedResRepo.delete(fr.getAggregationId());
					//store symbioteIds of deleted federated resource
					for(String fedId : fr.getFederations()) {
						PRnotification.add(fr.getAggregationId()+"@"+fedId);
					}
				}
			}
		}
		//send notification to PR about deleted federated resources
		rabbitManager.sendAsyncMessageJSON(PRexchange, PRremovedFedResRK, new ResourcesDeletedMessage(PRnotification));
	}
	
	/**
	 * Method finds all existing federations containing this platform and platform with the given id.
	 * 
	 * @param otherPlatformId
	 * @return
	 */
	public static List<String> findCommonFederations(String otherPlatformId, String homePlatformId){
		List<String> federatedHomeAndReceived = new ArrayList<>();
		for(Federation federation : fedRepo.findAll()) {			
			//if this platform and other platform are in federation...
			List<String> memberIds = federation.getMembers().stream().map(FederationMember::getPlatformId).collect(Collectors.toList());
			if(memberIds != null && memberIds.contains(homePlatformId) && memberIds.contains(otherPlatformId)) federatedHomeAndReceived.add(federation.getId());			
		}
		return federatedHomeAndReceived;
	}
}