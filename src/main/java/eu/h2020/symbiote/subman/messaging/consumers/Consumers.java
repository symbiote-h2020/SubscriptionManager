package eu.h2020.symbiote.subman.messaging.consumers;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
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

		//convert received RMQ message to ResourcesAddedOrUpdatedMessage object
		ResourcesAddedOrUpdatedMessage rsMsg = (ResourcesAddedOrUpdatedMessage) messageConverter.fromMessage(msg);	
		logger.info("Received ResourcesAddedOrUpdatedMessage from Platform Registry");

		//add received FederatedResource to local MongoDB
		for(FederatedResource fr : rsMsg.getNewFederatedResources()){
			fedResRepo.save(fr);
			logger.info("Federated resource with id " + fr.getSymbioteId()
			+ " added to repository.");
			
			//use one set for FederationMembers that have to be notified, to escape double notifications since the same platform could be in more then one federation
			Set<FederationMember> platformsToNotify = new HashSet<FederationMember>();
			
			//iterate federationIds
			for(String interestedFederationId : fr.getFederations()){
				//fetch federation with corresponding federationId
				Federation interestedFederation = fedRepo.findOne(interestedFederationId);
				
				//add all federationMembers to platformsToNotify set
				for (FederationMember fm : interestedFederation.getMembers()) {
					platformsToNotify.add(fm);
				}
			}
			
			//send HTTP-POST notification to all other members
			//create securityRequest
			SecurityRequest securityRequest = securityManager.generateSecurityRequest();
			if(securityRequest != null){
				//if the creation of securityRequest is successful broadcast FedreatedResource to interested platforms 
				for(FederationMember fedMem : platformsToNotify){
						
					ResponseEntity<?> serviceResponse = SecuredRequestSender.sendSecuredResourcesAddedOrUpdated(securityRequest, new ResourcesAddedOrUpdatedMessage(Arrays.asList(fr)), fedMem.getInterworkingServiceURL());
						
					boolean verifiedResponse = securityManager.verifyReceivedResponse(serviceResponse.getBody().toString(), "subscriptionManager", fedMem.getPlatformId());
					if (verifiedResponse) logger.info("Broadcast of addedOrUpdatedFederatedResource message successful!");
					else logger.info("Failed to broadcast addedOrUpdatedFederatedResource message due to the response verification error!");
				}
			}
			else logger.info("Failed to broadcast addedOrUpdatedFederatedResource message due to the securityRequest creation failure!");
		}
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
}
