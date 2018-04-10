package eu.h2020.symbiote.subman;

import eu.h2020.symbiote.subman.controller.SecurityManager;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * @author Vasileios Glykantzis (ICOM)
 * @since 2/22/2018.
 */
@Profile("test")
@Configuration
public class TestConfiguration {

    @Bean
    @Primary
    public SecurityManager securityManager() {
        return Mockito.mock(SecurityManager.class);
    }
}
