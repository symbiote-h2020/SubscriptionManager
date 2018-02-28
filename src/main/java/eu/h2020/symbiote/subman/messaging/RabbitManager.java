package eu.h2020.symbiote.subman.messaging;

import com.rabbitmq.client.*;

import eu.h2020.symbiote.subman.messaging.consumers.Consumers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Rabbit AMQP bean.
 *
 * @author Petar Krivic
 * 30/01/2018
 */
@Component
public class RabbitManager {

	private static Log log = LogFactory.getLog(RabbitManager.class);

	@Value("${rabbit.host}")
	private String rabbitHost;
	@Value("${rabbit.username}")
	private String rabbitUsername;
	@Value("${rabbit.password}")
	private String rabbitPassword;

	private Connection connection;
	private RabbitTemplate rabbitTemplate;

	public RabbitManager(RabbitTemplate rabbitTemplate) throws Exception {
		this.rabbitTemplate = rabbitTemplate;
	}

	/**
	 * Initiates connection with Rabbit server using parameters from
	 * bootstrapProperties
	 *
	 * @throws IOException
	 * @throws TimeoutException
	 */
	public Connection getConnection() throws IOException, TimeoutException {
		if (connection == null) {
			ConnectionFactory factory = new ConnectionFactory();
			factory.setHost(this.rabbitHost);
			factory.setUsername(this.rabbitUsername);
			factory.setPassword(this.rabbitPassword);
			this.connection = factory.newConnection();
		}
		return this.connection;
	}

	/**
	 * Method creates channel and declares Rabbit exchanges. It triggers start
	 * of all consumers used in Registry communication.
	 */
	public void init() {
		
		log.info("RabbitManager of SubscriptionManager is being initialized!");

		try {
			getConnection();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
	}
	
	public Object sendRpcMessage(String exchange, String routingKey, Object obj) {
		log.info("Sending RPC message");

		String correlationId = UUID.randomUUID().toString();
		rabbitTemplate.setReplyTimeout(30000);
		Object receivedObj = rabbitTemplate.convertSendAndReceive(exchange, routingKey, obj,
				new CorrelationData(correlationId));
		if (receivedObj == null) {
			log.info("Received null or Timeout!");
			return null;
		}

		log.info("RPC Response received obj: " + receivedObj);

		return receivedObj;
	}
	
	/**
	 * Cleanup method for rabbit - set on pre destroy
	 */
	@PreDestroy
	public void cleanup() {
		log.info("Rabbit cleaned!");
		try {
			if (this.connection != null && this.connection.isOpen()) {
				this.connection.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

