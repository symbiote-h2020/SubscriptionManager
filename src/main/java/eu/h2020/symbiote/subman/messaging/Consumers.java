package eu.h2020.symbiote.subman.messaging;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.FederatedResource;
import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.cloud.model.internal.Subscription;
import eu.h2020.symbiote.model.cim.Actuator;
import eu.h2020.symbiote.model.cim.Capability;
import eu.h2020.symbiote.model.cim.Device;
import eu.h2020.symbiote.model.cim.MobileSensor;
import eu.h2020.symbiote.model.cim.Resource;
import eu.h2020.symbiote.model.cim.Sensor;
import eu.h2020.symbiote.model.cim.Service;
import eu.h2020.symbiote.model.cim.StationarySensor;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.security.commons.SecurityConstants;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;
import eu.h2020.symbiote.subman.controller.RestInterface;
import eu.h2020.symbiote.subman.controller.SecuredRequestSender;
import eu.h2020.symbiote.subman.controller.SecurityManager;
import eu.h2020.symbiote.subman.repositories.FederatedResourceRepository;
import eu.h2020.symbiote.subman.repositories.FederationRepository;
import eu.h2020.symbiote.subman.repositories.SubscriptionRepository;

/**
 * @author Petar Krivic (UniZG-FER) 28/02/2018
 */
@Component
public class Consumers {

	private static Log logger = LogFactory.getLog(Consumers.class);

	@Value("${platform.id}")
	private String platformId;

	private static FederationRepository fedRepo;
	private static FederatedResourceRepository fedResRepo;
	private static SubscriptionRepository subscriptionRepo;
	private static SecurityManager securityManager;
	
	@Autowired
	private RabbitManager rabbitManager;
	
	@Value("${rabbit.exchange.platformRegistry.name}")
	private String PRexchange;
	
	@Value("${rabbit.routingKey.platformRegistry.removeFederatedResources}")
	private String PRremovedFedResRK;

	private MessageConverter messageConverter;

	private static ObjectMapper mapper = new ObjectMapper();
	
	//<platformId,numberOfCommonFederations>
	public static Map<String, Integer> numberOfCommonFederations;
	
	//<platformId, platformInterworkingServiceURL>
	public static Map<String, String> addressBook;

	@Autowired
	public Consumers(FederationRepository fedRepo, FederatedResourceRepository fedResRepo, SubscriptionRepository subscriptionRepo, SecurityManager securityManager) {
	    Consumers.fedRepo = fedRepo;
	    Consumers.fedResRepo = fedResRepo;
	    Consumers.subscriptionRepo = subscriptionRepo;
	    Consumers.securityManager = securityManager;
	    
		messageConverter = new Jackson2JsonMessageConverter();
		numberOfCommonFederations = new HashMap<>();
		addressBook = new HashMap<>();
	}

