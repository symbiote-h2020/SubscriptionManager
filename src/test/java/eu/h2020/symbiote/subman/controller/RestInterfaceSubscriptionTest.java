package eu.h2020.symbiote.subman.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import eu.h2020.symbiote.subman.repositories.SubscriptionRepository;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles("test")
public class RestInterfaceSubscriptionTest {
	
	ObjectMapper om = new ObjectMapper();
	
	@Value("${platform.id}")
    String thisPlatformId;

	@Autowired
	SubscriptionRepository subRepo;
	
	@Autowired
	RestInterface restInterface;
	
	 @Before
	 public void setUp() {
		 Subscription s = new Subscription();
		 s.setPlatformId(thisPlatformId);
		 subRepo.save(s);
	 }
	 
	@Test
	public void subscriptionDefinitionOK() throws JsonProcessingException{
		Subscription s = new Subscription();
		s.setPlatformId(thisPlatformId);
		s.setLocations(Arrays.asList("Split"));
		
		assertNull(subRepo.findOne(thisPlatformId).getLocations());
		
		assertEquals(new ResponseEntity<>(HttpStatus.OK), restInterface.subscriptionDefinition(new HttpHeaders(), om.writeValueAsString(s)));
		
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

}
