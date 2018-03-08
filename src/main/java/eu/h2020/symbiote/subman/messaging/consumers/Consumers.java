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
import org.springframework.stereotype.Component;

import eu.h2020.symbiote.cloud.model.internal.FederatedResource;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
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

	private MessageConverter messageConverter;

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

	@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "${rabbit.queueName.subscriptionManager.addOrUpdateResources}"), exchange = @Exchange(value = "${rabbit.exchange.subscriptionManager.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.addOrUpdateResources}"))
	public void addedOrUpdatedFederatedResource(Message msg) throws IOException {

		FederatedResource federatedResource = (FederatedResource) messageConverter.fromMessage(msg);
		fedResRepo.save(federatedResource);
		logger.info("Federated resource with id " + federatedResource.getId()
				+ " received from PlatfromRegistry added to repository.");

		Federation interestedFederation = fedRepo.findOne(federatedResource.getFederationId());

		for (FederationMember fm : interestedFederation.getMembers()) {
			// TODO send HTTP-POST notification to all other members..
		}
	}

	@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "${rabbit.queueName.subscriptionManager.removeResources}"), exchange = @Exchange(value = "${rabbit.exchange.subscriptionManager.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.removeResources}"))
	public void removeFederatedResource(String resourceId) throws IOException {

		FederatedResource fedRes = fedResRepo.findOne(resourceId);
		Federation interestedFederation = fedRepo.findOne(fedRes.getFederationId());

		fedResRepo.delete(resourceId);
		logger.info("Federated resource with id " + resourceId + " removed from repository.");

		for (FederationMember fm : interestedFederation.getMembers()) {
			// TODO send HTTP-POST notification to all other members..
		}
	}

//	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.subscription.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.subscriptionCreated}"))
//	public void subscriptionCreated() throws IOException { // DEFINE
//															// SUBSCRIPTION
//															// OBJECT
//
//	}
//
//	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.subscription.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.subscriptionRemoved}"))
//	public void subscriptionRemoved() throws IOException { // DEFINE
//															// SUBSCRIPTION
//															// OBJECT
//
//	}
}
