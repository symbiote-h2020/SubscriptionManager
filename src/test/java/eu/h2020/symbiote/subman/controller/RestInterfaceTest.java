package eu.h2020.symbiote.subman.controller;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.CloudResource;
import eu.h2020.symbiote.cloud.model.internal.FederatedResource;
import eu.h2020.symbiote.cloud.model.internal.FederationInfoBean;
import eu.h2020.symbiote.cloud.model.internal.ResourceSharingInformation;
import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.model.cim.Resource;
import eu.h2020.symbiote.model.mim.Federation;
import eu.h2020.symbiote.model.mim.FederationMember;
import eu.h2020.symbiote.security.commons.SecurityConstants;
import eu.h2020.symbiote.subman.messaging.RabbitManager;
import eu.h2020.symbiote.subman.repositories.FederatedResourceRepository;
import eu.h2020.symbiote.subman.repositories.FederationRepository;


@RunWith(MockitoJUnitRunner.class)
public class RestInterfaceTest {
	
	ObjectMapper om = new ObjectMapper();
	
	@Mock
	RabbitManager rabbitManager;
	
	@Mock
	SecurityManager securityManager;
	
	@Mock
	FederationRepository fedRepo;

	@Mock
	FederatedResourceRepository fedResRepo;
	
	@InjectMocks
	RestInterface restInterface;
	
	static ResourcesAddedOrUpdatedMessage  toSend;
	static ResourcesDeletedMessage deleted;
	static FederatedResource fr;
	static Federation f;
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
		