	/**
	 * Method receives created federations from FM component, and stores them to
	 * local MongoDB.
	 * 
	 * @param msg
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.created}"))
	public void federationCreated(Message msg) {

		logger.debug("Added federation message = " + msg);
	    try {
            Federation federation = (Federation) messageConverter.fromMessage(msg);
            logger.debug("Federation = " + ReflectionToStringBuilder.toString(federation));
            logger.debug("fedRepo = " + fedRepo);
            fedRepo.save(federation);
            logger.info("Federation with id: " + federation.getId() + " added to repository.");
            
            processFederationCreated(federation);
        } catch (Exception e) {
	        logger.warn("Exception thrown during federation creation", e);
        }
	}

	/**
	 * Method receives updated federations from FM component, and stores changes
	 * to local MongoDB.
	 * 
	 * @param msg
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.changed}"))
	public void federationChanged(Message msg) {
		logger.debug("Changed federation message = " + msg);

	    try {
            Federation federation = (Federation) messageConverter.fromMessage(msg);
            //fetch current federationMembers before updating it in mongoDB
            List<String> oldMembers = fedRepo.findOne(federation.getId()).getMembers().stream().map(FederationMember::getPlatformId).collect(Collectors.toList());
            fedRepo.save(federation);
            logger.info("Federation with id: " + federation.getId() + " updated.");
            
            processFederationUpdated(federation, oldMembers);
        } catch (Exception e) {
            logger.warn("Exception thrown during federation update", e);
        }
	}

	/**
	 * Method receives id of removed federation from FM component, and deletes
	 * it from MongoDB.
	 * 
	 * @param body
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.deleted}"))
	public void federationDeleted(byte[] body) throws IOException {
	    try {
            String federationId = new String(body);
            logger.debug("Deleted federation with id = " + federationId);

            processFederationDeleted(federationId);
        } catch (Exception e) {
            logger.warn("Exception thrown during federation deletion", e);
        }
	}

	/**
	 * Method receives ResourcesAddedOrUpdated message from PlatformRegistry
	 * component. It saves locally received federated resources to MongoDB, and
	 * forwards them to other interested federated platforms.
	 * 
	 * @param msg
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "${rabbit.queueName.subscriptionManager.addOrUpdateFederatedResources}"), exchange = @Exchange(value = "${rabbit.exchange.subscriptionManager.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.addOrUpdateFederatedResources}"))
	public void addedOrUpdateFederatedResource(Message msg) {
        logger.debug("addedOrUpdateFederatedResource message = " + msg);

        // Wrap in try/catch to avoid requeuing
        try {
            // Map<PlatformId, Map<FederatedResourceId, FederatedResource>>
            Map<String, Map<String, FederatedResource>> platformMessages = new HashMap<>();

            // convert received RMQ message to ResourcesAddedOrUpdatedMessage object
            ResourcesAddedOrUpdatedMessage rsMsg = (ResourcesAddedOrUpdatedMessage) messageConverter.fromMessage(msg);
            logger.info("Received ResourcesAddedOrUpdatedMessage from Platform Registry");

            // add received FederatedResource to local MongoDB
            for (FederatedResource fr : rsMsg.getNewFederatedResources()) {	
                fedResRepo.save(fr);
                logger.info("Federated resource with aggregatedId " + fr.getAggregationId() + " added to repository and is exposed to " +
                    fr.getFederations());

                // iterate interested federations
                for (String interestedFederationId : fr.getFederations()) {

                    Federation interestedFederation = fedRepo.findOne(interestedFederationId);

                    if (interestedFederation == null) {
                        logger.info("The federation with id " + interestedFederationId + " was not found in the federation repository!");
                        continue;
                    }

                    // add all federationMembers to platformsToNotify set
                    for (FederationMember fm : interestedFederation.getMembers()) {

                    	//to avoid platform sending HTTP request to itself
                    	if(fm.getPlatformId().equals(platformId)) continue;
                    	
                    	//check if current federation member is subscribed to current federated resource                 	 
                    	if(isSubscribed(subscriptionRepo.findOne(fm.getPlatformId()), fr)) {
                    	
	                        // if platform is not yet in a list for receiving
	                        // notification, add it
	                        if (!platformMessages.containsKey(fm.getPlatformId()))
	                            platformMessages.put(fm.getPlatformId(), new HashMap<>());
	
	
	                        Map<String, FederatedResource> platformMap = platformMessages.get(fm.getPlatformId());
	
	                        // if there is not entry in the platformMap for this
	                        // federatedResource create it
	                        if (!platformMap.containsKey(fr.getAggregationId())) {
	
	                            FederatedResource clonedFr = deserializeFederatedResource(serializeFederatedResource(fr));
	                            clonedFr.clearPrivateInfo();
	                            platformMap.put(clonedFr.getAggregationId(), clonedFr);
	                        }
	
	                        // add the federation info of currently iterated federation
	                        FederatedResource platformFederatedResource = platformMap.get(fr.getAggregationId());
	                        platformFederatedResource.shareToNewFederation(interestedFederationId, fr.getCloudResource()
	                                .getFederationInfo().getSharingInformation().get(interestedFederationId).getBartering());
	                        //overwrite the entry in a map with fedRes with updated federation info
	                        platformMap.put(fr.getAggregationId(), platformFederatedResource);
                    	}
                    }
                }
            }

            // send HTTP-POST notifications to federated platforms
            
            // create securityRequest
            SecurityRequest securityRequest = securityManager.generateSecurityRequest();
            if (securityRequest != null) {
                logger.debug("Security Request created successfully!");

                // if the creation of securityRequest is successful broadcast FederatedResource to interested platforms
                for (Map.Entry<String, Map<String, FederatedResource>> entry : platformMessages.entrySet()) {

                    List<FederatedResource> resourcesForSending = new ArrayList<>(entry.getValue().values());

                    logger.debug("Sending  addedOrUpdatedFederatedResource message to platform " + entry.getKey()+ " for federated resources: " +
                            resourcesForSending.stream().map(FederatedResource::getAggregationId).collect(Collectors.toList()));

					sendSecurityRequestAndVerifyResponse(securityRequest,
							mapper.writeValueAsString(new ResourcesAddedOrUpdatedMessage(resourcesForSending)),
							addressBook.get(entry.getKey()).replaceAll("/+$", "") + "/subscriptionManager" + "/addOrUpdate",
							entry.getKey());
                }
            } else
                logger.info("Failed to broadcast addedOrUpdatedFederatedResource message due to the securityRequest creation failure!");
        } catch (Exception e) {
            logger.warn("Exception thrown during addedOrUpdateFederatedResource", e);
        }
    }

	/**
	 * Method receives ResourcesDeletedMessage message from PlatformRegistry
	 * component. It saves locally received changes to MongoDB, and forwards
	 * info to other interested federated platforms.
	 * 
	 * @param msg
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "${rabbit.queueName.subscriptionManager.removeFederatedResources}"), exchange = @Exchange(value = "${rabbit.exchange.subscriptionManager.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.removeFederatedResources}"))
	public void removeFederatedResource(Message msg) {
        logger.debug("removeFederatedResource message = " + msg);

	    // Wrap in try/catch to avoid requeuing
	    try {
            ResourcesDeletedMessage rdDel = (ResourcesDeletedMessage) messageConverter.fromMessage(msg);
            logger.info("Received ResourcesDeletedMessage from Platform Registry");
            logger.debug("SymbioteIds of unshared federated resources: " + rdDel.getDeletedFederatedResources());
            
            // <platformId, Set<symbioteIDs>>
            Map<String, Set<String>> platformMessages = new HashMap<String, Set<String>>();

            for (String symbioteId : rdDel.getDeletedFederatedResources()) {
            	String [] splitSymbioteID = symbioteId.split("@");
                FederatedResource toUpdate = fedResRepo.findOne(splitSymbioteID[0]+"@"+splitSymbioteID[1]);
                if (toUpdate == null) {
                    logger.info("The federatedResource " + splitSymbioteID[0]+"@"+splitSymbioteID[1] + " was not found in the federatedResource repository");
                    continue;
                }

                //remove federated resource from given federations
                toUpdate.unshareFromFederation(splitSymbioteID[2]);

                // if federatedResource is unshared from all federations remove it
                if (toUpdate.getFederatedResourceInfoMap().size() == 0)
                    fedResRepo.delete(toUpdate.getAggregationId());
                
                // if not save it without removed federations where it is deleted
                else
                	fedResRepo.save(toUpdate);

                // iterate federations for current FederatedResource
                
                Federation currentFederation = fedRepo.findOne(splitSymbioteID[2]);

                if (currentFederation == null) {
                	logger.info("The federation with id " + splitSymbioteID[2] + " was not found in the federation repository");
                    continue;
                }

                // iterate members
                for (FederationMember fedMember : currentFederation.getMembers()) {
                    	
                	//to avoid platform sending HTTP request to itself
                	if(fedMember.getPlatformId().equals(platformId))
                    	continue;

                    //check if current federation member is subscribed to current federated resource that is being deleted from certain federations
                    if(isSubscribed(subscriptionRepo.findOne(fedMember.getPlatformId()), toUpdate)) {
	
                    	if (!platformMessages.containsKey(fedMember.getPlatformId()))
                    		platformMessages.put(fedMember.getPlatformId(), new HashSet<>());
	
                    	Set<String> currentPlatformMessageSet = platformMessages
	                                .get(fedMember.getPlatformId());
	                        
                    	currentPlatformMessageSet.add(symbioteId);
                    }
                }
            }

            // sending created map to interested federated platforms
            SecurityRequest securityRequest = securityManager.generateSecurityRequest();
            if (securityRequest != null) {
                logger.debug("Security Request created successfully!");

                // if the creation of securityRequest is successful broadcast changes to interested platforms
                for (Map.Entry<String, Set<String>> entry : platformMessages.entrySet()) {

                    Set<String> deleteMessage = entry.getValue();

                    logger.debug("Sending unsharedFederatedResource message to platform " + entry.getKey() + " for symbioteIds: " + deleteMessage);

					sendSecurityRequestAndVerifyResponse(securityRequest,
							mapper.writeValueAsString(new ResourcesDeletedMessage(deleteMessage)),
							addressBook.get(entry.getKey()).replaceAll("/+$", "") + "/subscriptionManager" + "/delete",
							entry.getKey());
                }
            } else
                logger.info("Failed to broadcast resourcesDeleted message due to the securityRequest creation failure!");
        } catch (Exception e) {
            logger.warn("Exception thrown during removeFederatedResource", e);
        }
	}
	
	/**
	 * Method sends security request with jsonMessage content to completeUrl,
	 * and verifies that received response matches given platformId.
	 * 
	 * @param securityRequest
	 * @param jsonMessage
	 * @param completeUrl
	 * @param platformId
	 */
	public static void sendSecurityRequestAndVerifyResponse(SecurityRequest securityRequest, String jsonMessage, String completeUrl, String platformId) {
		ResponseEntity<?> serviceResponse = null;
        try {
        	serviceResponse = SecuredRequestSender.sendSecuredRequest(securityRequest, jsonMessage, completeUrl);
        } catch (Exception e) {
            logger.warn("Exception thrown during sending security request!", e);
        }

        logger.debug("ServiceResponse = " + serviceResponse);

        //verify serviceResponse
        try {
            boolean verifiedResponse = securityManager.verifyReceivedResponse(
                    serviceResponse.getHeaders().get(SecurityConstants.SECURITY_RESPONSE_HEADER).get(0),
                    "subscriptionManager", platformId);
            if (verifiedResponse)
                logger.debug("Sending of security request message to platform " + platformId + " successfull!");
            else
                logger.warn("Failed to send security request message to platform " + platformId + " due to the response verification error!");
        } catch (Exception e) {
            logger.warn("Exception thrown during verifying service response", e);
        }
	}

