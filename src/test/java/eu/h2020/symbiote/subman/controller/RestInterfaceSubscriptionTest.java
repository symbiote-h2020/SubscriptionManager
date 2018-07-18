package eu.h2020.symbiote.subman.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.Subscription;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.subman.repositories.FederationRepository;
import eu.h2020.symbiote.subman.repositories.SubscriptionRepository;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles("test")
public class RestInterfaceSubscriptionTest {
	
	ObjectMapper om = new ObjectMapper();
	
	@Value("${platform.id}")
    String thisPlatformId;
	
	@Mock
	SecurityManager securityManager;

	@Autowired
	SubscriptionRepository subRepo;
	
	@Autowired
	FederationRepository fedRepo;
	
	@Autowired
	@InjectMocks
	RestInterface restInterface;
	
	 @Before
	 public void setUp() {
		 MockitoAnnotations.initMocks(this);
		 
		 subRepo.deleteAll();
		 fedRepo.deleteAll();
		 
		 Subscription s = new Subscription();
		 s.setPlatformId(thisPlatformId);
		 subRepo.save(s);
	 }
	 
	@Test
	public void subscriptionDefinitionOK() throws JsonProcessingException, InterruptedException{
		System.out.println(thisPlatformId);
		Subscription s = new Subscription();
		s.setPlatformId(thisPlatformId);
		s.setLocations(Arrays.asList("Split"));
		
		assertNull(subRepo.findOne(thisPlatformId).getLocations());
		
		assertEquals(new ResponseEntity<>(HttpStatus.OK), restInterface.subscriptionDefinition(new HttpHeaders(), om.writeValueAsString(s)));
		TimeUnit.MILLISECONDS.sleep(500);
		assertNotNull(subRepo.findOne(thisPlatformId).getLocations());	
	}
	
	@Test
	public void subscriptionDefinitionMalformedJson() throws JsonProcessingException{
		Subscription s = new Subscription();
		s.setPlatformId(thisPlatformId);
		s.setLocations(Arrays.asList("Split"));
		
		assertEquals(new ResponseEntity<>("Received JSON message cannot be mapped to Subscription!", HttpStatus.BAD_REQUEST),restInterface.subscriptionDefinition(new HttpHeaders(), "dasdas"+om.writeValueAsString(s)+"sdsfsfs"));
		assertNull(subRepo.findOne(thisPlatformId).getLocations());
	}
	
	@Test
	public void subscriptionDefinitionPlatformIdMissmatch() throws JsonProcessingException{
		Subscription s = new Subscription();
		s.setPlatformId("malformed");
		
		assertEquals(new ResponseEntity<>("PlatformId check failed!", HttpStatus.BAD_REQUEST),restInterface.subscriptionDefinition(new HttpHeaders(), om.writeValueAsString(s)));
		assertNull(subRepo.findOne(thisPlatformId).getLocations());
	}
	
	@Test
	public void foreignSubscriptionDefinitionMalformedJson() throws JsonProcessingException{
		Subscription s = new Subscription();
		s.setPlatformId("sender");
		s.setLocations(Arrays.asList("Split"));
		
		assertEquals(new ResponseEntity<>("Received JSON message cannot be mapped to Subscription!", HttpStatus.BAD_REQUEST),restInterface.foreignSubscriptionDefinition(new HttpHeaders(), "dasdas"+om.writeValueAsString(s)+"sdsfsfs"));
	}
	
//	@Test
//	public void foreignSubscriptionDefinitionPlatformsNotFederated() throws JsonProcessingException, InterruptedException{
//		
//		Subscription s = new Subscription();
//		s.setPlatformId("sender");
//		s.setLocations(Arrays.asList("Split"));
//		
//		Federation federation1 = new Federation();
//		federation1.setId("fed1");
//		Federation federation2 = new Federation();
//		federation2.setId("fed2");
//		
//		FederationMember fm1 = new FederationMember();
//		fm1.setPlatformId("sender");
//		
//		FederationMember fm2 = new FederationMember();
//		fm2.setPlatformId(thisPlatformId);
//		
//		federation1.setMembers(Arrays.asList(fm1));
//		federation2.setMembers(Arrays.asList(fm2));
//		
//		fedRepo.save(federation1);
//		fedRepo.save(federation2);
//		TimeUnit.MILLISECONDS.sleep(2000);
//		assertEquals(new ResponseEntity<>("Sender platform and receiving platfrom are not federated!", HttpStatus.BAD_REQUEST),restInterface.foreignSubscriptionDefinition(new HttpHeaders(), om.writeValueAsString(s)));
//	}
	
//	@Test
//	public void foreignSubscriptionDefinitionOK() throws JsonProcessingException{
//		
//		Subscription s = new Subscription();
//		s.setPlatformId("sender");
//		s.setLocations(Arrays.asList("Split"));
//		
//		Federation federation1 = new Federation();
//		federation1.setId("fed1");
//		
//		FederationMember fm1 = new FederationMember();
//		fm1.setPlatformId("sender");
//		
//		FederationMember fm2 = new FederationMember();
//		fm2.setPlatformId(thisPlatformId);
//		
//		federation1.setMembers(Arrays.asList(fm1,fm2));
//		
//		fedRepo.save(federation1);
//		
//		when(securityManager.generateServiceResponse()).thenReturn(new ResponseEntity<>(HttpStatus.OK));
//		when(securityManager.checkRequest(any(HttpHeaders.class),any(String.class),any(String.class))).thenReturn(new ResponseEntity<>(HttpStatus.OK));
//		assertEquals(HttpStatus.OK,restInterface.foreignSubscriptionDefinition(new HttpHeaders(), om.writeValueAsString(s)).getStatusCode());
//		assertNotNull(subRepo.findOne("sender"));
//	}

}
