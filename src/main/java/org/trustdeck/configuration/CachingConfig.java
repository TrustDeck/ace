/*
 * Trust Deck Services
 * Copyright 2024 Armin Müller and Eric Wündisch
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.trustdeck.configuration;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class provides configuration for the caching.
 * 
 * @author Armin Müller
 *
 */
@Configuration
public class CachingConfig {

	/**
	 * This method is used to initialize the hazelcast cache configuration object.
	 * 
	 * @return a configuration object to use for initializing a hazelcast instance
	 */    
    @Bean
    public Config hazelcastConfig() {
        Config config = new Config();
        config.setInstanceName("trustdeck-hazelcast");

        EvictionConfig eviction = new EvictionConfig()
                .setMaxSizePolicy(MaxSizePolicy.FREE_HEAP_SIZE)
                .setEvictionPolicy(EvictionPolicy.LFU);

        config.addMapConfig(new MapConfig()
                .setName("permission-actions-by-context")
                .setInMemoryFormat(InMemoryFormat.BINARY)
                .setEvictionConfig(eviction)
                .setTimeToLiveSeconds(15 * 60));

        config.addMapConfig(new MapConfig()
                .setName("effective-permissions-by-subject")
                .setInMemoryFormat(InMemoryFormat.BINARY)
                .setEvictionConfig(eviction)
                .setTimeToLiveSeconds(15 * 60));

        return config;
    }
}