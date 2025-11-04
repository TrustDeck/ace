/*
 * Trust Deck Services
 * Copyright 2024-2025 Armin Müller
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

package org.trustdeck.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.JSONB;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.trustdeck.utils.LRUCache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * Class used to handle the entity type's JSON schemas and their validation.
 * Includes a simple LRU caching for compiled schemas.
 * 
 * @author Armin Müller
 */
@Service
@Slf4j
public class JsonSchemaService {

	/** A mapper for transforming JSONB into JsonNode and back. */
	private ObjectMapper om;

	/** The schema factory used for building the schemas used for validating entity types and instances. */
	private JsonSchemaFactory factory;

	/** Stores (caches) the meta schema used to validate user-provided schemas. */
	private JsonSchema metaSchema;
	
	/** Least recently used cache for the compiled type schemas. */
	private final Map<String, JsonSchema> compiledCache = Collections.synchronizedMap(new LRUCache<>(50));

	/**
	 * Constructor that defines the object mapper and initializes the schema factory
	 * and the meta schema.
	 * 
	 * @param om the object mapper to map from the meta schema file to a JsonNode (will be auto injected by spring boot)
	 */
	public JsonSchemaService(ObjectMapper om) {
		this.om = om;
		this.factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

		// Load the meta schema from file into the application
		try (InputStream metaSchema = new ClassPathResource("schemas/definition-meta-schema.json").getInputStream()) {
			// Map the input stream to the proper type
			JsonNode meta = om.readTree(metaSchema);
			this.metaSchema = factory.getSchema(meta);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load definition meta-schema.", e);
		}
	}

	/**
	 * Validates the given definition.
	 * 
	 * @param definition the schema definition
	 * @return a list of human-readable errors (an empty list means its valid)
	 */
	public List<String> validateDefinition(JsonNode definition) {
		// Validate the given schema and collect all messages (errors)
		Set<ValidationMessage> msgs = metaSchema.validate(definition);

		// Check if there are any messages in the set
		if (!msgs.isEmpty()) {
			return msgs.stream().map(ValidationMessage::getMessage).collect(Collectors.toList());
		}

		// Check additional constraints that cannot (easily) be defined in the schema
		List<String> errors = new ArrayList<>();
		Set<String> seenNames = new HashSet<>();

		// Iterate over the nodes in the schema definition (which itself is also a JSON)
		for (JsonNode attr : definition.get("attributes")) {
			String name = attr.get("name").asText();
			String type = attr.get("type").asText();

			// Check for duplicated attribute names
			if (!seenNames.add(name)) {
				log.trace("Found a duplicated attribute name in the JSON Schema: " + name);
				errors.add("Duplicated attribute name: \"" + name + "\".");
			}

			// Ensure that there are no string-constraints used on non-string fields
			if (!"string".equalsIgnoreCase(type)) {
				// Non-string type
				if (attr.has("minLength")) {
					errors.add("minLength only valid for string (attribute \"" + name + "\").");
				}

				if (attr.has("maxLength")) {
					errors.add("maxLength only valid for string (attribute \"" + name + "\").");
				}

				if (attr.has("pattern")) {
					errors.add("pattern only valid for string (attribute \"" + name + "\").");
				}
			}
		}

		return errors;
	}

