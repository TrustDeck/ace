/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2024-2025 Armin Müller & Eric Wündisch
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
import org.trustdeck.ace.utils.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CachingService is a service class responsible for caching group paths for faster authentication.
 * It uses Hazelcast as the underlying caching mechanism.
 *
 * This service includes methods to retrieve group paths for a user, cache the group paths if not present,
 * flush and re-cache groups when a domain match is found, and handle concurrent access using write locks.
 *
 * @author Eric Wündisch & Armin Müller
 */
@Service
@Slf4j
public class CachingService {

    /** Hazelcast instance used for caching data. */
    @Autowired
    private HazelcastInstance hazelcastInstance;

    /** OIDC service used for retrieving (group) information related to users. */
    @Autowired
    private OidcService oidcService;

    /** Name of the lock used to control write operations on the cache. */
    private static final String WRITE_LOCK_NAME = "write-lock";

    /** Name of the cache used in Hazelcast. */
    private static final String CACHE_NAME = "hazelcast-instance";

    /** Maximum time to wait for the lock to be released, in seconds. */
    private static final long MAX_WAIT_TIME_SECONDS = 3;

    /**
     * Retrieves the group paths for the given user ID from the cache.
     * If the resource is currently locked, it waits for the lock to be released before trying to retrieve.
     * If the group paths are not present in the cache, it retrieves them from the OIDC service and caches them.
     *
     * @param userId the ID of the user
     * @return a list of group paths for the user
     */
    @SuppressWarnings("unchecked")
    public List<String> getGroupPaths(String userId) {
        IMap<String, List<String>> resourceCache = hazelcastInstance.getMap(CACHE_NAME);

        // Check if the write lock is currently held, and wait until it is released or timeout occurs
        if (resourceCache.isLocked(WRITE_LOCK_NAME)) {
            long startTime = System.currentTimeMillis();

            // Wait and check again until the max wait time is reached
            while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME_SECONDS * 1000) {

                // If the lock is released, retrieve data from the cache
                if (!resourceCache.isLocked(WRITE_LOCK_NAME)) {
                    List<String> resource = resourceCache.get(userId);
                    if (resource != null) {
                        return resource;
                    }
                }
            }
        }

        // Retrieve data from the cache
        List<String> resource = resourceCache.get(userId);
        if (resource != null && !resource.isEmpty()) {
            return resource;
        }

        // Cache miss - retrieve data from OIDC service and store in the cache
        return this.cacheGroups(userId);
    }

    /**
     * Flushes and re-caches group paths for a user if the given domain occurs in the cached groups.
     *
     * @param userId the ID of the user
     * @param domain the domain to check for
     */
    public void flushAndReCacheMatchingGroups(String userId, String domain) {
        this.flushAndReCacheMatchingGroups(userId, domain, false);
    }

    /**
     * Flushes and re-caches group paths for a user if the given domain occurs in the cached groups.
     *
     * @param userId the ID of the user
     * @param domain the domain to check for
     * @param force force deletion of groups from cache
     */
    public void flushAndReCacheMatchingGroups(String userId, String domain, boolean force) {
        this.flushGroupIfDomainOccurs(userId, domain, force);
        this.getGroupPaths(userId);
    }

    /**
     * Retrieves group paths for the given user ID from the OIDC service, caches them,
     * and returns the group paths.
     *
     * @param userId the ID of the user
     * @return a list of group paths for the user
     */
    protected List<String> cacheGroups(String userId) {
        IMap<String, List<String>> resourceCache = hazelcastInstance.getMap(CACHE_NAME);
        List<String> groups = null;

        try {
            // Check if the cache can be write-locked. If not, wait.
            if (resourceCache.isLocked(WRITE_LOCK_NAME)) {
                Long start = System.currentTimeMillis();
                while (resourceCache.isLocked(WRITE_LOCK_NAME)) {
                    // Still locked. Do we wait, or not?
                    if (System.currentTimeMillis() - start <= MAX_WAIT_TIME_SECONDS * 1000) {
                        // We wait. Check again.
                        if (!resourceCache.isLocked(WRITE_LOCK_NAME)) {
                            // Now it's available. Lock it and store data.
                            resourceCache.lock(WRITE_LOCK_NAME);

                            // Retrieve groups from OIDC service and convert to a flat list of group paths
                            groups = Utility.simpleFlatGroupPaths(oidcService.getGroupsByUserId(userId), true);

                            // Store the group paths in the cache with a 10-minute expiration time
                            resourceCache.put(userId, groups, 10, TimeUnit.MINUTES);
                            log.debug("Insert into cache: USER-ID: " + userId + ", GROUPS: " + groups);
                        }
                    }
                }
            } else {
                // The lock is available. Lock it and store data.
                resourceCache.lock(WRITE_LOCK_NAME);

                // Retrieve groups from OIDC service and convert to a flat list of group paths
                groups = Utility.simpleFlatGroupPaths(oidcService.getGroupsByUserId(userId), true);

                // Store the group paths in the cache with a 10-minute expiration time
                resourceCache.put(userId, groups, 10, TimeUnit.MINUTES);
                log.debug("Insert into cache: USER-ID: " + userId + ", GROUPS: " + groups);
            }
        } catch (NullPointerException | UnsupportedOperationException e) {
            // Handle cases where the group retrieval or caching fails
            groups = new ArrayList<>();
        } finally {
            // Unlock the cache after write operation is complete
            resourceCache.unlock(WRITE_LOCK_NAME);
        }

        if (groups == null) {
            // If the groups are still null, the cache was locked longer than the waiting time
            groups = new ArrayList<>();
            log.debug("Cache was busy longer than the maximum waiting time. No groups added to it.");
        }

        return groups;
    }

    /**
     * Flushes the group paths from the cache if the specified domain is found in any of the group paths.
     *
     * @param domain the domain to check for in group paths
     * @param userId the ID of the user
     * @param force force deletion of groups from cache
     */
    public void flushGroupIfDomainOccurs(String userId, String domain, boolean force) {
        IMap<String, List<String>> resourceCache = hazelcastInstance.getMap(CACHE_NAME);

        try {
            // Lock the cache to ensure exclusive access during flush operation
            resourceCache.lock(WRITE_LOCK_NAME);

            // Iterate over the users (key: userID, value: the user's group paths)
            for (Map.Entry<String, List<String>> entry : resourceCache.entrySet()) {
                if (force) {
                    // Lazy flushing: delete all group paths for the specified user
                    if (entry.getKey().equals(userId)) {
                        resourceCache.delete(entry.getKey());
                    }
                } else {
                    // Check if the domain is actually part of the user's group paths
                    List<String> groupPaths = entry.getValue();

                    // Check if any group path ends with the specified domain
                    if (groupPaths.stream().anyMatch(s -> s.endsWith("/" + domain))) {
                        // Remove all group paths from the cache if the domain is found
                        resourceCache.delete(entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            // Log any errors that occur during the flush operation
            log.debug("Couldn't flush cache: " + e.getMessage());
        } finally {
            // Unlock the cache after flush operation is complete
            resourceCache.unlock(WRITE_LOCK_NAME);
        }
    }
}