	protected static String serializeFederatedResource(FederatedResource federatedResource) {
		String string;

		try {
			string = mapper.writeValueAsString(federatedResource);
		} catch (JsonProcessingException e) {
			logger.info("Problem in serializing the federatedResource", e);
			return null;
		}
		return string;
	}

	protected static FederatedResource deserializeFederatedResource(String s) {

		FederatedResource federatedResource;

		try {
			federatedResource = mapper.readValue(s, FederatedResource.class);
		} catch (IOException e) {
			logger.info("Problem in deserializing the federatedResource");
			return null;
		}
		return federatedResource;
	}
	
	/**
	 * Method checks if each federationMember is already in some federation with this platform.
	 * if yes, it is assumed that subscription object for that platform already exists, and 
	 * that the other platform is already informed about this platform subscription.
	 * If not, initial subscription is created for federationMember and stored to DB 
	 * (although it is expected to receive HTTP POST from that platform and overwrite it).
	 * numberOfCommonFederations is used to track the number of common federations that this platform
	 * has with other platforms
	 * @param federation
	 */
	protected void processFederationCreated(Federation federation){
		
		//check if received federation contains this platform
		if(federation.getMembers().stream().map(FederationMember::getPlatformId).collect(Collectors.toList()).contains(platformId)){
			for(FederationMember fedMember : federation.getMembers()){
				if(fedMember.getPlatformId().equals(platformId))continue; //skip procedure for this platform
				//map keeps number of common federations of this platform with others
				addressBook.put(fedMember.getPlatformId(), fedMember.getInterworkingServiceURL());
				processFedMemberAdding(fedMember.getPlatformId(), federation.getId(), true);
			}
		}
	}
	
