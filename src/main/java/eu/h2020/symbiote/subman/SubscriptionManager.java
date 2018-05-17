package eu.h2020.symbiote.subman;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

import eu.h2020.symbiote.cloud.model.internal.Subscription;
import eu.h2020.symbiote.subman.repositories.SubscriptionRepository;

@EnableDiscoveryClient
@EnableAutoConfiguration
@SpringBootApplication
public class SubscriptionManager {

	@Value("${platform.id}")
	private String platformId;
	
	public static void main(String[] args) {
		WaitForPort.waitForServices(WaitForPort.findProperty("SPRING_BOOT_WAIT_FOR_SERVICES"));
		SpringApplication.run(SubscriptionManager.class, args);
    }
	

	@Bean
	CommandLineRunner initData(SubscriptionRepository subscriptionRepo){
	   return args -> {
		   Subscription initial = new Subscription();
		   initial.setPlatformId(platformId);
		   subscriptionRepo.save(initial);
	   };
	}
}
