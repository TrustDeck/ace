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

import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

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

	/**
	 * Removes duplicate strings from a given list.
	 * <p>
	 * This method converts a list of strings into a {@link Set}, which automatically removes any duplicate
	 * entries. It then converts the set back into a new list, preserving only unique elements.
	 * </p>
	 *
	 * @param l The list of strings that may contain duplicates.
	 * @return {@link List} of unique strings without duplicates.
	 */
	public static List<String> simpleDeduplicate(List<String> l) {
		// Convert the input list into a Set, which removes duplicates.
		Set<String> set = new HashSet<>(l);

		// Convert the Set back into a List and return it.
		return new ArrayList<>(set);
	}


	/**
	 * Flattens the paths of the specified groups into a single list.
	 * <p>
	 * This method iterates through a list of {@link GroupRepresentation} objects, extracting the path of each group
	 * and adding it to a result list. If the {@code recursive} parameter is set to {@code true}, it recursively
	 * flattens the paths of all sub-groups as well, adding their paths to the list.
	 * </p>
	 *
	 * @param groupRepresentations The list of {@link GroupRepresentation} objects to be flattened.
	 * @param recursive If {@code true}, the method will also include paths of sub-groups recursively.
	 * @return {@link List} of {@link String} containing the paths of all groups (and optionally sub-groups).
	 */
	public static List<String> simpleFlatGroupPaths(List<GroupRepresentation> groupRepresentations, Boolean recursive) {
		// Create an empty list to store the group paths.
		List<String> groupPaths = new ArrayList<>();

		// Check if the input list is not null.
		if (groupRepresentations != null) {
			// Iterate through each group representation in the list.
			for (GroupRepresentation groupRepresentation : groupRepresentations) {
				// Add the path of the current group to the result list.
				groupPaths.add(groupRepresentation.getPath());

				// If recursive is true, and there are sub-groups, process them as well.
				if (recursive && groupRepresentation.getSubGroups() != null && !groupRepresentation.getSubGroups().isEmpty()) {
					// Recursively retrieve the paths of all sub-groups and add them to the list.
					List<String> subGroupPaths = Utility.simpleFlatGroupPaths(groupRepresentation.getSubGroups(), true);
					groupPaths.addAll(subGroupPaths);
				}
			}
		}

		// Return the flattened list of group paths.
		return groupPaths;
	}


	/**
	 * Flattens the group structures into a map of group IDs to group paths.
	 * <p>
	 * This method traverses a list of {@link GroupRepresentation} objects and creates a map where each entry
	 * maps a group's ID to its path. If the {@code recursive} parameter is set to {@code true}, the method
	 * will also include all sub-groups' IDs and paths.
	 * </p>
	 *
	 * @param groupRepresentations The list of {@link GroupRepresentation} objects to be processed.
	 * @param recursive If {@code true}, the method will include IDs and paths of sub-groups recursively.
	 * @return {@link Map} with group IDs as keys and group paths as values.
	 */
	public static Map<String, String> flatGroups(List<GroupRepresentation> groupRepresentations, Boolean recursive) {
		// Create an empty map to store the group ID and path mappings.
		Map<String, String> map = new HashMap<>();

		// Check if the input list is not null.
		if (groupRepresentations != null) {
			// Iterate through each group representation.
			for (GroupRepresentation groupRepresentation : groupRepresentations) {
				// Add the current group's ID and path to the map.
				map.put(groupRepresentation.getId(), groupRepresentation.getPath());

				// If recursive is true, and the group has sub-groups, process them as well.
				if (recursive && groupRepresentation.getSubGroups() != null && !groupRepresentation.getSubGroups().isEmpty()) {
					// Recursively flatten sub-groups and add them to the map.
					Map<String, String> tempMap = Utility.flatGroups(groupRepresentation.getSubGroups(), true);
					map.putAll(tempMap);
				}
			}
		}

		// Return the flattened map of group IDs to paths.
		return map;
	}


	/**
	 * Retrieves an entry from the map of group IDs to paths that matches a specified path.
	 * <p>
	 * This method searches through a map of group IDs to paths and returns the first entry whose value (path)
	 * matches the specified path. If no such entry is found, it returns {@code null}.
	 * </p>
	 *
	 * @param flatGroups A map where keys are group IDs and values are group paths.
	 * @param path The group path to search for within the map.
	 * @return {@link Map.Entry} containing the group ID and path that matches the specified path, or {@code null} if no match is found.
	 */
	public static Map.Entry<String, String> getMatchByPath(Map<String, String> flatGroups, String path) {
		// Iterate through each entry in the map.
		for (Map.Entry<String, String> entry : flatGroups.entrySet()) {
			// Check if the value (group path) matches the specified path.
			if (entry.getValue().equals(path)) {
				// Return the matching entry if found.
				return entry;
			}
		}

		// Return null if no matching path is found.
		return null;
	}


}