package eu.h2020.symbiote.subman.controller;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.CloudResource;
import eu.h2020.symbiote.cloud.model.internal.FederatedResource;
import eu.h2020.symbiote.cloud.model.internal.FederationInfoBean;
import eu.h2020.symbiote.cloud.model.internal.ResourceSharingInformation;
import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.model.cim.Resource;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;

@RunWith(MockitoJUnitRunner.class)
public class SecuredRequestSenderTest {

	@Mock
	SecurityManager securityManager;
	
	@Mock
	RestTemplate restTemplate;
	
	@InjectMocks
	SecuredRequestSender srs;
	
	ObjectMapper om = new ObjectMapper();
	
	static ResourcesAddedOrUpdatedMessage toSend;
	static ResourcesDeletedMessage deleted;
	static FederatedResource fr;
	static Resource resDummy;
	static CloudResource dummy;
	
	@Before
    public void setUp() {
    	
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
		toSend = new ResourcesAddedOrUpdatedMessage(Arrays.asList(fr));
		
		Map<String, Set<String>> rdMap = new HashMap<>();
		Set<String> fede = new HashSet<>(Arrays.asList("fed1", "fed2"));
		rdMap.put("fr1", fede);
		deleted = new ResourcesDeletedMessage(rdMap);
	}
	
	@Test
	public void sendAddedOrUpdatedTest() throws JsonProcessingException{
		
		SecurityRequest sr= new SecurityRequest("guestTokenDummy");
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, SecuredRequestSender.sendSecuredRequest(sr, om.writeValueAsString(toSend), "localhost:8080".replaceAll("/+$", "") + "/subscriptionManager" + "/addOrUpdate").getStatusCode());
	}
	
	@Test
	public void sendDeletedTest() throws JsonProcessingException{
		
		SecurityRequest sr= new SecurityRequest("guestTokenDummy");
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, SecuredRequestSender.sendSecuredRequest(sr, om.writeValueAsString(deleted), "localhost:8080".replaceAll("/+$", "") + "/subscriptionManager" + "/delete").getStatusCode());
	}
}
