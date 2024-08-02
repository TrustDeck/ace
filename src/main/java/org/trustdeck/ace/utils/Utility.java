/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2022-2024 Armin Müller & Eric Wündisch
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

package org.trustdeck.ace.utils;

import org.springframework.stereotype.Component;

/**
 * This class offers a variety of utilities.
 * 
 * @author Armin Müller
 */
@Component
public class Utility {
	
	/** Maximum allowed validity time in seconds. */
	public static final Long MAX_ALLOWED_VALIDITY_TIME = 30 * 365 * 24 * 60 * 60L;
	
	/**
	 * This methods processes the given String, computes 
	 * the encoded time and returns it in seconds.
	 * 
	 * @param validityTime the String containing the validity time
	 * @return the computed validity time in seconds or {@code null} if something went wrong
	 */
	public static Long validityTimeToSeconds(String validityTime) {
		if (validityTime == null) {
			return null;
		}
		
		String vTime = validityTime.trim().toLowerCase();
		
		// Search for the start index of the unit
		int indexOfUnit = 0;
		for (int i = 0; i < vTime.length(); i++) {
			if ("abcdefghijklmnopqrstuvwxyz".contains(String.valueOf(vTime.charAt(i)))) {
				indexOfUnit = i;
				break;
			}
			
			if (i == vTime.length() - 1) {
				// There was no unit found
				return null;
			}
		}
		
		// Extract unit and time
		String unit = vTime.substring(indexOfUnit);
		Long time = 0L;
		try {
			time = Long.valueOf(vTime.substring(0, indexOfUnit).trim());
		} catch (NumberFormatException e) {
			return null;
		}
		
		// Transform time into seconds
		if (unit.contains("years") || unit.contains("year") || unit.contains("y")) {
			return time * 365 * 24 * 60 * 60L;
		}
		
		if (unit.contains("weeks") || unit.contains("week") || unit.contains("w")) {
			return time * 7 * 24 * 60 * 60L;
		}
		
		if (unit.contains("days") || unit.contains("day") || unit.contains("d")) {
			return time * 24 * 60 * 60L;
		}
		
		if (unit.contains("hours") || unit.contains("hour") || unit.contains("h")) {
			return time * 60 * 60L;
		}
		
		if (unit.contains("minutes") || unit.contains("minute") || unit.contains("mins") || unit.contains("min") || unit.contains("m")) {
			return time * 60L;
		}
		
		if (unit.contains("seconds") || unit.contains("second") || unit.contains("secs") || unit.contains("sec") || unit.contains("s")) {
			return time;
		}
		
		// If this point is reached, no validity time could be recognized.
		return null;
	}
}