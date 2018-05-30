package eu.h2020.symbiote.subman;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import eu.h2020.symbiote.subman.messaging.RabbitManager;

@RunWith(MockitoJUnitRunner.class)
public class RabbitManagerTest {
	
	@Mock
	RabbitTemplate rabbitTemplate;
	
	@Autowired
	@InjectMocks
	RabbitManager rabbitManager;
	
	@Before
	public void setup() {
	    ReflectionTestUtils.setField(rabbitManager, "rabbitHost", "localhost");
	    ReflectionTestUtils.setField(rabbitManager, "rabbitUsername", "guest");
	    ReflectionTestUtils.setField(rabbitManager, "rabbitPassword", "guest");
	}
	
	@Test
	public void testSendRpcMessage(){
		when(rabbitTemplate.convertSendAndReceive(any(String.class), any(String.class), any(Object.class),
				any(CorrelationData.class))).thenReturn(null);
		assertNull(rabbitManager.sendRpcMessage("", "", new Object()));
		
		when(rabbitTemplate.convertSendAndReceive(any(String.class), any(String.class), any(Object.class),
				any(CorrelationData.class))).thenReturn("message received");
		assertNotNull(rabbitManager.sendRpcMessage("", "", new Object()));	
	}
	
	@Test
	public void rabbitInit() {
		rabbitManager.init();
		assertNotNull(rabbitManager.connection);
		
		rabbitManager.cleanup();
		assertFalse(rabbitManager.connection.isOpen());
	}
}