	/**
	 * Build an instance JSON schema from a validated definition in JsonNode form 
	 * that can be used for validating entity instances.
	 * 
	 * @param definition the validated schema definition
	 * @return an instance JSON schema
	 */
	public JsonNode buildInstanceSchema(JsonNode definition) {
		// Build schema object from the JsonNode type
		ObjectNode schema = om.createObjectNode();
		schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
		schema.put("$id",
				"urn:schema:" + definition.get("typeName").asText() + ":" + definition.get("version").asText());
		schema.put("type", "object");
		schema.put("additionalProperties", false);

		// Add entries for the attributes into the schema
		ObjectNode properties = om.createObjectNode();
		ArrayNode required = om.createArrayNode();

		// Iterate over all attributes in the given definition and create a new
		// schema-attribute
		for (JsonNode defAttribute : definition.get("attributes")) {
			String name = defAttribute.get("name").asText();
			String type = defAttribute.get("type").asText();

			// Set the attributes\"s type
			ObjectNode attribute = om.createObjectNode();
			switch (type) {
			case "string": {
				attribute.put("type", "string");
				break;
			}
			case "integer": {
				attribute.put("type", "integer");
				break;
			}
			case "number": {
				attribute.put("type", "number");
				break;
			}
			case "boolean": {
				attribute.put("type", "boolean");
				break;
			}
			case "date": {
				attribute.put("type", "string");
				attribute.put("format", "date");
				break;
			}
			case "datetime": {
				attribute.put("type", "string");
				attribute.put("format", "date-time");
				break;
			}
			default:
				throw new IllegalArgumentException("Unsupported type: " + type);
			}

			// Add constraints to the attribute if there are any in the original definition
			copyIfPresent(defAttribute, attribute, "minimum");
			copyIfPresent(defAttribute, attribute, "maximum");
			copyIfPresent(defAttribute, attribute, "minLength");
			copyIfPresent(defAttribute, attribute, "maxLength");
			copyIfPresent(defAttribute, attribute, "pattern");
			copyIfPresent(defAttribute, attribute, "enum");

			// Store the new attribute in the properties nodes
			properties.set(name, attribute);

			// Add the attribute to the list of required attributes if needed
			if (defAttribute.get("required").asBoolean()) {
				required.add(name);
			}
		}

		// Add the property-nodes to the schema
		schema.set("properties", properties);

		// Add required flags if there are any
		if (required.size() > 0) {
			schema.set("required", required);
		}

		return schema;
	}
	
	/**
	 * Build an instance JSON schema from a validated definition in JSONB form 
	 * that can be used for validating entity instances.
	 * 
	 * @param definition the validated schema definition
	 * @return an instance JSON schema
	 */
	public JsonNode buildInstanceSchema(JSONB definition) {
		JsonNode schema;
		try {
			schema = om.readTree(definition.toString());
		} catch (JsonProcessingException e) {
			log.debug("Failed to parse JSONB into JsonNode while building the instance schema.", e);
			return null;
		}
		
		return buildInstanceSchema(schema);
	}

	/**
	 * Ensure the project specific type definition (projectDef) is a superset of a
	 * base type (baseDef):
	 * <ul>
	 *   <li>Contains all base attributes (no deletions)</li>
	 *   <li>Same type for shared attributes</li>
	 *   <li>Constraints can only be stricter, never weaker:
	 *     <ul>
	 *       <li>numbers: min >= base.min; max <= base.max</li>
	 *       <li>strings: minLength >= base.minLength; maxLength <= base.maxLength</li>
	 *       <li>pattern: require equality to base type</li>
	 *     </ul>
	 *   </li>
	 * </ul>
	 * 
	 * @param baseDef the type definition of the base type
	 * @param projectDef the type definition for the project-specific type
	 * @return a list of errors encountered during validation
	 */
	public List<String> validateProjectTypeIsSuperset(JsonNode baseDef, JsonNode projectDef) {
		List<String> errors = new ArrayList<>();

		// Map attributes by name
		Map<String, JsonNode> baseAttributes = new HashMap<>();
		for (JsonNode attribute : baseDef.get("attributes")) {
			baseAttributes.put(attribute.get("name").asText(), attribute);
		}

		Map<String, JsonNode> projectAttributes = new HashMap<>();
		for (JsonNode attribute : projectDef.get("attributes")) {
			projectAttributes.put(attribute.get("name").asText(), attribute);
		}

		// Ensure that there are no attribute deletions: project must contain all base attributes
		for (String name : baseAttributes.keySet()) {
			if (!projectAttributes.containsKey(name)) {
				errors.add("Project specific type definition is missing base attribute \"" + name + "\".");
			}
		}
		
		if (!errors.isEmpty())
			return errors;

		// For shared attributes, ensure type equality, constraints not weaker
		for (String attributeName : baseAttributes.keySet()) {
			JsonNode b = baseAttributes.get(attributeName);
			JsonNode p = projectAttributes.get(attributeName);

			// Check if the types are identical
			String bType = b.get("type").asText();
			String pType = p.get("type").asText();
			
			if (!bType.equals(pType)) {
				errors.add("Attribute \"" + attributeName + "\" changes type from \"" + bType + "\" to \"" + pType + "\".");
				continue;
			}

			// Ensure string constraints are kept
			if (bType.equals("string")) {
				// Ensure regex patterns are kept
				if (b.has("pattern")) {
					if (!p.has("pattern")) {
						errors.add("Attribute \"" + attributeName + "\": base has a pattern; project must keep the same pattern.");
					} else if (!b.get("pattern").asText().equals(p.get("pattern").asText())) {
						errors.add("Attribute \"" + attributeName + "\": project pattern must equal base pattern (\"" + b.get("pattern").asText() + "\").");
					}
				}
			}
		}

		return errors;
	}

