package eu.h2020.symbiote.subman.messaging.consumers;

import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.subman.messaging.RabbitManager;
import eu.h2020.symbiote.subman.repositories.FederationRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles("test")
public class ConsumersTest {

    @Value("${rabbit.exchange.federation}")
    String federationExchange;

    @Value("${rabbit.routingKey.federation.created}")
    String federationCreatedKey;

    @Autowired
    FederationRepository federationRepository;

    @Autowired
    RabbitManager rabbitManager;

    @Before
    public void setup() {
        federationRepository.deleteAll();
    }

    @Test
    public void federationCreated() throws InterruptedException {
        Federation federation = new Federation();

        federation.setId("exampleId");
        federation.setName("FederationName");
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, federation);

        TimeUnit.MILLISECONDS.sleep(400);
        List<Federation> federations = federationRepository.findAll();
        assertEquals(1, federations.size());
    }
}