	/**
	 * Method updates numberOfCommonFederations according to federation updates.
	 * If federation members are added to federation, own subscription is sent to new members
	 * and their initial subscription is created in subscriptionRepo.
	 * If federation members are removed from federation, map is updated, and
	 * if there are no more common federations of this platform and deleted member,
	 * its subscription is removed from subsriptionRepo
	 * @param federation
	 */
	protected void processFederationUpdated(Federation federation, List<String> oldMembers){
			
		List<String> newMembers = federation.getMembers().stream().map(FederationMember::getPlatformId).collect(Collectors.toList());
		
		//if this platform is added to existing federation process it as new federation is created
		if(!oldMembers.contains(platformId) && newMembers.contains(platformId))
			processFederationCreated(federation);
		
		//if this platform is removed from federation...
		else if(oldMembers.contains(platformId) && !newMembers.contains(platformId)){
			for(String oldFedMembersId : oldMembers){
				if(oldFedMembersId.equals(platformId))continue;
				else processFedMemberRemoval(oldFedMembersId);
			}
			unshareFedResFromDeletedFederation(federation.getId());
		}
		
		//if this platform was, and still is in updated federation...
		else if(oldMembers.contains(platformId) && newMembers.contains(platformId)){
			for(String newFedMembersId : newMembers){
				if(newFedMembersId.equals(platformId))continue;
				//if new federation member is added in this updated federation...
				else if(!oldMembers.contains(newFedMembersId)){
					addressBook.put(newFedMembersId, federation.getMembers().stream().filter(x -> newFedMembersId.equals(x.getPlatformId())).findAny().get().getInterworkingServiceURL());
					processFedMemberAdding(newFedMembersId, federation.getId(), false);
				}
			}			
			for(String oldFedMembersId : oldMembers){
				if(oldFedMembersId.equals(platformId))continue;
				//if federation member is removed in updated federation
				else if(!newMembers.contains(oldFedMembersId)){
					processFedMemberRemoval(oldFedMembersId);
					unshareFedResOnFedMemberRemoval(oldFedMembersId, federation.getId());
				}
			}	
		}
	}
	