	/**
	 * Validate an instance JSON against an instance schema.
	 * 
	 * @param instance the instance as a JsonNode
	 * @param instanceSchema the schema to evaluate against
	 * @return a list of validation errors (an empty list means its valid)
	 */
	public List<String> validateInstance(JsonNode instance, JsonNode instanceSchema) {
		// Compile schema so it can be used
		JsonSchema compiled = factory.getSchema(instanceSchema);

		// Validate and return possible error messages to the caller
		Set<ValidationMessage> msgs = compiled.validate(instance);
		return msgs.stream().map(ValidationMessage::getMessage).collect(Collectors.toList());
	}

	/**
	 * Validate an instance JSONB against an instance schema.
	 * 
	 * @param instanceData the instance as a JSONB
	 * @param instanceSchema the schema to evaluate against
	 * @return a list of validation errors (an empty list means its valid)
	 */
	public List<String> validateInstance(JSONB instanceData, JsonNode instanceSchema) {
		// Parse instance data
		JsonNode instance;
		try {
			instance = om.readTree(instanceData.toString());
		} catch (JsonProcessingException e) {
			log.debug("Failed to parse JSONB into JsonNode while validating the instance payload.", e);
			return null;
		}
		
		return validateInstance(instance, instanceSchema);
	}

	/**
	 * Validate an instance JSONB against a previously compiled instance schema.
	 * 
	 * @param instanceData the instance as a JSONB
	 * @param compiledInstanceSchema the already compiled schema to evaluate against
	 * @return a list of validation errors (an empty list means its valid)
	 */
	public List<String> validateInstance(JSONB instanceData, JsonSchema compiledInstanceSchema) {
		// Parse instance data
		JsonNode instance;
		try {
			instance = om.readTree(instanceData.toString());
		} catch (JsonProcessingException e) {
			log.debug("Failed to parse JSONB into JsonNode while validating the instance payload.", e);
			return List.of("Malformed JSON payload: " + e.getMessage());
		}
	
		// Validate and return any encountered errors
		Set<ValidationMessage> msgs = compiledInstanceSchema.validate(instance);
        return msgs.stream().map(ValidationMessage::getMessage).collect(Collectors.toList());
	}

	/**
	 * Validate an instance JsonNode against a previously compiled instance schema.
	 * 
	 * @param data the JSON data as a JsonNode
	 * @param compiledInstanceSchema the already compiled schema to evaluate against
	 * @return a list of validation errors (an empty list means its valid)
	 */
	public List<String> validateInstance(JsonNode data, JsonSchema compiledInstanceSchema) {
		// Validate and return any encountered errors
		Set<ValidationMessage> msgs = compiledInstanceSchema.validate(data);
        return msgs.stream().map(ValidationMessage::getMessage).collect(Collectors.toList());
	}

