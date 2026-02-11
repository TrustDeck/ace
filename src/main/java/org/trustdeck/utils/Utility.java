/*
 * Trust Deck Services
 * Copyright 2022-2026 Armin Müller and Eric Wündisch
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

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

/**
 * This class offers a variety of utilities.
 *
 * @author Armin Müller and Eric Wündisch
 */
@Slf4j
@Component
public class Utility {
    
    /** The default alphabet (A-Z0-9) used for the pseudonymization process. */
    private static final String DEFAULT_PSEUDONYMIZATION_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    /** A flexible date-time-formatter that accepts a multitude of input variants. */
    private static final DateTimeFormatter FLEXIBLE_DATE_TIME_FORMATTER = buildFormatter();
	
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

    /**
     * Method to build a flexible date time formatter that accepts date-times such as:
     * <li>2025-12-31</li>
     * <li>2025-12-31 12:00</li>
     * <li>2025-12-31 12:00:30</li>
     * <li>2025-12-31T12:00</li>
     * <li>2025-12-31T12:00:30</li>
     * <li>... with offsets: Z, +02:00, etc.</li>
     * 
     * @return
     */
	private static DateTimeFormatter buildFormatter() {
		return new DateTimeFormatterBuilder()
		        .parseCaseInsensitive()
		        .append(DateTimeFormatter.ISO_LOCAL_DATE)
		        // Time with 'T' separator
		        .optionalStart()
		            .appendLiteral('T')
		            .append(DateTimeFormatter.ISO_LOCAL_TIME)
		        .optionalEnd()
		        // Time with space separator
		        .optionalStart()
		            .appendLiteral(' ')
		            .append(DateTimeFormatter.ISO_LOCAL_TIME)
		        .optionalEnd()
		        // Optional offset (Z or +HH:mm) after the time
		        .optionalStart()
		            .appendOffset("+HH:MM", "Z")
		        .optionalEnd()
		        .toFormatter(Locale.ROOT)
		        .withResolverStyle(ResolverStyle.SMART);
	}
	
	/**
	 * Method to parse a string in an unknown date-time format into
	 * the OffsetDateTime format.
	 * 
	 * @param dateTime the date time String
	 * @return the given date-time as an instance of OffsetDateTime, or {@code null} if no known format was detected
	 */
	public static OffsetDateTime parseDateTimeString(String dateTime) {
        // Check if the input is given
		if (Assertion.isNullOrEmpty(dateTime)) {
        	return null;
        }
		
		// Use the system time zone when necessary for conversion/parsing
        ZoneId timeZone = ZoneId.systemDefault();
        
        // Try best-fit parsing: OffsetDateTime, ZonedDateTime, LocalDateTime, LocalDate
        try {
            TemporalAccessor ta = FLEXIBLE_DATE_TIME_FORMATTER.parseBest(
            		dateTime.trim(),
                    OffsetDateTime::from,
                    ZonedDateTime::from,
                    LocalDateTime::from,
                    LocalDate::from
            );

            if (ta instanceof OffsetDateTime odt) {
            	log.trace("Given input date time is of type " + OffsetDateTime.class.getName() + ": " + odt.toString());
                return odt;
            } else if (ta instanceof ZonedDateTime zdt) {
            	log.trace("Given input date time is of type " + ZonedDateTime.class.getName() + ": " + zdt.toString());
                return zdt.toOffsetDateTime();
            } else if (ta instanceof LocalDateTime ldt) {
            	log.trace("Given input date time is of type " + LocalDateTime.class.getName() + ": " + ldt.toString());
                return ldt.atZone(timeZone).toOffsetDateTime();
            } else if (ta instanceof LocalDate ld) {
            	log.trace("Given input date time is of type " + LocalDate.class.getName() + ": " + ld.toString());
                return ld.atStartOfDay(timeZone).toOffsetDateTime();
            } else {
            	log.trace("Could not detect type of the given input string.");
            	return null;
            }
        } catch (DateTimeParseException e) {
            log.debug("No valid parser option for the given date \"" + dateTime + "\" was found.");
            return null;
        }
    }
	
	public record Pair<A, B>(A first, B second) {}
}