		FederationMember fm = new FederationMember();
		fm.setPlatformId("p1");
		f = new Federation();
		f.setMembers(Arrays.asList(fm));
        
    }
	
	@Test
	public void resourcesAddedOrUpdatedBadRequestMapperFailure(){
		assertEquals(new ResponseEntity<>("Received message cannot be mapped to ResourcesAddedOrUpdatedMessage!",HttpStatus.BAD_REQUEST), restInterface.resourcesAddedOrUpdated(new HttpHeaders(), "sss"));
	}
	
	@Test
	public void resourcesDeletedBadRequestMapperFailure(){
		assertEquals(new ResponseEntity<>("Received message cannot be mapped to ResourcesDeletedMessage!", HttpStatus.BAD_REQUEST), restInterface.resourcesDeleted(new HttpHeaders(), "sss"));
	}
	
	@Test
	public void resourcesAddedOrUpdatedBadRequestConditionFailure() throws JsonProcessingException{
		when(fedRepo.findOne("fed1")).thenReturn(null);
		assertEquals(new ResponseEntity<>("Sender not allowed to share all received fedrated resources!", HttpStatus.BAD_REQUEST), restInterface.resourcesAddedOrUpdated(new HttpHeaders(), om.writeValueAsString(toSend)));
	}
	
	@Test
	public void resourcesDeletedBadRequestConditionFailure() throws JsonProcessingException{
		when(fedResRepo.findOne(any(String.class))).thenReturn(null);
		assertEquals(new ResponseEntity<>("The platform that shared the resource is not in the federation",
				HttpStatus.BAD_REQUEST), restInterface.resourcesDeleted(new HttpHeaders(), om.writeValueAsString(deleted)));
	}
	
	@Test
	public void checkPlatformIdInFederationsCondition1Test(){
		when(fedRepo.findOne("fed1")).thenReturn(null);
		assertEquals(false, restInterface.checkPlatformIdInFederationsCondition1("p1", toSend));

		when(fedRepo.findOne("fed1")).thenReturn(new Federation());
		assertEquals(false, restInterface.checkPlatformIdInFederationsCondition1("p1", toSend));
		
		when(fedRepo.findOne("fed1")).thenReturn(f);
		assertEquals(true, restInterface.checkPlatformIdInFederationsCondition1("p1", toSend));
	}
	
	@Test
	public void checkPlatformIdInFederationsCondition2Test(){
		when(fedResRepo.findOne(any(String.class))).thenReturn(null);
		assertEquals(false, restInterface.checkPlatformIdInFederationsCondition2(deleted));

		when(fedResRepo.findOne(any(String.class))).thenReturn(fr);
		when(fedRepo.findOne(any(String.class))).thenReturn(null);
		assertEquals(false, restInterface.checkPlatformIdInFederationsCondition2(deleted));

		when(fedResRepo.findOne(any(String.class))).thenReturn(fr);
		when(fedRepo.findOne(any(String.class))).thenReturn(f);
		assertEquals(false, restInterface.checkPlatformIdInFederationsCondition2(deleted));
		
		when(fedResRepo.findOne(any(String.class))).thenReturn(fr);
		when(fedRepo.findOne(any(String.class))).thenReturn(f);
		assertEquals(false, restInterface.checkPlatformIdInFederationsCondition2(deleted));
		
		FederationMember fm = new FederationMember();
		fm.setPlatformId("a");
		f.setMembers(Arrays.asList(fm));
		when(fedResRepo.findOne(any(String.class))).thenReturn(fr);
		when(fedRepo.findOne(any(String.class))).thenReturn(f);
		assertEquals(true, restInterface.checkPlatformIdInFederationsCondition2(deleted));
	}
	
	@Test
	public void resourcesAddedOrUpdatedUnauthorized() throws JsonProcessingException{
		when(fedRepo.findOne("fed1")).thenReturn(f);
		fr = new FederatedResource("a@p1",dummy);
		toSend = new ResourcesAddedOrUpdatedMessage(Arrays.asList(fr));
		
		when(securityManager.generateServiceResponse()).thenReturn(new ResponseEntity<>(HttpStatus.OK));
		when(securityManager.checkRequest(any(HttpHeaders.class),any(String.class),any(String.class))).thenReturn(new ResponseEntity<>(HttpStatus.UNAUTHORIZED));
		assertEquals(new ResponseEntity<>(HttpStatus.UNAUTHORIZED), restInterface.resourcesAddedOrUpdated(new HttpHeaders(), om.writeValueAsString(toSend)));
	}
	
	@Test
	public void resourcesAddedOrUpdatedAuthorizedOk() throws JsonProcessingException{
		when(fedRepo.findOne("fed1")).thenReturn(f);
		fr = new FederatedResource("a@p1",dummy);
		toSend = new ResourcesAddedOrUpdatedMessage(Arrays.asList(fr));

		when(securityManager.generateServiceResponse()).thenReturn(new ResponseEntity<>(HttpStatus.OK));
		when(securityManager.checkRequest(any(HttpHeaders.class),any(String.class),any(String.class))).thenReturn(new ResponseEntity<>(HttpStatus.OK));
		HttpHeaders headers = new HttpHeaders();
		headers.put(SecurityConstants.SECURITY_RESPONSE_HEADER, Collections.singletonList(null));
		assertEquals(new ResponseEntity<>(headers, HttpStatus.OK),restInterface.resourcesAddedOrUpdated(new HttpHeaders(), om.writeValueAsString(toSend)));
	}
	
	@Test
	public void resourcesDeletedUnauthorized() throws JsonProcessingException{
		FederationMember fm = new FederationMember();
		fm.setPlatformId("a");
		f.setMembers(Arrays.asList(fm));
		when(fedResRepo.findOne(any(String.class))).thenReturn(fr);
		when(fedRepo.findOne(any(String.class))).thenReturn(f);
		
		when(securityManager.generateServiceResponse()).thenReturn(new ResponseEntity<>(HttpStatus.UNAUTHORIZED));
		assertEquals(new ResponseEntity<>(HttpStatus.UNAUTHORIZED), restInterface.resourcesDeleted(new HttpHeaders(), om.writeValueAsString(deleted)));
	}
	
	@Test
	public void resourcesDeletedAuthorizedOk() throws JsonProcessingException{
		FederationMember fm = new FederationMember();
		fm.setPlatformId("a");
		f.setMembers(Arrays.asList(fm));
		when(fedResRepo.findOne(any(String.class))).thenReturn(fr);
		when(fedRepo.findOne(any(String.class))).thenReturn(f);
		
		when(securityManager.generateServiceResponse()).thenReturn(new ResponseEntity<>(HttpStatus.OK));
		when(securityManager.checkRequest(any(HttpHeaders.class),any(String.class),any(String.class))).thenReturn(new ResponseEntity<>(HttpStatus.OK));
		HttpHeaders headers = new HttpHeaders();
		headers.put(SecurityConstants.SECURITY_RESPONSE_HEADER, Collections.singletonList(null));
		assertEquals(new ResponseEntity<>(headers, HttpStatus.OK), restInterface.resourcesDeleted(new HttpHeaders(), om.writeValueAsString(deleted)));
	}
}