	/**
	 * Updating numberOfCommonFederations map according to federationMemebers of deleted federation,
	 * and if there are no more common federations of this platform and deleted member,
	 * its subscription is removed from subsriptionRepo
	 * @param federationId
	 */
	protected void processFederationDeleted(String federationId){
		
		List<String> deletedMembers = fedRepo.findOne(federationId).getMembers().stream().map(FederationMember::getPlatformId).collect(Collectors.toList());
		fedRepo.delete(federationId);
        logger.info("Federation with id: " + federationId + " removed from repository.");
		if(deletedMembers.contains(platformId)){
			for(String deletedMemberId : deletedMembers){
				if(deletedMemberId.equals(platformId))continue;
				else processFedMemberRemoval(deletedMemberId);
			}
			unshareFedResFromDeletedFederation(federationId);
		}
	}
	
	/**
	 * Method iterates all federated resources and unshare each one from a given fedId
	 * It is then deleted if unshared from all federations, or overwritten in mongoDB without
	 * removed federation
	 * @param federationId
	 */
	protected void unshareFedResFromDeletedFederation(String federationId) {
		Set<String> platformRegistryNotification = new HashSet<>();
		//iterate all federatedResources and unshare each one that has been in deleted federation from it
		List<FederatedResource> allFedRes = fedResRepo.findAll(); // fetch all current fedRes
		for (FederatedResource fr : allFedRes) {
			if(fr.getFederatedResourceInfoMap().containsKey(federationId)) {
				fr.unshareFromFederation(federationId);
				platformRegistryNotification.add(fr.getAggregationId()+"@"+federationId);
				if(fr.getFederatedResourceInfoMap().size()>0)fedResRepo.save(fr); //overwrite if it is shared in another federations
				else fedResRepo.delete(fr.getAggregationId());	//delete if unshared from all federations
			}
		}
		//send notification about deleted symbioteIds
		rabbitManager.sendAsyncMessageJSON(PRexchange, PRremovedFedResRK, new ResourcesDeletedMessage(platformRegistryNotification));
	}
	
