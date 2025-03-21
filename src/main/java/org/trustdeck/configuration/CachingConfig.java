/*
 * Trust Deck Services
 * Copyright 2024 Armin Müller & Eric Wündisch
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
import com.hazelcast.config.NearCacheConfig;

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

    @Bean
    public Config hazelcastConfig() {
        Config config = new Config();

        String name = "group-path-cache";

        EvictionConfig evictionConfig = new EvictionConfig()
        		.setMaxSizePolicy(MaxSizePolicy.FREE_HEAP_SIZE)
        		.setEvictionPolicy(EvictionPolicy.LFU);

        NearCacheConfig nearCacheConfig = new NearCacheConfig()
                .setInMemoryFormat(InMemoryFormat.BINARY)
                .setSerializeKeys(true)
                .setInvalidateOnChange(false)
                .setTimeToLiveSeconds(3600)
                .setEvictionConfig(evictionConfig)
                .setLocalUpdatePolicy(NearCacheConfig.LocalUpdatePolicy.CACHE_ON_UPDATE);

        config.setInstanceName("hazelcast-instance")
                .addMapConfig(new MapConfig()
                        .setName(name)
                        .setEvictionConfig(evictionConfig)
                        .setNearCacheConfig(nearCacheConfig));

        // Minimal value to remove the warning which creates a minimal cluster on a single node
        config.getCPSubsystemConfig().setCPMemberCount(3).setGroupSize(3);

        return config;
    }
}