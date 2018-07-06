package eu.h2020.symbiote.subman.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import eu.h2020.symbiote.cloud.model.internal.CloudResource;
import eu.h2020.symbiote.cloud.model.internal.FederatedResource;
import eu.h2020.symbiote.cloud.model.internal.FederatedResourceInfo;
import eu.h2020.symbiote.cloud.model.internal.FederationInfoBean;
import eu.h2020.symbiote.cloud.model.internal.ResourceSharingInformation;
import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.cloud.model.internal.Subscription;
import eu.h2020.symbiote.model.cim.Actuator;
import eu.h2020.symbiote.model.cim.Capability;
import eu.h2020.symbiote.model.cim.Device;
import eu.h2020.symbiote.model.cim.Location;
import eu.h2020.symbiote.model.cim.Resource;
import eu.h2020.symbiote.model.cim.Sensor;
import eu.h2020.symbiote.model.cim.Service;
import eu.h2020.symbiote.model.cim.SymbolicLocation;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;
import eu.h2020.symbiote.subman.controller.SecurityManager;
import eu.h2020.symbiote.subman.repositories.FederatedResourceRepository;
import eu.h2020.symbiote.subman.repositories.FederationRepository;
import eu.h2020.symbiote.subman.repositories.SubscriptionRepository;

/**
 * @author Petar Krivic (UniZG-FER)
 */
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
        resDummy = new Service();
		resDummy.setInterworkingServiceURL("dummyUrl");
		dummy = new CloudResource();
		dummy.setResource(resDummy);
		
		ResourceSharingInformation rsi = new ResourceSharingInformation();
		rsi.setBartering(true);
		Map<String, ResourceSharingInformation> rsiMap = new HashMap<>();
		rsiMap.put("todel", rsi);
		FederationInfoBean fib = new FederationInfoBean();
		fib.setSharingInformation(rsiMap);
		dummy.setFederationInfo(fib);
		
		fr = new FederatedResource("a@a",dummy, (double) 4);
		//fr.setRestUrl("aa");
		
		fm = new FederationMember();
		fm.setPlatformId("todel");
		fm.setInterworkingServiceURL("http://todel-interworking.com");
		fm1 = new FederationMember();
		fm1.setPlatformId(thisPlatformId);
		f = new Federation();
