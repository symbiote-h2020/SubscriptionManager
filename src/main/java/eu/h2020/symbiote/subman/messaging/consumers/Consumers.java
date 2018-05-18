package eu.h2020.symbiote.subman.messaging.consumers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.security.commons.SecurityConstants;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;
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

	@Autowired
	private FederationRepository fedRepo;

	@Autowired
	private FederatedResourceRepository fedResRepo;
	
	@Autowired
	private SubscriptionRepository subscriptionRepo;

	@Autowired
	private SecurityManager securityManager;

	private MessageConverter messageConverter;

	private ObjectMapper mapper = new ObjectMapper();
	
	private Map<String, Integer> numberOfCommonFederations;

	@Autowired
	public Consumers() {
		messageConverter = new Jackson2JsonMessageConverter();
		numberOfCommonFederations = new HashMap<>();
	}

	/**
	 * Method receives created federations from FM component, and stores them to
	 * local MongoDB.
	 * 
	 * @param msg
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.created}"))
	public void federationCreated(Message msg) {

	    try {
            Federation federation = (Federation) messageConverter.fromMessage(msg);
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
	public void federationChanged(Message msg) throws IOException {

	    try {
            Federation federation = (Federation) messageConverter.fromMessage(msg);
            fedRepo.save(federation);
            logger.info("Federation with id: " + federation.getId() + " updated.");
            
            processFederationUpdated(federation);
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
            fedRepo.delete(federationId);
            logger.info("Federation with id: " + federationId + " removed from repository.");
            
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

        // Wrap in try/catch to avoid requeuing
        try {
            // Map<PlatformId, Map<FederatedResourceId, FederatedResource>>
            Map<String, Map<String, FederatedResource>> platformMessages = new HashMap<>();

            // Map<PlatformId, interworkingServiceUrl>
            Map<String, String> urls = new HashMap<>();

            // convert received RMQ message to ResourcesAddedOrUpdatedMessage object
            ResourcesAddedOrUpdatedMessage rsMsg = (ResourcesAddedOrUpdatedMessage) messageConverter.fromMessage(msg);
            logger.info("Received ResourcesAddedOrUpdatedMessage from Platform Registry");

            // add received FederatedResource to local MongoDB
            for (FederatedResource fr : rsMsg.getNewFederatedResources()) {
                fedResRepo.save(fr);
                logger.info("Federated resource with id " + fr.getSymbioteId() + " added to repository and is exposed to " +
                    fr.getFederations());

                // iterate interested federations
                for (String interestedFederationId : fr.getFederations()) {

                    Federation interestedFederation = fedRepo.findOne(interestedFederationId);

                    if (interestedFederation == null) {
                        logger.info("The federation with id " + interestedFederationId + " was not found in the federation repository");
                        continue;
                    }

                    // add all federationMembers to platformsToNotify set
                    for (FederationMember fm : interestedFederation.getMembers()) {

                    	//to avoid platform sending HTTP request to itself
                    	if(fm.getPlatformId().equals(this.platformId)) continue;
                    	
                    	/**
                    	 * CHECK IF CURRENT FEDERATION MEMEBER IS SUBSCRIBED TO CURRENT FEDERATED RESOURCE
                    	 */
                    	
                        // if platform is not yet in a list for receiving
                        // notification, add it
                        if (!platformMessages.containsKey(fm.getPlatformId())) {
                            platformMessages.put(fm.getPlatformId(), new HashMap<>());
                            urls.put(fm.getPlatformId(), fm.getInterworkingServiceURL());
                        }

                        Map<String, FederatedResource> platformMap = platformMessages.get(fm.getPlatformId());

                        // if there is not entry in the platformMap for this
                        // federatedResource create it
                        if (!platformMap.containsKey(fr.getSymbioteId())) {

                            FederatedResource clonedFr = deserializeFederatedResource(serializeFederatedResource(fr));
                            clonedFr.clearPrivateInfo();
                            platformMap.put(clonedFr.getSymbioteId(), clonedFr);
                        }

                        // add the federation info of currently iterated federation
                        FederatedResource platformFederatedResource = platformMap.get(fr.getSymbioteId());
                        platformFederatedResource.shareToNewFederation(interestedFederationId, fr.getCloudResource()
                                .getFederationInfo().getSharingInformation().get(interestedFederationId).getBartering());
                    }
                }
            }

            // send HTTP-POST notifications to federated platforms
            // create securityRequest
            SecurityRequest securityRequest = securityManager.generateSecurityRequest();
            if (securityRequest != null) {
                logger.debug("Security Request created successfully!");

                // if the creation of securityRequest is successful broadcast
                // FederatedResource to interested platforms
                for (Map.Entry<String, Map<String, FederatedResource>> entry : platformMessages.entrySet()) {

                    List<FederatedResource> resourcesForSending = new ArrayList<>(
                            entry.getValue().values());

                    logger.debug("Sending  addedOrUpdatedFederatedResource message to platform " + entry.getKey()
                            + " for " +
                            resourcesForSending.stream()
                                    .map(FederatedResource::getSymbioteId).collect(Collectors.toList()));

                    ResponseEntity<?> serviceResponse = null;
                    try {
                        serviceResponse = SecuredRequestSender.sendSecuredResourcesAddedOrUpdated(
                                securityRequest, new ResourcesAddedOrUpdatedMessage(resourcesForSending),
                                urls.get(entry.getKey()));
                    } catch (Exception e) {
                        logger.warn("Exception thrown during sending addedOrUpdatedFederatedResource", e);
                    }

                    logger.debug("ServiceResponse = " + serviceResponse);

                    try {
                        // verify serviceResponse
                        boolean verifiedResponse = securityManager.verifyReceivedResponse(
                                serviceResponse.getHeaders().get(SecurityConstants.SECURITY_RESPONSE_HEADER).get(0),
                                "subscriptionManager", entry.getKey());
                        if (verifiedResponse)
                            logger.info("Sending of addedOrUpdatedFederatedResource message to platform " + entry.getKey()
                                    + " successfull!");
                        else
                            logger.info("Failed to send addedOrUpdatedFederatedResource message to platform " + entry.getKey()
                                    + " due to the response verification error!");
                    } catch (Exception e) {
                    logger.warn("Exception thrown during verifying service response", e);
                    }
                }
            } else
                logger.info(
                        "Failed to broadcast addedOrUpdatedFederatedResource message due to the securityRequest creation failure!");
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

	    // Wrap in try/catch to avoid requeuing
	    try {
            ResourcesDeletedMessage rdDel = (ResourcesDeletedMessage) messageConverter.fromMessage(msg);
            logger.info("Received ResourcesDeletedMessage from Platform Registry");

            // <platformId,<federatedResourceId, Set<federationsId>>>
            Map<String, Map<String, Set<String>>> platformMessages = new HashMap<String, Map<String, Set<String>>>();
            // <platformId,interworkingServiceUrl
            Map<String, String> urls = new HashMap<String, String>();

            logger.debug("Federated resources to be unshared = " + rdDel.getDeletedFederatedResourcesMap().keySet());

            for (Map.Entry<String, Set<String>> entry : rdDel.getDeletedFederatedResourcesMap().entrySet()) {
                FederatedResource toUpdate = fedResRepo.findOne(entry.getKey());
                if (toUpdate == null) {
                    logger.info("The federatedResource " + entry.getKey() + " was not found in the federatedResource repository");
                    continue;
                }

                for (String fedId : entry.getValue()) {
                    // remove federationIds in which resource is unshared
                    toUpdate.getFederations().remove(fedId);
                    toUpdate.getCloudResource().getFederationInfo().getSharingInformation().remove(fedId);
                }

                // if federatedResource is unshared from all federations remove it
                if (toUpdate.getCloudResource().getFederationInfo().getSharingInformation().isEmpty())
                    fedResRepo.delete(entry.getKey());

                // iterate federations for current FederatedResource
                for (String federationId : entry.getValue()) {
                    Federation currentFederation = fedRepo.findOne(federationId);

                    if (currentFederation == null) {
                        logger.info("The federation with id " + federationId + " was not found in the federation repository");
                        continue;
                    }

                    // iterate members
                    for (FederationMember fedMember : currentFederation.getMembers()) {
                    	
                    	//to avoid platform sending HTTP request to itself
                    	if(fedMember.getPlatformId().equals(this.platformId)) continue;
                    	
                    	/**
                    	 * CHECK IF CURRENT FEDERATION MEMBER IS SUBSCRIBED TO CURRENT FEDERATED RESOURCE
                    	 */
                    	
                        if (!platformMessages.containsKey(fedMember.getPlatformId())) {
                            platformMessages.put(fedMember.getPlatformId(), new HashMap<>());
                            urls.put(fedMember.getPlatformId(), fedMember.getInterworkingServiceURL());
                        }
                        Map<String, Set<String>> currentPlatformMessageMap = platformMessages
                                .get(fedMember.getPlatformId());
                        if (!currentPlatformMessageMap.containsKey(entry.getKey()))
                            currentPlatformMessageMap.put(entry.getKey(), new HashSet<>());
                        currentPlatformMessageMap.get(entry.getKey()).add(federationId);
                    }
                }
            }

            // sending created map to interested federated platforms
            SecurityRequest securityRequest = securityManager.generateSecurityRequest();
            if (securityRequest != null) {
                logger.debug("Security Request created successfully!");

                // if the creation of securityRequest is successful broadcast changes to interested platforms
                for (Map.Entry<String, Map<String, Set<String>>> entry : platformMessages.entrySet()) {

                    Map<String, Set<String>> deleteMessage = entry.getValue();

                    logger.debug("Sending unsharedFederatedResource message to platform " + entry.getKey()
                            + " for " + deleteMessage);

                    ResponseEntity<?> serviceResponse = null;
                    try {
                         serviceResponse = SecuredRequestSender.sendSecuredResourcesDeleted(securityRequest,
                                new ResourcesDeletedMessage(deleteMessage), urls.get(entry.getKey()));
                    } catch (Exception e) {
                        logger.warn("Exception thrown during sending unsharedFederatedResource", e);
                    }

                    logger.debug("ServiceResponse = " + serviceResponse);

                    //verify serviceResponse
                    try {
                        boolean verifiedResponse = securityManager.verifyReceivedResponse(
                                serviceResponse.getHeaders().get(SecurityConstants.SECURITY_RESPONSE_HEADER).get(0),
                                "subscriptionManager", entry.getKey());
                        if (verifiedResponse)
                            logger.debug("Sending of unsharedFederatedResource message to platform " + entry.getKey()
                                    + " successfull!");
                        else
                            logger.warn("Failed to send unsharedFederatedResource message to platform " + entry.getKey()
                                    + " due to the response verification error!");
                    } catch (Exception e) {
                        logger.warn("Exception thrown during verifying service response", e);
                    }
                }
            } else
                logger.info(
                        "Failed to broadcast addedOrUpdatedFederatedResource message due to the securityRequest creation failure!");
        } catch (Exception e) {
            logger.warn("Exception thrown during removeFederatedResource", e);
        }
	}

	protected String serializeFederatedResource(FederatedResource federatedResource) {
		String string;

		try {
			string = mapper.writeValueAsString(federatedResource);
		} catch (JsonProcessingException e) {
			logger.info("Problem in serializing the federatedResource", e);
			return null;
		}
		return string;
	}

	protected FederatedResource deserializeFederatedResource(String s) {

		FederatedResource federatedResource;

		try {
			federatedResource = mapper.readValue(s, FederatedResource.class);
		} catch (IOException e) {
			logger.info("Problem in deserializing the federatedResource", e);
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
		
		//map keeps number of common federations of this platform with others
		for(FederationMember fedMember : federation.getMembers()){
			if(numberOfCommonFederations.containsKey(fedMember.getPlatformId()))
				numberOfCommonFederations.put(fedMember.getPlatformId(), numberOfCommonFederations.get(fedMember.getPlatformId()) + 1);
			else {
				numberOfCommonFederations.put(fedMember.getPlatformId(), 1);
				Subscription subscription = new Subscription();
				subscription.setPlatformId(fedMember.getPlatformId());
				subscriptionRepo.save(subscription);
				//TODO SEND HTT-POST OF OWN SUBSCRIPTION
			}		
		}
	}
	
	//TODO test
	/**
	 * Method updates numberOfCommonFederations according to federation updates.
	 * If federation members are added to federation, own subscription is sent to new members
	 * and their initial subscription is created in subscriptionRepo.
	 * If federation members are removed from federation, map is updated, and
	 * if there are no more common federations of this platform and deleted member,
	 * its subscription is removed from subsriptionRepo
	 * @param federation
	 */
	protected void processFederationUpdated(Federation federation){
			
		List<String> oldMembers = fedRepo.findOne(federation.getId()).getMembers().stream().map(FederationMember::getPlatformId).collect(Collectors.toList());
		List<String> newMembers = federation.getMembers().stream().map(FederationMember::getPlatformId).collect(Collectors.toList());
		//if federationsMembers are changed, update commonFederations map
		for (String newFedMembersId : newMembers){
			//if new federation member is added in this updated federation...
			if(!oldMembers.contains(newFedMembersId)){
				if(numberOfCommonFederations.containsKey(newFedMembersId))
					numberOfCommonFederations.put(newFedMembersId, numberOfCommonFederations.get(newFedMembersId) + 1);
				else {
					numberOfCommonFederations.put(newFedMembersId, 1);
					Subscription subscription = new Subscription();
					subscription.setPlatformId(newFedMembersId);
					subscriptionRepo.save(subscription);
					//TODO SEND HTT-POST OF OWN SUBSCRIPTION
				}
			}
		}
		
		for(String oldFedMembersId : oldMembers){
			if(!newMembers.contains(oldFedMembersId)){
				//if federation members is removed in updated federation
				if(numberOfCommonFederations.get(oldFedMembersId)>1) numberOfCommonFederations.put(oldFedMembersId, numberOfCommonFederations.get(oldFedMembersId) - 1);
				else {
					numberOfCommonFederations.remove(oldFedMembersId);
					subscriptionRepo.delete(oldFedMembersId);
				}
			}
		}	
	}
	
	//TODO test
	/**
	 * Updating numberOfCommonFederations map according to federationMemebers of deleted federation,
	 * and if there are no more common federations of this platform and deleted member,
	 * its subscription is removed from subsriptionRepo
	 * @param federationId
	 */
	protected void processFederationDeleted(String federationId){
		List<String> deletedMembers = fedRepo.findOne(federationId).getMembers().stream().map(FederationMember::getPlatformId).collect(Collectors.toList());
		for(String deletedMemberId : deletedMembers){
			if(numberOfCommonFederations.get(deletedMemberId)>1) numberOfCommonFederations.put(deletedMemberId, numberOfCommonFederations.get(deletedMemberId) - 1);
			else {
				numberOfCommonFederations.remove(deletedMemberId);
				subscriptionRepo.delete(deletedMemberId);
			}
		}
	}
	
}