	/**
	 * Method iterates all federated resources and finds those shared by given platformID.
	 * They are then unshared from a given fedId and deleted from mongoDB
	 * if unshared from all federations, or overwritten in mongoDB without removed federation.
	 * @param federationId
	 */
	protected void unshareFedResOnFedMemberRemoval(String removedPlatformId, String federationId) {
		Set<String> platformRegistryNotification = new HashSet<>();
		//iterate all federatedResources and unshare each one that has been in deleted federation from it
		List<FederatedResource> allFedRes = fedResRepo.findAll(); // fetch all current fedRes
		for (FederatedResource fr : allFedRes) {
			if(fr.getPlatformId().equals(removedPlatformId)) { //if current fedRes is shared by platform removed from federation
				if(fr.getFederatedResourceInfoMap().containsKey(federationId))
					fr.unshareFromFederation(federationId);
				if(fr.getFederatedResourceInfoMap().size()>0)fedResRepo.save(fr); //overwrite if it is shared in another federations
				else fedResRepo.delete(fr.getAggregationId());	//delete if unshared from all federations
				platformRegistryNotification.add(fr.getAggregationId()+"@"+federationId);
			}
		}
		//send notification about deleted symbioteIds
		rabbitManager.sendAsyncMessageJSON(PRexchange, PRremovedFedResRK, new ResourcesDeletedMessage(platformRegistryNotification));
	}
	
	/**
	 * Method processes removal of federationMember(Id) from a single common federation that it had with this platform.
	 * @param oldFedMembersId
	 */
	protected void processFedMemberRemoval(String oldFedMembersId){
		if(numberOfCommonFederations.get(oldFedMembersId)>1) numberOfCommonFederations.put(oldFedMembersId, numberOfCommonFederations.get(oldFedMembersId) - 1);
		else {
			numberOfCommonFederations.remove(oldFedMembersId);
			subscriptionRepo.delete(oldFedMembersId);
			addressBook.remove(oldFedMembersId);
		}
	}
	
	/**
	 * Method processes adding of FederationMember(Id) to a common federation with this platform.
	 * @param newFedMembersId
	 */
	protected void processFedMemberAdding(String newFedMemberId, String federationId, boolean created){
		if(numberOfCommonFederations.containsKey(newFedMemberId)) {
			numberOfCommonFederations.put(newFedMemberId, numberOfCommonFederations.get(newFedMemberId) + 1);
			if(!created)
				processSendingExistingFederatedResources(newFedMemberId, RestInterface.findCommonFederations(newFedMemberId), platformId);
		}
		else {
			numberOfCommonFederations.put(newFedMemberId, 1);
			
			// Wrap in try/catch to avoid requeuing
	        try {
			//send HTTP-POST of own subscription
			SecurityRequest securityRequest = securityManager.generateSecurityRequest();
            if (securityRequest != null) {
                logger.debug("Security Request created successfully!");

                // if the creation of securityRequest is successful send it to the federated platform  
				sendSecurityRequestAndVerifyResponse(securityRequest,
						mapper.writeValueAsString(subscriptionRepo.findOne(platformId)),
						addressBook.get(newFedMemberId).replaceAll("/+$", "") + "/subscriptionManager" + "/subscription",
						newFedMemberId);       
            } else
                logger.info(
                        "Failed to send own subscription message due to securityRequest creation failure!");
	        } catch (Exception e) {
	            logger.warn("Exception thrown during processing federationMember addition.", e);
	        }
        }
	}
	
