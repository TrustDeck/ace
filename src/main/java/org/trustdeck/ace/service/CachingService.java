/*
 * ACE - Advanced Confidentiality Engine
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

package org.trustdeck.ace.service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class encapsulates a service to cache authentication information.
 *
 * @author Eric Wündisch & Armin Müller
 */
@Service
@Slf4j
public class CachingService {

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private OidcService oidcService;

    private static final String WRITE_LOCK_NAME = "write-lock";
    private static final String CACHE_NAME = "hazelcast-instance";
    private static final long MAX_WAIT_TIME_SECONDS = 10;

    @SuppressWarnings("unchecked")
	public List<String> getGroupPaths(String userId) {
        IMap<String, Object> resourceCache = hazelcastInstance.getMap(CACHE_NAME);

        // This should not happen often so this is mostly false
        if (resourceCache.isLocked(WRITE_LOCK_NAME)) {
            long startTime = System.currentTimeMillis();
            
            // Wait and check again
            while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME_SECONDS * 1000) {
                
            	// Check if it is still locked
                if (resourceCache.isLocked(WRITE_LOCK_NAME)) {
                	List<String> resource = (List<String>) resourceCache.get(userId);
                    if (resource != null) {
                        return resource;
                    }
                }
            }
        }

        // Retrieve data from cache
        List<String> resource = (List<String>) resourceCache.get(userId);
        if (resource != null) {
            return resource;
        }

        // Cache miss. Retrieve data from OIDC service and store in cache.
        List<String> groups = null;
        try {
            resourceCache.lock(WRITE_LOCK_NAME);
            groups = oidcService.flatGroupPaths(oidcService.getGroupsByUserId(userId), false);
            resourceCache.put(userId, groups, 10, TimeUnit.MINUTES);
            log.trace("Insert into cache: USER-ID: "+userId+", GROUPS: "+groups);
        } catch (NullPointerException | UnsupportedOperationException e) {
            groups = new ArrayList<>();
        } finally {
            resourceCache.unlock(WRITE_LOCK_NAME);
        }

        return groups;
    }
}