//    	f.setId("fedId");
		f.setId("todel");
		f.setMembers(Arrays.asList(fm));
		
		Subscription s = new Subscription();
    	s.setPlatformId("todel");
    	subRepo.save(s);
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
        FederationMember fm5 = new FederationMember();
		fm5.setPlatformId("1950");
        federation.setMembers(Arrays.asList(fm5));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, federation);

        TimeUnit.MILLISECONDS.sleep(400);
        //check that there is no subscription created for fm
        assertNull(subRepo.findOne(fm5.getPlatformId()));
        
        
        //adding this platform to existing federation
        federation.setMembers(Arrays.asList(fm5,fm1));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationChangedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        //check that now subscription is created for fm5
        assertNotNull(subRepo.findOne(fm5.getPlatformId()));
        
        FederationMember fm2 = new FederationMember();
        fm2.setPlatformId("fm2");
        fm2.setInterworkingServiceURL("fm2-interworking");
        federation.setMembers(Arrays.asList(fm5,fm1,fm2));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationChangedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        //check that now subscription is created for fm2
        assertNotNull(subRepo.findOne(fm5.getPlatformId()));
        assertNotNull(subRepo.findOne(fm2.getPlatformId()));
        
        federation.setMembers(Arrays.asList(fm5,fm1));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationChangedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        //check that now subscription is deleted for fm2
        assertNotNull(subRepo.findOne(fm5.getPlatformId()));
        assertNull(subRepo.findOne(fm2.getPlatformId()));
        
        //removing this platform from existing federation
        federation.setMembers(Arrays.asList(fm5));
        rabbitManager.sendAsyncMessageJSON(federationExchange, federationChangedKey, federation);
        
        TimeUnit.MILLISECONDS.sleep(400);
        //check that subscription of fm5 is removed
        assertNull(subRepo.findOne(fm5.getPlatformId()));
    }

    @Test
    public void federationDeletedTest() throws InterruptedException {

    	Federation federation = new Federation();

        federation.setId("exampleId");
        federation.setName("FederationName");
        FederationMember fm2 = new FederationMember();
        fm2.setPlatformId("fm2");
        federation.setMembers(Arrays.asList(fm,fm1,fm2));

        rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, federation);

        TimeUnit.MILLISECONDS.sleep(400);
        
        assertNotNull(subRepo.findOne(fm.getPlatformId()));
        assertNotNull(subRepo.findOne(fm2.getPlatformId()));
        assertNotNull(federationRepository.findOne("exampleId"));
        
        RabbitTemplate rabbitTemplate = rabbitManager.getRabbitTemplate();
        Message message = new Message("exampleId".getBytes(), new MessageProperties());
        rabbitTemplate.send(federationExchange, federationDeletedKey, message);

        TimeUnit.MILLISECONDS.sleep(400);
        //check that federation is removed from mongoDB
        assertNull(federationRepository.findOne("exampleId"));
        //check that subscriptions of fm and fm2 are removed from mongoDB
        assertNull(subRepo.findOne(fm.getPlatformId()));
        assertNull(subRepo.findOne(fm2.getPlatformId()));
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
    	Map<String, FederatedResourceInfo> fedDummies = new HashMap<>();
    	FederatedResourceInfo fri = new FederatedResourceInfo("a@a@todel", "odataurl", "resturl");
    	fedDummies.put("todel", fri);
    	fr.setFederatedResourceInfoMap(fedDummies);
    	fedResRepo.save(fr);
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(1, current.get(0).getFederations().size());
    	assertEquals(1, current.size());
    	
    	Set<String> toDelete = new HashSet<>();
    	toDelete.add("a@a@todel");
    	ResourcesDeletedMessage msg = new ResourcesDeletedMessage(toDelete);
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resRemovedRk, msg);
    	TimeUnit.MILLISECONDS.sleep(400);
    	current = fedResRepo.findAll();
    	assertEquals(0, current.size());
    }
    
    @Test
    public void deletedFederatedResourceInterestedFederationTest() throws InterruptedException{
    	Map<String, FederatedResourceInfo> fedDummies = new HashMap<>();
    	FederatedResourceInfo fri = new FederatedResourceInfo("a@a@todel", "odataurl", "resturl");
    	fedDummies.put("todel", fri);
    	fr.setFederatedResourceInfoMap(fedDummies);
    	fedResRepo.save(fr);
    	f.setId("todel");
    	federationRepository.insert(f);
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(1, current.get(0).getFederations().size());
    	assertEquals(1, current.size());
    	
    	Set<String> toDelete = new HashSet<>();
    	toDelete.add("a@a@todel");
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
    	fib.setAggregationId("id@shs");
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
    	Map<String, FederatedResourceInfo> fedDummies = new HashMap<>();
    	FederatedResourceInfo fri = new FederatedResourceInfo("a@a@todel", "odataurl", "resturl");
    	fedDummies.put("todel", fri);
    	fr.setFederatedResourceInfoMap(fedDummies);
    	fedResRepo.save(fr);
    	f.setId("todel");
    	
    	//federationRepository.insert(f);
    	rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, f);
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(1, current.get(0).getFederations().size());
    	assertEquals(1, current.size());
    	
    	Set<String> toDelete = new HashSet<>();
    	toDelete.add("a@a@todel");
    	ResourcesDeletedMessage msg = new ResourcesDeletedMessage(toDelete);
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resRemovedRk, msg);
    	TimeUnit.MILLISECONDS.sleep(400);
    	current = fedResRepo.findAll();
    	assertEquals(0, current.size());
    }
    
    @Test
    public void deletedFederatedResourceInterestedFederationBroadcastNoResponseTestMockedSecurity() throws InterruptedException{
    	Map<String, FederatedResourceInfo> fedDummies = new HashMap<>();
    	FederatedResourceInfo fri = new FederatedResourceInfo("a@a@todel", "odataurl", "resturl");
    	fedDummies.put("todel", fri);
    	fr.setFederatedResourceInfoMap(fedDummies);
    	fedResRepo.save(fr);
    	f.setId("todel");
    	
    	//federationRepository.insert(f);
    	rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, f);
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(1, current.get(0).getFederations().size());
    	assertEquals(1, current.size());
    	
    	Set<String> toDelete = new HashSet<>();
    	toDelete.add("a@a@todel");
    	ResourcesDeletedMessage msg = new ResourcesDeletedMessage(toDelete);
    	
    	 
    	doReturn(new SecurityRequest("sdad"))
    		.when(securityManager).generateSecurityRequest();
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resRemovedRk, msg);
    	TimeUnit.MILLISECONDS.sleep(400);
    	current = fedResRepo.findAll();
    	assertEquals(0, current.size());
    }
    
    /**
     * Throws nullPointer since SecuredRequestSender does not return service response
     * @throws InterruptedException
     */
    @Test
    public void addedOrUpdatedFederatedResourceInterestedFederationBroadcastNoResponseTestMockedSecurity() throws InterruptedException{
    	
    	List<FederatedResource> current = fedResRepo.findAll();
    	assertEquals(0, current.size());
    	f.setMembers(Arrays.asList(fm,fm1));
    	//federationRepository.insert(f);
    	rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, f);
    	TimeUnit.MILLISECONDS.sleep(400);
    	
    	ResourcesAddedOrUpdatedMessage msg = new ResourcesAddedOrUpdatedMessage(Arrays.asList(fr));
    	
    	doReturn(new SecurityRequest("sdad"))
        .when(securityManager).generateSecurityRequest();
        
    	doReturn(true)
        .when(securityManager).verifyReceivedResponse(any(String.class),any(String.class),any(String.class));
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resAddedOrUpdatedRk, msg);
    	TimeUnit.MILLISECONDS.sleep(400);
    	current = fedResRepo.findAll();
    	assertEquals(1, current.size());  	
    }
    
    @Test
    public void addedOrUpdatedFederatedResourceInterestedFederationBroadcastNoResponseTest() throws InterruptedException{
    	
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
    public void SubscriptionResourceTypeMatcher() throws InterruptedException{
    	
    	Subscription s = new Subscription();
    	
    	Resource r = new Service();
    	r.setInterworkingServiceURL("dafsfa");
    	CloudResource crSub = new CloudResource();
    	crSub.setResource(r);
    	FederatedResource fedResource= new FederatedResource("a@a",crSub, (double) 4);
    	
    	//check service
    	assertTrue(Consumers.isSubscribed(s, fedResource));
    	
    	r = new Device();
    	r.setInterworkingServiceURL("dafsfa");
    	crSub.setResource(r);
    	//check device
    	assertTrue(Consumers.isSubscribed(s, fedResource));
    	
    	s.getResourceType().put("device", false);
    	
    	r = new Sensor();
    	r.setInterworkingServiceURL("dafsfa");
    	crSub.setResource(r);
    	//check sensor
    	assertTrue(Consumers.isSubscribed(s, fedResource));
    	
    	r = new Actuator();
    	r.setInterworkingServiceURL("dafsfa");
    	crSub.setResource(r);
    	//check actuator
    	assertTrue(Consumers.isSubscribed(s, fedResource));
    	
    	s.getResourceType().put("actuator", false);
    	assertFalse(Consumers.isSubscribed(s, fedResource));
    }
    
    @Test
    public void SubscriptionLocationMatcher() throws InterruptedException{
    	
    	Subscription s = new Subscription();
    	s.setLocations(Arrays.asList("Split"));
    	
    	Device d = new Sensor();
    	d.setInterworkingServiceURL("dafsfa");
    	 	
    	CloudResource crSub = new CloudResource();
    	crSub.setResource(d);
    	FederatedResource fedResource= new FederatedResource("a@a",crSub, (double) 4);
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); // no location
    	
    	Location l = new SymbolicLocation();
    	l.setName("Split");
    	d.setLocatedAt(l);

    	assertTrue(Consumers.isSubscribed(s, fedResource)); // location matching
    	
    	s.setLocations(Arrays.asList("Zagreb"));

    	assertFalse(Consumers.isSubscribed(s, fedResource)); // location missmatch
    	
    	Service sd = new Service();
    	sd.setInterworkingServiceURL("dafsfa");
    	crSub.setResource(sd); 
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); // not instance of device
    	
    }
    
    @Test
    public void SubscriptionObservedPropertyMatcher() throws InterruptedException{
    	
    	Subscription s = new Subscription();
    	s.setObservedProperties(Arrays.asList("temperature", "humidity"));
    	
    	Sensor d = new Sensor();
    	d.setInterworkingServiceURL("dafsfa");
    	d.setObservesProperty(Arrays.asList("noise"));	
    	CloudResource crSub = new CloudResource();
    	crSub.setResource(d);
    	FederatedResource fedResource= new FederatedResource("a@a",crSub, (double) 4);
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); //check op name missmatch
    	
    	d.setObservesProperty(Arrays.asList("temperature"));	

    	assertTrue(Consumers.isSubscribed(s, fedResource)); // op matching one property
    	
    	d.setObservesProperty(Arrays.asList("temperature", "humidity"));	

    	assertTrue(Consumers.isSubscribed(s, fedResource));	//matching all properties
    	
    	d.setObservesProperty(Arrays.asList());	
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); // empty observedProperties
    	
    	d.setObservesProperty(null);	
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); // observedProperties is null
    	
    	Actuator sd = new Actuator();
    	sd.setInterworkingServiceURL("dafsfa");
    	
    	crSub.setResource(sd);  	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); //check resource not instance of sensor
    }
    
    @Test
    public void SubscriptionCapabilityMatcher() throws InterruptedException{
    	
    	Subscription s = new Subscription();
    	s.setCapabilities(Arrays.asList("on-off"));
    	
    	Actuator d = new Actuator();
    	d.setInterworkingServiceURL("dafsfa");
    	
    	Capability cap = new Capability();
    	cap.setName("move");
    	
    	CloudResource crSub = new CloudResource();
    	crSub.setResource(d);
    	FederatedResource fedResource= new FederatedResource("a@a",crSub, (double) 4);
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); // check capabilities null
    	
    	d.setCapabilities(Arrays.asList());
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); // check empty capabilities
    	
    	d.setCapabilities(Arrays.asList(cap));
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); //check capability name missmatch
    	
    	cap.setName("on-off");
    	d.setCapabilities(Arrays.asList(cap));	

    	assertTrue(Consumers.isSubscribed(s, fedResource));	//check matching
    	
    	Sensor sd = new Sensor();
    	sd.setInterworkingServiceURL("dafsfaaa");
    	crSub.setResource(sd);
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); //check resource not instance of actuator
    }
    
    @Test
    public void SubscriptionLocationCapabilityMatcher() throws InterruptedException{
    	
    	Subscription s = new Subscription();
    	s.setLocations(Arrays.asList("Split"));
    	s.setCapabilities(Arrays.asList("on-off"));
    	
    	Actuator d = new Actuator();
    	d.setInterworkingServiceURL("dafsfa");
    	Capability cap = new Capability();
    	cap.setName("move");
    	d.setCapabilities(Arrays.asList(cap));	
    	CloudResource crSub = new CloudResource();
    	crSub.setResource(d);
    	FederatedResource fedResource= new FederatedResource("a@a",crSub, (double) 4);
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); //location missmatch
    	
    	Location l = new SymbolicLocation();
    	l.setName("Split");
    	d.setLocatedAt(l);
    	
    	assertFalse(Consumers.isSubscribed(s, fedResource)); //capability name missmatch
    	
    	cap.setName("on-off");
    	d.setCapabilities(Arrays.asList(cap));
  	
    	assertTrue(Consumers.isSubscribed(s, fedResource)); // subscription match
    }
    
    @Test
    public void addedOrupdatedResources() throws InterruptedException {
    	
    	f.setMembers(Arrays.asList(fm,fm1));
    	federationRepository.save(f);
    	//rabbitManager.sendAsyncMessageJSON(federationExchange, federationCreatedKey, f);
    	TimeUnit.MILLISECONDS.sleep(500);
    	
    	Map<String, FederatedResourceInfo> fedDummies = new HashMap<>();
    	FederatedResourceInfo fri = new FederatedResourceInfo("a@a@todel", "odataurl", "resturl");
    	fedDummies.put("todel", fri);
    	fr.setFederatedResourceInfoMap(fedDummies);
    	
    	ResourcesAddedOrUpdatedMessage raoum = new ResourcesAddedOrUpdatedMessage(Arrays.asList(fr));
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resAddedOrUpdatedRk, raoum);
    	TimeUnit.MILLISECONDS.sleep(500);
    	
    	assertTrue(fedResRepo.findAll().size()>0);
    	assertNotNull(fedResRepo.findOne("a@a"));
    	
    	Set<String> toDel = new HashSet<>();
    	toDel.add("a@a@todel");
    	ResourcesDeletedMessage rdm = new ResourcesDeletedMessage(toDel);
    	
    	rabbitManager.sendAsyncMessageJSON(subscriptionManagerExchange, resRemovedRk, rdm);
    	TimeUnit.MILLISECONDS.sleep(500);
    	
    	assertFalse(fedResRepo.findAll().size()>0);
    	assertNull(fedResRepo.findOne("a@a"));
    }
    
}