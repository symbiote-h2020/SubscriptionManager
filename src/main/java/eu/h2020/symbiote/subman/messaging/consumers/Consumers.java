package eu.h2020.symbiote.subman.messaging.consumers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;
import eu.h2020.symbiote.subman.controller.SecuredRequestSender;
import eu.h2020.symbiote.subman.controller.SecurityManager;
import eu.h2020.symbiote.subman.repositories.FederatedResourceRepository;
import eu.h2020.symbiote.subman.repositories.FederationRepository;

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
	private SecurityManager securityManager;
	
	private MessageConverter messageConverter;
	
	private ObjectMapper mapper = new ObjectMapper();
	
	@Autowired
	public Consumers() {
		messageConverter = new Jackson2JsonMessageConverter();
	}

	/**
	 * Method receives created federations from FM component, and stores them to local MongoDB.
	 * @param msg
	 * @throws IOException
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.created}"))
	public void federationCreated(Message msg) throws IOException {

		Federation federation = (Federation) messageConverter.fromMessage(msg);
		fedRepo.save(federation);
		logger.info("Federation with id: " + federation.getId() + " added to repository.");
	}

	/**
	 * Method receives updated federations from FM component, and stores changes to local MongoDB.
	 * @param msg
	 * @throws IOException
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.changed}"))
	public void federationChanged(Message msg) throws IOException {

		Federation federation = (Federation) messageConverter.fromMessage(msg);
		fedRepo.save(federation);
		logger.info("Federation with id: " + federation.getId() + " updated.");
	}

	/**
	 * Method receives id of removed federation from FM component, and deletes it from MongoDB.
	 * @param federationId
	 * @throws IOException
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.deleted}"))
	public void federationDeleted(String federationId) throws IOException {

		fedRepo.delete(federationId);
		logger.info("Federation with id: " + federationId + " removed from repository.");
	}

	/**
	 * Method receives ResourcesAddedOrUpdated message from PlatformRegistry component.
	 * It saves locally received federated resources to MongoDB, and forwards them to other interested federated platforms.
	 * @param msg
	 * @throws IOException
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "${rabbit.queueName.subscriptionManager.addOrUpdateFederatedResources}"), exchange = @Exchange(value = "${rabbit.exchange.subscriptionManager.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.addOrUpdateFederatedResources}"))
	public void addedOrUpdateFederatedResource(Message msg) throws IOException {

		// Map<PlatformId, Map<FederatedResourceId, FederatedResource>>
        Map<String, Map<String, FederatedResource>> platformMessages = new HashMap<>();
        
        //Map<PlatformId, interworkingServiceUrl>
        Map<String, String> urls = new HashMap<String, String>();
        
		//convert received RMQ message to ResourcesAddedOrUpdatedMessage object
		ResourcesAddedOrUpdatedMessage rsMsg = (ResourcesAddedOrUpdatedMessage) messageConverter.fromMessage(msg);	
		logger.info("Received ResourcesAddedOrUpdatedMessage from Platform Registry");

		//add received FederatedResource to local MongoDB
		for(FederatedResource fr : rsMsg.getNewFederatedResources()){
			fedResRepo.save(fr);
			logger.info("Federated resource with id " + fr.getSymbioteId()
			+ " added to repository.");
			
			//iterate interested federations
			for (String interestedFederationId : fr.getFederations()) {

                Federation interestedFederation = fedRepo.findOne(interestedFederationId);
                
                //add all federationMembers to platformsToNotify set
                for (FederationMember fm : interestedFederation.getMembers()) {
                	
                	//if platform is not yet in a list for receiving notification, add it
                	if (!platformMessages.containsKey(fm.getPlatformId())){
                        platformMessages.put(fm.getPlatformId(), new HashMap<>());
                        urls.put(fm.getPlatformId(), fm.getInterworkingServiceURL());
                	}
                	
                	Map<String, FederatedResource> platformMap = platformMessages.get(fm.getPlatformId());
                	
                	//if there is not entry in the platformMap for this federatedResource create it
                	if (!platformMap.containsKey(fr.getSymbioteId())) {
                		
                		FederatedResource clonedFr = deserializeFederatedResource(serializeFederatedResource(fr));
                		//TODO check if federationsSet is emptied
                		clonedFr.clearPrivateInfo();
                		platformMap.put(clonedFr.getSymbioteId(), clonedFr);
                	}
                	
                	//add the federation info of currently iterated federation
                	FederatedResource platformFederatedResource = platformMap.get(fr.getSymbioteId());
                	platformFederatedResource.shareToNewFederation(interestedFederationId,
                            fr.getCloudResource().getFederationInfo().getSharingInformation().get(interestedFederationId).getBartering());
                }
			}
		}
				
			//send HTTP-POST notifications to federated platforms
			//create securityRequest
			SecurityRequest securityRequest = securityManager.generateSecurityRequest();
			if(securityRequest != null){
				//if the creation of securityRequest is successful broadcast FedreatedResource to interested platforms 
				for(Map.Entry<String, Map<String, FederatedResource>> entry : platformMessages.entrySet()){
					
					List<FederatedResource> resourcesForSending = new ArrayList<FederatedResource>(entry.getValue().values());
					ResponseEntity<?> serviceResponse = SecuredRequestSender.sendSecuredResourcesAddedOrUpdated(securityRequest, new ResourcesAddedOrUpdatedMessage(resourcesForSending), urls.get(entry.getKey()));
						
					boolean verifiedResponse = securityManager.verifyReceivedResponse(serviceResponse.getBody().toString(), "subscriptionManager", entry.getKey());
					if (verifiedResponse) logger.info("Sending of addedOrUpdatedFederatedResource message to platform "+entry.getKey()+" successfull!");
					else logger.info("Failed to send addedOrUpdatedFederatedResource message to platform "+entry.getKey()+" due to the response verification error!");
				}
			}
			else logger.info("Failed to broadcast addedOrUpdatedFederatedResource message due to the securityRequest creation failure!");
	}

	/**
	 * Method receives ResourcesDeletedMessage message from PlatformRegistry component.
	 * It saves locally received changes to MongoDB, and forwards info to other interested federated platforms.
	 * @param msg
	 * @throws IOException
	 */
	@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "${rabbit.queueName.subscriptionManager.removeFederatedResources}"), exchange = @Exchange(value = "${rabbit.exchange.subscriptionManager.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.removeFederatedResources}"))
	public void removeFederatedResource(Message msg) throws IOException {

		ResourcesDeletedMessage rdDel = (ResourcesDeletedMessage) messageConverter.fromMessage(msg);
		logger.info("Received ResourcesDeletedMessage from Platform Registry");		
	}
	
	
	
	private String serializeFederatedResource(FederatedResource federatedResource) {
        String string;

        try {
            string = mapper.writeValueAsString(federatedResource);
        } catch (JsonProcessingException e) {
            logger.info("Problem in serializing the federatedResource", e);
            return null;
        }
        return string;
    }

    private FederatedResource deserializeFederatedResource(String s) {

        FederatedResource federatedResource;

        try {
            federatedResource = mapper.readValue(s, FederatedResource.class);
        } catch (IOException e) {
            logger.info("Problem in deserializing the federatedResource", e);
            return null;
        }
        return federatedResource;
    }
}
