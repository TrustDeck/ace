/*
 * Trust Deck Services
 * Copyright 2024-2025 Armin Müller and Eric Wündisch
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

package org.trustdeck.utils;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Basic least recently used (LRU) cache.
 * Eviction based on the access order settings of the superclass.
 * Not thread-safe.
 * 
 * @param <K> the key
 * @param <V> the value
 * @author Armin Müller
 */
@Getter
@Setter
public final class LRUCache<K, V> extends LinkedHashMap<K, V> {

	/** This class' serial version unique identifier. */
	private static final long serialVersionUID = -3476172641347057859L;
	
	/** The maximum size of the cache. */
	private final int maxSize;
	
	/**
	 * Basic constructor for the cache.
	 * 
	 * @param maxSize the maximum size for the cache; further 
	 * entries lead to eviction of the least recently used one
	 */
	public LRUCache(int maxSize) {
		// Setting accessOrder to true leads to LRU behavior
		super(16, 0.75f, true);
		this.maxSize = maxSize;
	}

	@Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}
