/*
 * Trust Deck Services
 * Copyright 2023-2024 Armin Müller & Eric Wündisch
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

package org.trustdeck.ace.algorithms;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

import org.trustdeck.ace.jooq.generated.tables.pojos.Domain;
import org.trustdeck.ace.utils.Assertion;

/**
 * This class allows for finding a path between two arbitrary domains, if there is one.
 *
 * @author Eric Wündisch & Armin Müller
 */
@Slf4j
public class PathFinder {

	/** A list of domain objects and their corresponding IDs. */
    private Map<Integer, Domain> nodeMapByID;
    
    /** A list of domain objects and their corresponding name. */
    private Map<String, Domain> nodeMapByName;

    /**
     * Constructor to instantiate a new domain tree.
     *
     * @param nodes a list of the tree's nodes
     */
    public PathFinder(List<Domain> nodes) {
        nodeMapByID = new HashMap<>();
        nodeMapByName = new HashMap<>();
        
        // Pre-fill the maps to use them later
        for (Domain node : nodes) {
            nodeMapByID.put(node.getId(), node);
            nodeMapByName.put(node.getName(), node);
        }
    }

    /**
     * Finds the path between two domains in the domain tree.
     *
     * @param sourceName the name of the source domain
     * @param destName the name of the destination domain
     * @return the path from the source domain to the destination domain as a list of domain nodes, or {@code null} if nothing was found
     */
    public List<Domain> getPath(String sourceName, String destName) {
        Domain start = nodeMapByName.get(sourceName);
        Domain end = nodeMapByName.get(destName);
        List<Domain> path = new ArrayList<>();
        Set<Domain> visited = new HashSet<Domain>();
        
        if (!Assertion.assertNotNullAll(start, end)) {
        	log.debug("The start or the end node were not found in the list of provided nodes.");
        	return null;
        }
        
        dfs(start, end, path, visited);
        
        return path;
    }

    /**
     * Depth-first search.
     *
     * @param current the node currently inspected by the DFS
     * @param end the destination node
     * @param path the list of nodes that represent the path
     * @param visited the set of nodes that were already checked by the DFS
     * @return {@code true} when a path from source node to destination node was found, 
     * 			{@code false} when no path was found. The corresponding path is stored 
     * 			in the {@code path} parameter.
     */
    private boolean dfs(Domain current, Domain end, List<Domain> path, Set<Domain> visited) {
        // Breadth-first search (BFS), Dijkstra's algorithm, and A* search algorithm are also possible.

    	visited.add(current);
        path.add(current);
        
        // Check if we are done
        if (current.equals(end)) {
            return true;
        }
        
        // Retrieve child-domains and recursively start DFS-ing through them
        for (Domain child : getChildren(current)) {
            if (!visited.contains(child)) {            	
                if (dfs(child, end, path, visited)) {
                    return true;
                }
            }
        }
        
        // When the destination was not found yet, try finding it by starting with the parent-node
        Domain parent = nodeMapByID.get(current.getSuperdomainid());
        if (parent != null && !visited.contains(parent)) {
            if (dfs(parent, end, path, visited)) {
                return true;
            }
        }
        
        // The path to the destination wasn't found. Remove the current node from the path again.
        path.remove(current);
        return false;
    }

    /**
     * Returns the children of this node.
     *
     * @param domain the domain that should be checked for its children
     * @return a list of domains that are children of the given one
     */
    private List<Domain> getChildren(Domain domain) {
        List<Domain> children = new ArrayList<>();
        
        for (Domain node : nodeMapByID.values()) {
            if (domain.getId().equals(node.getSuperdomainid())) {
                children.add(node);
            }
        }
        
        return children;
    }
}