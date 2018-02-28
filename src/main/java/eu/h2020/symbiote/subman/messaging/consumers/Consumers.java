package eu.h2020.symbiote.subman.messaging.consumers;

import java.io.IOException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.model.cim.Resource;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.subman.repositories.FederationRepository;

@Component
public class Consumers {

	private static Log logger = LogFactory.getLog(Consumers.class);

	@Value("${platform.id}")
	private String platformId;

	@Autowired
	private FederationRepository fedRepo;

	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.created}"))
	public void federationCreated(Federation federation) throws IOException {
		fedRepo.save(federation);
	}

	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.changed}"))
	public void federationChanged(Federation federation) throws IOException {
		fedRepo.save(federation); // check if old one is deleted
	}

	@RabbitListener(bindings = @QueueBinding(value = @Queue, exchange = @Exchange(value = "${rabbit.exchange.federation}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.federation.deleted}"))
	public void federationDeleted(String federationId) throws IOException {
		fedRepo.delete(federationId);
	}

	@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "${rabbit.queueName.subscriptionManager.addOrUpdateResources}"), exchange = @Exchange(value = "${rabbit.exchange.subscriptionManager.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.addOrUpdateResources}"))
	public void addedOrUpdatedFederatedResource(Resource resource) throws IOException {
		ObjectMapper mapper = new ObjectMapper();

		for (Federation fed : fedRepo.findAll()) {
			boolean member = false;
			List<FederationMember> members = fed.getMembers();
			for (FederationMember fm : members) {
				if (fm.getPlatformId().equals(platformId))
					member = true;
			}

			// if this platform is a member of current federation
			if (member) {
				// send HTTP-POST notification to all other members..
				for (FederationMember fm : members) {
					if (!fm.getPlatformId().equals(platformId)) {
						try {
							//TODO send HTTPS message to platforms Subscription Manager
						} catch (Exception e) {
							logger.error("ERROR OCCURED SENDING HTTP POST REQUEST!");
							e.printStackTrace();
						}
					}
				}
			}
		}
	}

	@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "${rabbit.queueName.subscriptionManager.removeResources}"), exchange = @Exchange(value = "${rabbit.exchange.subscriptionManager.name}", type = "topic", ignoreDeclarationExceptions = "true", durable = "false"), key = "${rabbit.routingKey.subscriptionManager.removeResources}"))
	public void removeFederatedResource(String resourceId) throws IOException {
		ObjectMapper mapper = new ObjectMapper();

		for (Federation fed : fedRepo.findAll()) {
			boolean member = false;
			List<FederationMember> members = fed.getMembers();
			for (FederationMember fm : members) {
				if (fm.getPlatformId().equals(platformId))
					member = true;
			}

			// if this platform is a member of current federation
			if (member) {
				// send HTTP-POST notification to all other members..
				for (FederationMember fm : members) {
					if (!fm.getPlatformId().equals(platformId)) {
						try {
							//TODO send HTTPS message to platforms Subscription Manager
						} catch (Exception e) {
							logger.error("ERROR OCCURED SENDING HTTP POST REQUEST!");
							e.printStackTrace();
						}
					}
				}
			}
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
