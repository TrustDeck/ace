package org.trustdeck.ace.configuration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties
public class RoleConfig {
    @Value("${app.roles:-}")
    private List<String> roles = new ArrayList<String>();
}
