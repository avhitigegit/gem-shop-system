package lk.nuvi.gemshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Gem Shop System – backend API. One package per module (see package-info.java in each). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GemShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(GemShopApplication.class, args);
    }
}