	/**
	 * Build or reuse a compiled schema for an entity type (build
	 * from its definition or get from LRU-cached).
	 * Convenience method that allows using the JSONB directly.
	 * 
	 * @param typeDefinition the type definition
	 * @return the compiled schema
	 */
	public JsonSchema getCompiledSchemaFromDefinition(JSONB typeDefinition) {
		// Parse the JSONB
		JsonNode def;
		try {
			def = om.readTree(typeDefinition.toString());
		} catch (JsonProcessingException e) {
			log.debug("Failed to parse JSONB into JsonNode while retrieving the compiled schema.", e);
			return null;
		}
		
		return getCompiledSchemaFromDefinition(def);
	}

	/**
	 * Build or reuse a compiled schema for an entity type (build
	 * from its definition or get from LRU-cached).
	 * 
	 * @param typeDefinition the type definition
	 * @return the compiled schema
	 */
	public JsonSchema getCompiledSchemaFromDefinition(JsonNode typeDefinition) {
		boolean useCache = true;
		
		// Build the schema from the definition
		JsonNode instanceSchemaNode = buildInstanceSchema(typeDefinition);
		
		// Canonicalize the schema and hash it
		byte[] canonical = canonicalize(instanceSchemaNode);
		String key = null;
		try {
			key = sha256Base64(canonical);
		} catch (IllegalStateException e) {
			log.debug("Failed to compute hash, so caching is unavailable. Compile instead.");
			useCache = false;
		}

		if (useCache) {
			log.trace("Checking cache for compiled schema.");
		
			// Check the (locked) cache for an already compiled schema
			JsonSchema cached;
			synchronized (compiledCache) {
				cached = compiledCache.get(key);
			}
			if (cached != null) {
				log.trace("Cache hit: using compiled schema from cache.");
				return cached;
			} else {
				log.trace("Cache miss: compile schema and cache it.");
			}
		}

		// No cached schema found or no cache used: compile the schema
		log.trace("Compiling schema ...");
		JsonSchema compiled = factory.getSchema(instanceSchemaNode);

		if (useCache) {
			// Cache the newly compiled schema (use double-checked locking)
			synchronized (compiledCache) {
				JsonSchema existing = compiledCache.get(key);
				if (existing != null) {
					// The schema was cached in the meantime; return it
					return existing;
				}
				
				// Add to cache
				log.trace("Added compiled schema to cache.");
				compiledCache.put(key, compiled);
			}
		}
		
		return compiled;
	}

	/**
	 * Ensures the JSON is always written in a consistent way by sorting
	 * object keys alphabetically before converting it to bytes.
	 * 
	 * @param node the JSON to canonicalize
	 * @return a byte array representing the canonicalized form of the given JSON
	 */
	private byte[] canonicalize(JsonNode node) {
		try {
			ObjectMapper omCopy = om.copy();
			omCopy.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
			
			return omCopy.writeValueAsBytes(node);
		} catch (IOException e) {
			// Fallback to String
			return node.toString().getBytes(StandardCharsets.UTF_8);
		}
	}

	/**
	 * Computes the SHA-256 hash of the given byte 
	 * array and returns it as a Base64 string.
	 * 
	 * @param data the input data to hash
	 * @return the Base64 encoded SHA-256 hash of the input
	 * @throws IllegalStateException if the SHA-256 algorithm is not available on the system
	 */
	private static String sha256Base64(byte[] data) throws IllegalStateException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available.", e);
		}
	}

	/**
	 * Helper method that copies optional constraint keywords from an attribute
	 * definition node into the generated instance-schema property node, but only if
	 * the keyword exists. Copying is done by call-by-reference.
	 * 
	 * @param from the attribute definition node
	 * @param to the generated instance-schema property node
	 * @param field the optional constraint keyword (e.g., minimum, maximum, enum, ...)
	 */
	private static void copyIfPresent(JsonNode from, ObjectNode to, String field) {
		if (from.has(field)) {
			to.set(field, from.get(field));
		}
	}
}