	/**
	 * If there are already shared resources in a given federation set that fit the newly added members subscription,
	 * they are sent to it.
	 * 
	 * @param newFedMemberId
	 * @param federationId
	 */
	public static void processSendingExistingFederatedResources(String newFedMemberId, List<String> federationIds, String homePlatformId) {
		
		//check if there are shared resources in received federations that fit the subscription of added member
		List<FederatedResource> forSending = findExistingSharedResourcesInFederations(newFedMemberId, federationIds, homePlatformId);
		
		if(forSending.size() > 0) {
		//send found federatedResource to newFedMemberId
			try {
				SecurityRequest securityRequest = securityManager.generateSecurityRequest();
	            if (securityRequest != null) {
	                logger.debug("Security Request created successfully!");

	                // if the creation of securityRequest is successful send it to the federated platform  
	                logger.debug("Sending federated resources: " +
                            forSending.stream().map(FederatedResource::getAggregationId).collect(Collectors.toList()) + " to platform: " + newFedMemberId);

					sendSecurityRequestAndVerifyResponse(securityRequest,
							mapper.writeValueAsString(new ResourcesAddedOrUpdatedMessage(forSending)),
							addressBook.get(newFedMemberId).replaceAll("/+$", "") + "/subscriptionManager" + "/addOrUpdate",
							newFedMemberId); 
	            } else
	                logger.info(
	                        "Failed to send ResourcesAddedOrUpdated message due to securityRequest creation failure!");
			} catch (Exception e) {
	            logger.warn("Exception thrown during sending existing fedRes to added platform in existing federation.", e);
	        }
		}
	}
	
	/**
	 * Method finds all federated resources shared by this platform. It checks if they are shared in federations common to the given 
	 * memberId and this platform, and if yes, adds them in a list without the info about federations that are not common to them.
	 * 
	 * @param newFedMemberId
	 * @param federationId
	 * @return
	 */
	public static List<FederatedResource> findExistingSharedResourcesInFederations(String newFedMemberId, List<String> federationIds, String homePlatformId){
		List<FederatedResource> allFedRes = fedResRepo.findAll();
		List<FederatedResource> forSending = new ArrayList<>();
		for(FederatedResource fedRes : allFedRes) {
			//if this platform shared current fedRes...
			if(fedRes.getPlatformId().equals(homePlatformId)){
				//find all given common federations in fedRes
				List<String> toStay = new ArrayList<>();
				for(String fedId : federationIds) {
					if(fedRes.getFederatedResourceInfoMap().containsKey(fedId))toStay.add(fedId);
				}
				if(toStay.size()>0) {
					Subscription addedMemberSubscription = subscriptionRepo.findOne(newFedMemberId);
					if(addedMemberSubscription == null) {
						logger.debug("Added member subscription does not exist! Federated resources will be sent when subscription object is received!");
						break;
					}
					else {
						if(isSubscribed(addedMemberSubscription, fedRes)) {
							FederatedResource clonedFr = deserializeFederatedResource(serializeFederatedResource(fedRes));
	                        clonedFr.clearPrivateInfo();
	                        for(String federationId : toStay) {
		                        clonedFr.shareToNewFederation(federationId, fedRes.getCloudResource()
		                                .getFederationInfo().getSharingInformation().get(federationId).getBartering());
	                        }
	                        forSending.add(clonedFr);
						}
					}
				}
			}
		}
		return forSending;
	}
	
