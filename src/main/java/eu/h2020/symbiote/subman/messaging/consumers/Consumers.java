package eu.h2020.symbiote.subman.messaging.consumers;

import java.io.IOException;

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

	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.created}"))
	public void federationCreated(Message msg) throws IOException {

		Federation federation = (Federation) messageConverter.fromMessage(msg);
		fedRepo.save(federation);
		logger.info("Federation with id: " + federation.getId() + " added to repository.");
	}

	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.changed}"))
	public void federationChanged(Message msg) throws IOException {

		Federation federation = (Federation) messageConverter.fromMessage(msg);
		fedRepo.save(federation);
		logger.info("Federation with id: " + federation.getId() + " updated.");
	}

	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.deleted}"))
	public void federationDeleted(String federationId) throws IOException {

		fedRepo.delete(federationId);
		logger.info("Federation with id: " + federationId + " removed from repository.");
	}

	@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "${rabbit.queueName.subscriptionManager.addOrUpdateFederatedResources}"), exchange = @Exchange(value = "${rabbit.exchange.subscriptionManager.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.addOrUpdateFederatedResources}"))
	public void addedOrUpdateFederatedResource(Message msg) throws IOException {

		ResourcesAddedOrUpdatedMessage rsMsg = (ResourcesAddedOrUpdatedMessage) messageConverter.fromMessage(msg);	
		logger.info("Received ResourcesAddedOrUpdatedMessage from Platform Registry");

		for(FederatedResource fr : rsMsg.getNewFederatedResources()){
			fedResRepo.save(fr);
			logger.info("Federated resource with id " + fr.getId()
			+ " added to repository.");
			
			//TODO check if the whole list is for the same federation members
			Federation interestedFederation = fedRepo.findOne(fr.getFederationId());
			
			for (FederationMember fm : interestedFederation.getMembers()) {
				// TODO send HTTP-POST notification to all other members..
				SecurityRequest securityRequest = securityManager.generateSecurityRequest();
				if(securityRequest != null){
					//TODO fetch federation member url and receiving platform id
					String fmUrl = null;
					String receivingPlatformId = null;
					
					ResponseEntity<?> serviceResponse = SecuredRequestSender.sendSecuredResourcesAddedOrUpdated(securityRequest, rsMsg, fmUrl);
					
					//TODO check that serviceResponse is properly fetched
					boolean verifiedResponse = securityManager.verifyReceivedResponse(serviceResponse.getBody().toString(), "subscriptionManager", receivingPlatformId);
					if (verifiedResponse) logger.info("Broadcast of addedOrUpdatedFederatedResource message successful!");
					else logger.info("Failed to broadcast addedOrUpdatedFederatedResource message due to the response verification error!");
				}
				else logger.info("Failed to broadcast addedOrUpdatedFederatedResource message due to the securityRequest failure!");
			}
		}
	}

	@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "${rabbit.queueName.subscriptionManager.removeFederatedResources}"), exchange = @Exchange(value = "${rabbit.exchange.subscriptionManager.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.removeFederatedResources}"))
	public void removeFederatedResource(Message msg) throws IOException {

		ResourcesDeletedMessage rdDel = (ResourcesDeletedMessage) messageConverter.fromMessage(msg);
		logger.info("Received ResourcesDeletedMessage from Platform Registry");
		
		for(String id : rdDel.getDeletedIds()){
			FederatedResource fedRes = fedResRepo.findOne(id);
			Federation interestedFederation = fedRepo.findOne(fedRes.getFederationId());
			
			fedResRepo.delete(id);
			logger.info("Federated resource with id " + id + " removed from repository.");
			
			//TODO check if the whole list is for the same federation members
			for (FederationMember fm : interestedFederation.getMembers()) {
				// TODO send HTTP-POST notification to all other members..
			}
		}
	}
}
