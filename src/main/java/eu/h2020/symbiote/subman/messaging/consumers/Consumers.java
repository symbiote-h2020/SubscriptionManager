package eu.h2020.symbiote.subman.messaging.consumers;

import java.io.IOException;

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
import eu.h2020.symbiote.subman.repositories.FederationRepository;

@Component
public class Consumers {
	
	@Autowired
	private FederationRepository fedRepo;

	@RabbitListener(bindings = @QueueBinding(
	        value = @Queue,
	        exchange = @Exchange(value = "${rabbit.exchange.federation.name}", type = "topic", ignoreDeclarationExceptions = "true", durable="false"),
	        key = "${rabbit.routingKey.subscriptionManager.federationCreated}"
	    ))
	    public void federationCreated(Federation federation) throws IOException {
			fedRepo.save(federation);
	    }
	
	@RabbitListener(bindings = @QueueBinding(
	        value = @Queue,
	        exchange = @Exchange(value = "${rabbit.exchange.federation.name}", type = "topic", ignoreDeclarationExceptions = "true", durable="false"),
	        key = "${rabbit.routingKey.subscriptionManager.federationChanged}"
	    ))
	    public void federationChanged(Federation federation) throws IOException {
			fedRepo.save(federation); //check if old one is deleted
	    }
	
	@RabbitListener(bindings = @QueueBinding(
	        value = @Queue,
	        exchange = @Exchange(value = "${rabbit.exchange.federation.name}", type = "topic", ignoreDeclarationExceptions = "true", durable="false"),
	        key = "${rabbit.routingKey.subscriptionManager.federationDeleted}"
	    ))
	    public void federationDeleted(String federationId) throws IOException {
	        fedRepo.delete(federationId);
	    }
	
	@RabbitListener(bindings = @QueueBinding(
	        value = @Queue,
	        exchange = @Exchange(value = "${rabbit.exchange.federation.name}", type = "topic", ignoreDeclarationExceptions = "true", durable="false"),
	        key = "${rabbit.routingKey.subscriptionManager.resourceAdded}"
	    ))
	    public void resourceAdded(Resource resource) throws IOException {
	        
	    }
	
	@RabbitListener(bindings = @QueueBinding(
	        value = @Queue,
	        exchange = @Exchange(value = "${rabbit.exchange.federation.name}", type = "topic", ignoreDeclarationExceptions = "true", durable="false"),
	        key = "${rabbit.routingKey.subscriptionManager.resourceUpdated}"
	    ))
	    public void resourceUpdated(Resource resource) throws IOException {
	        
	    }
	
	@RabbitListener(bindings = @QueueBinding(
	        value = @Queue,
	        exchange = @Exchange(value = "${rabbit.exchange.federation.name}", type = "topic", ignoreDeclarationExceptions = "true", durable="false"),
	        key = "${rabbit.routingKey.subscriptionManager.resourceDeleted}"
	    ))
	    public void resourceDeleted(Resource resource) throws IOException {
	        
	    }
	
	@RabbitListener(bindings = @QueueBinding(
	        value = @Queue,
	        exchange = @Exchange(value = "${rabbit.exchange.subscription.name}", type = "topic", ignoreDeclarationExceptions = "true", durable="false"),
	        key = "${rabbit.routingKey.subscriptionManager.subscriptionCreated}"
	    ))
	    public void subscriptionCreated() throws IOException { //DEFINE SUBSCRIPTION OBJECT
	        
	    }
	
	@RabbitListener(bindings = @QueueBinding(
	        value = @Queue,
	        exchange = @Exchange(value = "${rabbit.exchange.subscription.name}", type = "topic", ignoreDeclarationExceptions = "true", durable="false"),
	        key = "${rabbit.routingKey.subscriptionManager.subscriptionRemoved}"
	    ))
	    public void subscriptionRemoved() throws IOException { //DEFINE SUBSCRIPTION OBJECT
	        
	    }
}
