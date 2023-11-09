package dev.switchboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SwitchboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwitchboardApplication.class, args);
    }
}