	/**
	 * Method checks if platform with the given id is subscribed to
	 * the specified federatedResource, according to its subscription.
	 *  
	 * @param platformId
	 * @param fedRes
	 * @return
	 */
	public static boolean isSubscribed (Subscription platformSubscription, FederatedResource fedRes) {
		
		//if resource is not defined, federated resource is not valid for subscription matching
		if(fedRes.getCloudResource().getResource() == null)
			return false;
		
		//resourceType matching condition
		if(!resourceTypeMatching(platformSubscription.getResourceType(),fedRes.getCloudResource().getResource()))
			return false;
		
		//if subscription has location condition check it
		if(platformSubscription.getLocations() != null && platformSubscription.getLocations().size() > 0) {
			//location matching condition
			if(!locationMatching(platformSubscription.getLocations(),fedRes.getCloudResource().getResource()))
				return false;
		}
		
		//if subscription has observedProperty condition check it
		if(platformSubscription.getObservedProperties() != null && platformSubscription.getObservedProperties().size() > 0) {
			//observedProperties matching condition
			if(!observedPropertyMatching(platformSubscription.getObservedProperties(), fedRes.getCloudResource().getResource()))
				return false;
		}
		
		//if subscription has capability condition check it
		if(platformSubscription.getCapabilities() != null && platformSubscription.getCapabilities().size() > 0) {
			//capabilities matching condition
			if(!capabilityMatching(platformSubscription.getCapabilities(), fedRes.getCloudResource().getResource()))
				return false;
		}		
		
		return true;
	}
	
	/**
	 * Method does resourceType matching of given Resource and subscription resourceType map.
	 * 
	 * @param resourceType
	 * @param resource
	 * @return
	 */
	public static boolean resourceTypeMatching(Map<String, Boolean> resourceType, Resource resource) {
		
		if(resourceType.get("service") && resource instanceof Service)
			return true;

		if (resourceType.get("device") && resource instanceof Device)
			return true;
		
		if (resourceType.get("sensor") && resource instanceof Sensor)
			return true;
		
		if (resourceType.get("actuator") && resource instanceof Actuator)
			return true;
		
		return false;
	}
	
	/**
	 * Method does location matching of given resource and subscription list of locations.
	 * 
	 * @param locations
	 * @param resource
	 * @return
	 */
	public static boolean locationMatching(List<String> locations, Resource resource) {
		if(resource instanceof Device) {
			if(((Device) resource).getLocatedAt() != null && locations.contains(((Device) resource).getLocatedAt().getName()))
				return true;
		}
		return false;
	}
	
	/**
	 * Method does observedProperty matching of given resource and subscription list of observedProperties.
	 * 
	 * @param observedProperties
	 * @param resource
	 * @return
	 */
	public static boolean observedPropertyMatching(List<String> observedProperties, Resource resource) {
		if(resource instanceof Sensor || resource instanceof StationarySensor || resource instanceof MobileSensor) { // check that resource is sensor
			if(((Sensor)resource).getObservesProperty() != null && ((Sensor)resource).getObservesProperty().size() > 0)	{//check that sensor has any observed properties
				for(String property : observedProperties) {
					//if any of subscribed observedProperties is available, resource fits the subscription
					if(((Sensor)resource).getObservesProperty().contains(property))
						return true;
				}
			}
		}	
		return false;
	}
	
	/**
	 * Method does capability matching of given resource and subscription list of capabilities.
	 * 
	 * @param capabilities
	 * @param resource
	 * @return
	 */
	public static boolean capabilityMatching(List<String> capabilities, Resource resource) {
		if(resource instanceof Actuator) { // check that resource is actuator
			if(((Actuator)resource).getCapabilities() != null && ((Actuator)resource).getCapabilities().size() > 0)	{ //check that actuator has any capabilities
				for(String capability : capabilities) {
					//if any of subscribed capabilities is available, resource fits the subscription
					if(((Actuator)resource).getCapabilities().stream().map(Capability :: getName).collect(Collectors.toList()).contains(capability))
						return true;
				}
			}
		}	
		return false;
	}
	
}