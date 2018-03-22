package eu.h2020.symbiote.subman;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.CloudResource;
import eu.h2020.symbiote.cloud.model.internal.FederatedResource;
import eu.h2020.symbiote.cloud.model.internal.ResourcesAddedOrUpdatedMessage;
import eu.h2020.symbiote.cloud.model.internal.ResourcesDeletedMessage;
import eu.h2020.symbiote.model.cim.Resource;
import eu.h2020.symbiote.subman.controller.RestInterface;

@RunWith(SpringRunner.class)
public class RestInterfaceTest {
	
	private static final Logger logger = LoggerFactory.getLogger(RestInterfaceTest.class);
	
	@Test
	public void resourceAddedOrUpdatedMappertest(){
		ObjectMapper om = new ObjectMapper();
		
		Resource resDummy = new Resource();
		resDummy.setInterworkingServiceURL("dummyUrl");
		CloudResource dummy = new CloudResource();
		dummy.setResource(resDummy);
		FederatedResource fr = new FederatedResource("a@a",dummy);
		fr.setRestUrl("aa");
		ResourcesAddedOrUpdatedMessage toSend = new ResourcesAddedOrUpdatedMessage(Arrays.asList(fr));
		try {
			String json = om.writeValueAsString(toSend);

			ResourcesAddedOrUpdatedMessage received = RestInterface.mapAddedOrUpdatedMessage(json);

			assertEquals(toSend.getNewFederatedResources().size(), received.getNewFederatedResources().size());
			assertEquals("a@a", received.getNewFederatedResources().get(0).getSymbioteId());
		} catch (JsonProcessingException e) {
			logger.info("ERROR mapping object to json!");
		}
	}
	
	@Test
	public void resourceDeletedMappertest(){
		ObjectMapper om = new ObjectMapper();
		
		Map<String,Set<String>> toTest = new HashMap<String,Set<String>>();
		HashSet<String> set = new HashSet<String>();
		set.add("FED1");
		set.add("FED2");
		toTest.put("fedResKey1", set);
		ResourcesDeletedMessage toSend = new ResourcesDeletedMessage(toTest);
		try {
			String json = om.writeValueAsString(toSend);

			ResourcesDeletedMessage received = RestInterface.mapDeletedMessage(json);

			assertTrue(received.getDeletedFederatedResourcesMap().containsKey("fedResKey1"));
			assertTrue(received.getDeletedFederatedResourcesMap().get("fedResKey1").size() == 2);
		} catch (JsonProcessingException e) {
			logger.info("ERROR mapping object to json!");
		}
	}

}
