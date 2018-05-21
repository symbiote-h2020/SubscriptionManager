package eu.h2020.symbiote.subman.messaging.consumers;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import eu.h2020.symbiote.cloud.model.internal.CloudResource;
import eu.h2020.symbiote.cloud.model.internal.FederatedResource;
import eu.h2020.symbiote.cloud.model.internal.FederationInfoBean;
import eu.h2020.symbiote.cloud.model.internal.ResourceSharingInformation;
import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.cloud.model.internal.Subscription;
import eu.h2020.symbiote.model.cim.Resource;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.security.commons.SecurityConstants;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;
import eu.h2020.symbiote.subman.messaging.RabbitManager;
import eu.h2020.symbiote.subman.repositories.FederatedResourceRepository;
import eu.h2020.symbiote.subman.repositories.FederationRepository;
import eu.h2020.symbiote.subman.repositories.SubscriptionRepository;
import eu.h2020.symbiote.subman.controller.SecuredRequestSender;
import eu.h2020.symbiote.subman.controller.SecurityManager;

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
    
    @Value("${platform.id}")
    String thisPlatformId;

    @Autowired
    FederationRepository federationRepository;
    
    @Autowired
    FederatedResourceRepository fedResRepo;
    
    @Autowired
    SubscriptionRepository subRepo;

    @Autowired
    RabbitManager rabbitManager;
    
    @Autowired
    SecurityManager securityManager;

    static Resource resDummy;
	static CloudResource dummy;
	static FederatedResource fr;
	FederationMember fm;
	FederationMember fm1;
	Federation f;
	
    @Before
    public void setup() {
        federationRepository.deleteAll();
        fedResRepo.deleteAll();
        subRepo.deleteAll();
        resDummy = new Resource();
		resDummy.setInterworkingServiceURL("dummyUrl");
		dummy = new CloudResource();
		dummy.setResource(resDummy);
		FederationInfoBean fib = new FederationInfoBean();
		Map<String, ResourceSharingInformation> map = new HashMap<>();
		ResourceSharingInformation rsi = new ResourceSharingInformation();
		rsi.setBartering(true);
		map.put("todel", rsi);
		fib.setSharingInformation(map);
		dummy.setFederationInfo(fib);
		
		fr = new FederatedResource("a@a",dummy);
		fr.setRestUrl("aa");
		
		fm = new FederationMember();
		fm.setPlatformId("todel");
		fm1 = new FederationMember();
		fm1.setPlatformId(thisPlatformId);
		f = new Federation();
		f.setMembers(Arrays.asList(fm));
    }

    @Test
    public void federationCreatedTest() throws InterruptedException {
        Federation federation = new Federation();

        federation.setId("exampleId");
        federation.setName("FederationName");
        federation.setMembers(Arrays.asList(fm,fm1));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, federation);

        TimeUnit.MILLISECONDS.sleep(400);
        //check that federation is saved in mongoDB
        assertNotNull(federationRepository.findOne("exampleId"));
        //check that subscription is created for federation member
        assertNotNull(subRepo.findOne(fm.getPlatformId()));
        
        //manipulate subscription of added federationMember
        Subscription s = new Subscription();
        s.setPlatformId("todel");
        s.setLocations(Arrays.asList("Split"));
        subRepo.save(s);
        
        federation.setId("exampleId1");
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        //check that the subscription of federationMember is not overwritten since it already exists
        assertEquals("Split", subRepo.findOne(fm.getPlatformId()).getLocations().get(0));
        
        FederationMember fm2 = new FederationMember();
        fm2.setPlatformId("fmaaa");
        federation.setMembers(Arrays.asList(fm2));
        federation.setId("exampleId2");
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        //check that subscription is not created for fm2 since it is not federated with this platform
        assertNull(subRepo.findOne(fm2.getPlatformId()));
        
    }
    
    @Test
    public void federationChangedTest() throws InterruptedException {
        Federation federation = new Federation();

        federation.setId("exampleId");
        federation.setName("FederationName");
        federation.setMembers(Arrays.asList(fm));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, federation);

        TimeUnit.MILLISECONDS.sleep(400);
        //check that there is no subscription created for fm
        assertNull(subRepo.findOne(fm.getPlatformId()));
        
        
        //adding this platform to existing federation
        federation.setMembers(Arrays.asList(fm,fm1));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationChangedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        //check that now subscription is created for fm
        assertNotNull(subRepo.findOne(fm.getPlatformId()));
        
        FederationMember fm2 = new FederationMember();
        fm2.setPlatformId("fm2");
        federation.setMembers(Arrays.asList(fm,fm1,fm2));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationChangedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        //check that now subscription is created for fm2
        assertNotNull(subRepo.findOne(fm.getPlatformId()));
        assertNotNull(subRepo.findOne(fm2.getPlatformId()));
        
        federation.setMembers(Arrays.asList(fm,fm1));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationChangedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        //check that now subscription is deleted for fm2
        assertNotNull(subRepo.findOne(fm.getPlatformId()));
        assertNull(subRepo.findOne(fm2.getPlatformId()));
        
        //removing this platform from existing federation
        federation.setMembers(Arrays.asList(fm));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationChangedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        //check that subscription of fm is removed
        assertNull(subRepo.findOne(fm.getPlatformId()));
    }

    @Test
    public void federationDeletedTest() throws InterruptedException {
        String federationId = "exampleId";
        Federation federation = new Federation();

        federation.setId(federationId);
        federation.setName("FederationName");
        FederationMember fm1 = new FederationMember();
        fm1.setPlatformId("testPlatform");
        federation.setMembers(Arrays.asList(fm,fm1));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, federation);

        TimeUnit.MILLISECONDS.sleep(500);
        
        assertNotNull(subRepo.findOne(fm.getPlatformId()));
        RabbitTemplate rabbitTemplate = rabbitManager.getRabbitTemplate();
        Message message = new Message(federationId.getBytes(), new MessageProperties());
        rabbitTemplate.send(federationExchange, federationDeletedKey, message);

        TimeUnit.MILLISECONDS.sleep(400);
        assertEquals(0, federationRepository.findAll().size());
        assertNull(subRepo.findOne(fm.getPlatformId()));
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
    public void addedOrUpdatedFederatedResourceInterestedFederationTest() throws InterruptedException{
    	
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(0, current.size());
    	f.setId("todel");
    	federationRepository.insert(f);
    	ResourcesAddedOrUpdatedMessage msg = new ResourcesAddedOrUpdatedMessage(Arrays.asList(fr));
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resAddedOrUpdatedRk, msg);
    	TimeUnit.MILLISECONDS.sleep(400);
    	current = fedResRepo.findAll();
    	assertEquals(1, current.size());  	
    }
    
    @Test
    public void deletedFederatedResourceLocalMongoDeletitionTest() throws InterruptedException{
    	Set<String> federationDummies = new HashSet<>();
    	federationDummies.add("todel");
    	fr.setFederations(federationDummies);
    	fedResRepo.save(fr);
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(1, current.get(0).getFederations().size());
    	assertEquals(1, current.size());
    	
    	Map<String, Set<String>> toDelete = new HashMap<>();
    	toDelete.put("a@a", federationDummies);
    	ResourcesDeletedMessage msg = new ResourcesDeletedMessage(toDelete);
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resRemovedRk, msg);
    	TimeUnit.MILLISECONDS.sleep(400);
    	current = fedResRepo.findAll();
    	assertEquals(0, current.size());
    }
    
    @Test
    public void deletedFederatedResourceInterestedFederationTest() throws InterruptedException{
    	Set<String> federationDummies = new HashSet<>();
    	federationDummies.add("todel");
    	fr.setFederations(federationDummies);
    	fedResRepo.save(fr);
    	f.setId("todel");
    	federationRepository.insert(f);
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(1, current.get(0).getFederations().size());
    	assertEquals(1, current.size());
    	
    	Map<String, Set<String>> toDelete = new HashMap<>();
    	toDelete.put("a@a", federationDummies);
    	ResourcesDeletedMessage msg = new ResourcesDeletedMessage(toDelete);
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resRemovedRk, msg);
    	TimeUnit.MILLISECONDS.sleep(400);
    	current = fedResRepo.findAll();
    	assertEquals(0, current.size());
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
    
    @Test
    public void serializationFailureTest() {
    	
        Consumers con = new Consumers();
        FederatedResource cloneFr = con.deserializeFederatedResource("dummy");
        
        assertNull(cloneFr);
    }
    
    @Test
    public void deletedFederatedResourceInterestedFederationBroadcastNoResponseTest() throws InterruptedException{
    	Set<String> federationDummies = new HashSet<>();
    	federationDummies.add("todel");
    	fr.setFederations(federationDummies);
    	fedResRepo.save(fr);
    	f.setId("todel");
    	federationRepository.insert(f);
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(1, current.get(0).getFederations().size());
    	assertEquals(1, current.size());
    	
    	Map<String, Set<String>> toDelete = new HashMap<>();
    	toDelete.put("a@a", federationDummies);
    	ResourcesDeletedMessage msg = new ResourcesDeletedMessage(toDelete);
  
    	doReturn(new SecurityRequest("sdad"))
        .when(securityManager).generateSecurityRequest();
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resRemovedRk, msg);
    	TimeUnit.MILLISECONDS.sleep(400);
    	current = fedResRepo.findAll();
    	assertEquals(0, current.size());
    }
    
    @Test
    public void addedOrUpdatedFederatedResourceInterestedFederationBroadcastNoResponseTest() throws InterruptedException{
    	
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(0, current.size());
    	f.setId("todel");
    	federationRepository.insert(f);
    	ResourcesAddedOrUpdatedMessage msg = new ResourcesAddedOrUpdatedMessage(Arrays.asList(fr));
    	
    	doReturn(new SecurityRequest("sdad"))
        .when(securityManager).generateSecurityRequest();
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resAddedOrUpdatedRk, msg);
    	TimeUnit.MILLISECONDS.sleep(400);
    	current = fedResRepo.findAll();
    	assertEquals(1, current.size());  	
    }
}