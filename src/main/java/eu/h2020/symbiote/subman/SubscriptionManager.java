package eu.h2020.symbiote.subman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@EnableDiscoveryClient
@EnableAutoConfiguration
@SpringBootApplication
public class SubscriptionManager {

	public static void main(String[] args) {
		WaitForPort.waitForServices(WaitForPort.findProperty("SPRING_BOOT_WAIT_FOR_SERVICES"));
		SpringApplication.run(SubscriptionManager.class, args);
    }
}
