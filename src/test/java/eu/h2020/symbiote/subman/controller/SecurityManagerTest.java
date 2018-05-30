package eu.h2020.symbiote.subman.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(locations = "classpath:test.properties")
public class SecurityManagerTest {
	
	@Autowired
	SecurityManager securityManager;
	
	@Test
	public void testSecurityNotEnabledServiceResponse () {
		assertEquals(new ResponseEntity<>("generateServiceResponse: Security is disabled", HttpStatus.OK), securityManager.generateServiceResponse());
	}
	
	@Test
	public void testSecurityNotEnabledCheckRequest () {
		assertEquals(new ResponseEntity<>("Security disabled", HttpStatus.OK), securityManager.checkRequest(new HttpHeaders(), "", ""));
	}
}
