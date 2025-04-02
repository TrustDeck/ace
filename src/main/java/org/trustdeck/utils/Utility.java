/*
 * Trust Deck Services
 * Copyright 2022-2025 Armin Müller & Eric Wündisch
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

import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class offers a variety of utilities.
 *
 * @author Armin Müller & Eric Wündisch
 */
@Slf4j
@Component
public class Utility {
    
    /** The default alphabet (A-Z0-9) used for the pseudonymization process. */
    private static final String DEFAULT_PSEUDONYMIZATION_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	
	/**
	 * This method processes the given String, computes
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
		
		// Search for the start index of the unit aka the first non-number character
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
		long time = 0L;
		try {
			time = Long.parseLong(vTime.substring(0, indexOfUnit).trim());
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

	/**
	 * Extracts the paths of the specified groups into a single list.
	 * Iterates through a list of {@link GroupRepresentation} objects, extracting the path of each group
	 * and adding it to a result list. If the {@code recursive} parameter is set to {@code true}, it recursively
	 * flattens the paths of all subgroups as well, adding their paths to the list.
	 *
	 * @param groupRepresentations the list of {@link GroupRepresentation} objects to be flattened
	 * @param recursive if {@code true}, the method will also include paths of subgroups recursively
	 * @return {@link List} of {@link String} containing the paths of all groups (and optionally subgroups)
	 */
	public static List<String> extractGroupPaths(List<GroupRepresentation> groupRepresentations, Boolean recursive) {
		// Create an empty list to store the group paths in
		List<String> groupPaths = new ArrayList<>();

		if (groupRepresentations != null) {
			// Iterate through each group representation in the list and extract the path
			for (GroupRepresentation groupRepresentation : groupRepresentations) {
				groupPaths.add(groupRepresentation.getPath());

				// Recursively retrieve the paths of all subgroups and add them to the list if indicated
				if (recursive && groupRepresentation.getSubGroups() != null && !groupRepresentation.getSubGroups().isEmpty()) {
					groupPaths.addAll(extractGroupPaths(groupRepresentation.getSubGroups(), true));
				}
			}
		}

		return groupPaths;
	}

	/**
	 * Flattens the group structures into a group IDs-to-group paths-mapping.
	 * This method traverses a list of {@link GroupRepresentation} objects and creates a map where each entry
	 * maps a group's ID to its path. If the {@code recursive} parameter is set to {@code true}, the method
	 * will also include all subgroups' IDs and paths.
	 *
	 * @param groupRepresentations the list of {@link GroupRepresentation} objects to be processed
	 * @param recursive if {@code true}, the method will include IDs and paths of subgroups recursively
	 * @return {@link Map} with group IDs as keys and group paths as values
	 */
	public static Map<String, String> flattenGroupIDToPathMapping(List<GroupRepresentation> groupRepresentations, Boolean recursive) {
		// Create an empty map to store the group ID and path mappings
		Map<String, String> map = new HashMap<>();

		if (groupRepresentations != null) {
			// Iterate through each group representation and add the current group's ID and path to the map
			for (GroupRepresentation groupRepresentation : groupRepresentations) {
				map.put(groupRepresentation.getId(), groupRepresentation.getPath());

				// Recursively flatten subgroups and add them to the map if indicated
				if (recursive && groupRepresentation.getSubGroups() != null && !groupRepresentation.getSubGroups().isEmpty()) {
					map.putAll(flattenGroupIDToPathMapping(groupRepresentation.getSubGroups(), true));
				}
			}
		}

		return map;
	}

	/**
	 * Retrieves an entry from the group IDs-to-paths-mapping, which matches a specified path.
	 *
	 * @param flatGroups a map where keys are group IDs and values are group paths
	 * @param path the group path to search for within the map
	 * @return {@link Map.Entry} containing the group ID and path that matches the specified path, or {@code null} if no match is found
	 */
	public static Map.Entry<String, String> findGroupEntryByPath(Map<String, String> flatGroups, String path) {
		// Iterate over each entry in the map
		for (Map.Entry<String, String> entry : flatGroups.entrySet()) {
			// Check if the value (group path) matches the specified path, return if found
			if (entry.getValue().equals(path)) {
				return entry;
			}
		}

		// Return null if no matching path is found
		return null;
	}
	
	/**
	 * Method to generate the alphabet depending on the given algorithm.
	 * 
	 * @param algorithm the user-given algorithm
	 * @param alphabet the alphabet provided by the user, if available
	 * @return the alphabet that matches the algorithm as a String
	 */
	public static String generateAlphabet(String algorithm, String alphabet) {
		// The possible alphabets
		String HEXADECIMAL_ALPHABET = "ABCDEF0123456789";
		String NUMBERS_ONLY_ALPHABET = "0123456789";
		String LETTERS_ONLY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		String LETTERS_AND_NUMBERS_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		String LETTERS_AND_NUMBERS_WITHOUT_BIOS_ALPHABET = "ACDEFGHJKLMNPQRTUVWXYZ0123456789";
		
		// If nothing was provided, return the default alphabet
		if (algorithm == null) {
			return DEFAULT_PSEUDONYMIZATION_ALPHABET;
		}
		
		// Generate alphabet depending on the used algorithm
		switch (algorithm.trim().toUpperCase()) {
	        case "MD5":
	        case "SHA1":
	        case "SHA2":
	        case "SHA3":
	        case "BLAKE3":
	        case "XXHASH": {
	        	return HEXADECIMAL_ALPHABET;
	        }
	        case "CONSECUTIVE":
	        case "RANDOM_NUM": {
	        	return NUMBERS_ONLY_ALPHABET;
	        }
	        case "RANDOM": {
	        	// If "RANDOM" was selected, use the user-provided alphabet or A-Z0-9 if nothing was provided
	        	return (alphabet != null && !alphabet.isBlank()) ? alphabet : LETTERS_AND_NUMBERS_ALPHABET;
	        }
	        case "RANDOM_HEX": {
	        	return HEXADECIMAL_ALPHABET;
	        }
	        case "RANDOM_LET": {
	        	return LETTERS_ONLY_ALPHABET;
	        }
	        case "RANDOM_SYM": {
	        	return LETTERS_AND_NUMBERS_ALPHABET;
	        }
	        case "RANDOM_SYM_BIOS": {
	        	return LETTERS_AND_NUMBERS_WITHOUT_BIOS_ALPHABET;
	        }
	        default: {
	            // Unrecognized algorithm
	            log.debug("The pseudonymization algorithm that was requested (" + algorithm + ") wasn't recognized.");
	            return DEFAULT_PSEUDONYMIZATION_ALPHABET;
	        }
		}
	}
}