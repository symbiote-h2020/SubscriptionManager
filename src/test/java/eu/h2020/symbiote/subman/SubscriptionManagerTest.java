package eu.h2020.symbiote.subman;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import eu.h2020.symbiote.subman.repositories.SubscriptionRepository;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles("test")
public class SubscriptionManagerTest {

	@Autowired
	SubscriptionRepository subscriptionRepo;
	
	@Value("${platform.id}")
	private String platformId;
	
	@Test
	public void contextLoads() {
		assertEquals(1, subscriptionRepo.findAll().size());
	}

}
