package eu.h2020.symbiote.subman.messaging.consumers;

import eu.h2020.symbiote.cloud.model.internal.CloudResource;
import eu.h2020.symbiote.cloud.model.internal.FederatedResource;
import eu.h2020.symbiote.cloud.model.internal.FederationInfoBean;
import eu.h2020.symbiote.cloud.model.internal.ResourceSharingInformation;
import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.model.cim.Resource;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.subman.messaging.RabbitManager;
import eu.h2020.symbiote.subman.repositories.FederatedResourceRepository;
import eu.h2020.symbiote.subman.repositories.FederationRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles("test")
public class ConsumersTest {

    @Value("${rabbit.exchange.federation}")
    String federationExchange;
    
    @Value("${rabbit.exchange.subscriptionManager.name}")
    String subscriptionManagerExchange;

    @Value("${rabbit.routingKey.federation.created}")
    String federationCreatedKey;
    
    @Value("${rabbit.routingKey.federation.changed}")
    String federationChangedKey;

    @Value("${rabbit.routingKey.federation.deleted}")
    String federationDeletedKey;
    
    @Value("${rabbit.routingKey.subscriptionManager.addOrUpdateFederatedResources}")
    String resAddedOrUpdatedRk;
    
    @Value("${rabbit.routingKey.subscriptionManager.removeFederatedResources}")
    String resRemovedRk;

    @Autowired
    FederationRepository federationRepository;
    
    @Autowired
    FederatedResourceRepository fedResRepo;

    @Autowired
    RabbitManager rabbitManager;

    static Resource resDummy;
	static CloudResource dummy;
	static FederatedResource fr;
	
    @Before
    public void setup() {
        federationRepository.deleteAll();
        resDummy = new Resource();
		resDummy.setInterworkingServiceURL("dummyUrl");
		dummy = new CloudResource();
		dummy.setResource(resDummy);
		FederationInfoBean fib = new FederationInfoBean();
		Map<String, ResourceSharingInformation> map = new HashMap<>();
		map.put("fed1", null);
		fib.setSharingInformation(map);
		dummy.setFederationInfo(fib);
		
		fr = new FederatedResource("a@a",dummy);
		fr.setRestUrl("aa");
    }

    @Test
    public void federationCreatedTest() throws InterruptedException {
        Federation federation = new Federation();

        federation.setId("exampleId");
        federation.setName("FederationName");
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, federation);

        TimeUnit.MILLISECONDS.sleep(400);
        List<Federation> federations = federationRepository.findAll();
        assertEquals(1, federations.size());
    }
    
    @Test
    public void federationChangedTest() throws InterruptedException {
        Federation federation = new Federation();

        federation.setId("exampleId");
        federation.setName("FederationName");
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, federation);

        TimeUnit.MILLISECONDS.sleep(400);
        
        //changing of created federation
        federation.setId("exampleId");
        federation.setName("FederationName1");
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationChangedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        List<Federation> federations = federationRepository.findAll();
        assertEquals(1, federations.size());
        assertEquals("FederationName1", federations.get(0).getName());
    }

    @Test
    public void federationDeletedTest() throws InterruptedException {
        String federationId = "exampleId";
        Federation federation = new Federation();

        federation.setId(federationId);
        federation.setName("FederationName");
        federationRepository.save(federation);

        while (federationRepository.findAll().size() == 0)
            TimeUnit.MILLISECONDS.sleep(100);

        RabbitTemplate rabbitTemplate = rabbitManager.getRabbitTemplate();
        Message message = new Message(federationId.getBytes(), new MessageProperties());
        rabbitTemplate.send(federationExchange, federationDeletedKey, message);

        TimeUnit.MILLISECONDS.sleep(400);
        List<Federation> federations = federationRepository.findAll();
        assertEquals(0, federations.size());
    }
    
    @Test
    public void addedOrUpdatedFederatedResourceLocalMongoStorageTest() throws InterruptedException{
    	
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(0, current.size());
    	ResourcesAddedOrUpdatedMessage msg = new ResourcesAddedOrUpdatedMessage(Arrays.asList(fr));
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resAddedOrUpdatedRk, msg);
    	TimeUnit.MILLISECONDS.sleep(400);
    	current = fedResRepo.findAll();
    	assertEquals(1, current.size());
    	
    }
    
    @Test
    public void serializationTest() {
    	Resource r = new Resource();
    	r.setId("id1");
    	r.setName("dummy");
    	r.setInterworkingServiceURL("dummyURL");
    	CloudResource cr = new CloudResource();
    	cr.setInternalId("id1");
    	cr.setPluginId("pg1");
    	cr.setResource(r);
    	FederationInfoBean fib = new FederationInfoBean();
    	fib.setSymbioteId("id@shs");
    	cr.setFederationInfo(fib);
        FederatedResource fr = new FederatedResource(cr);
        Consumers con = new Consumers();
        
        String serialized = con.serializeFederatedResource(fr);
        FederatedResource cloneFr = con.deserializeFederatedResource(serialized);
        
        assertEquals(cloneFr.getCloudResource().getInternalId(), fr.getCloudResource().getInternalId());
    }
}