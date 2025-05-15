package com.mucifex.pathfinding.internal.pathfind.main.astar;

import net.minecraft.util.BlockPos;

import java.util.*;

public class AStarPathFinder {
    // Maximum number of consecutive non-productive iterations before aborting
    private static final int MAX_CONSECUTIVE_NON_PRODUCTIVE_ITERATIONS = 1000;
    // Maximum allowed distance from the start position
    private static final int MAX_ALLOWED_DISTANCE = 200;
    // Distance threshold for early success detection
    private static final double CLOSE_ENOUGH_DISTANCE = 2.0;
    
    public static List<AStarNode> compute(BlockPos start, BlockPos end, int depth) {
        PriorityQueue<AStarNode> openQueue = new PriorityQueue<>(Comparator.comparingDouble(AStarNode::getTotalCost));
        // Using HashSet instead of ArrayList for closed list for O(1) lookups
        Set<BlockPos> closedSet = new HashSet<>();
        // Track best nodes by position for quick replacement
        Map<BlockPos, AStarNode> openSet = new HashMap<>();

        AStarNode endNode = new AStarNode(end);
        AStarNode startNode = new AStarNode(start, endNode);
        int startX = start.getX();
        int startY = start.getY();
        int startZ = start.getZ();

        // Add start node
        openQueue.add(startNode);
        openSet.put(start, startNode);
        
        // Track consecutive non-productive iterations
        int nonProductiveIterations = 0;
        
        // Store best node distance to target
        double bestDistanceToTarget = Double.MAX_VALUE;
        AStarNode bestNode = startNode;

        for(int i = 0; i < depth; i++) {
            if(openQueue.isEmpty()) {
                // If no path found but we have a best node, return path to it
                if (bestNode != startNode) {
                    System.out.println("DEBUG: No complete path found, returning best partial path");
                    return getPath(bestNode);
                }
                return new ArrayList<>();
            }

            AStarNode currentNode = openQueue.poll();
            BlockPos currentPos = new BlockPos(currentNode.getX(), currentNode.getY(), currentNode.getZ());
            openSet.remove(currentPos);
            
            // Add to closed set
            closedSet.add(currentPos);

            // Check if we've reached the target or are close enough
            if(currentNode.equals(endNode)) {
                System.out.println("DEBUG: Path found in " + i + " iterations");
                return getPath(currentNode);
            }
            
            // Calculate distance to target for early termination check
            int dx = currentNode.getX() - end.getX();
            int dy = currentNode.getY() - end.getY();
            int dz = currentNode.getZ() - end.getZ();
            double distanceToTarget = Math.sqrt(dx*dx + dy*dy + dz*dz);
            
            // Early success: if we're close enough to target, consider it a success
            // This helps prevent excessive node expansion in the final approach
            if (distanceToTarget <= CLOSE_ENOUGH_DISTANCE) {
                System.out.println("DEBUG: Close enough to target (" + distanceToTarget + " blocks), returning path");
                return getPath(currentNode);
            }
            
            // Update best node if this is closer to target
            if (distanceToTarget < bestDistanceToTarget) {
                bestDistanceToTarget = distanceToTarget;
                bestNode = currentNode;
                nonProductiveIterations = 0; // Reset counter as we're making progress
            } else {
                nonProductiveIterations++;
                
                // Early termination if we've been stuck for too long
                if (nonProductiveIterations >= MAX_CONSECUTIVE_NON_PRODUCTIVE_ITERATIONS) {
                    System.out.println("DEBUG: Early termination due to lack of progress after " + nonProductiveIterations + " iterations");
                    return getPath(bestNode);
                }
            }
            
            // Check if we've gone too far from starting position (prevents runaway paths)
            int distX = currentNode.getX() - startX;
            int distY = currentNode.getY() - startY;
            int distZ = currentNode.getZ() - startZ;
            double distanceFromStart = Math.sqrt(distX*distX + distY*distY + distZ*distZ);
            
            if (distanceFromStart > MAX_ALLOWED_DISTANCE) {
                System.out.println("DEBUG: Early termination - path exceeds maximum allowed distance");
                return getPath(bestNode);
            }

            // Process neighbors more efficiently
            populateNeighbours(openQueue, openSet, closedSet, currentNode, startNode, endNode);
        }

        // If we've exhausted the depth but didn't reach the target, return best partial path
        System.out.println("DEBUG: Path depth exceeded, returning best partial path");
        return getPath(bestNode);
    }

    private static void populateNeighbours(PriorityQueue<AStarNode> openQueue, Map<BlockPos, AStarNode> openSet, Set<BlockPos> closedSet, AStarNode current, AStarNode startNode, AStarNode endNode) {
        List<AStarNode> neighbours = new ArrayList<>();
        
        // Cardinal directions (N, E, S, W)
        neighbours.add(new AStarNode(-1, 0, 0, current, endNode));  // West
        neighbours.add(new AStarNode(1, 0, 0, current, endNode));   // East
        neighbours.add(new AStarNode(0, 0, -1, current, endNode));  // North
        neighbours.add(new AStarNode(0, 0, 1, current, endNode));   // South
        
        // Diagonal directions (NE, SE, SW, NW) - Added for more natural movement
        neighbours.add(new AStarNode(1, 0, -1, current, endNode));  // Northeast
        neighbours.add(new AStarNode(1, 0, 1, current, endNode));   // Southeast
        neighbours.add(new AStarNode(-1, 0, 1, current, endNode));  // Southwest
        neighbours.add(new AStarNode(-1, 0, -1, current, endNode)); // Northwest
        
        // Vertical movement
        neighbours.add(new AStarNode(0, 1, 0, current, endNode));   // Up
        neighbours.add(new AStarNode(0, -1, 0, current, endNode));  // Down
        
        // Calculate vector to end node for prioritizing movement toward target
        int dx = endNode.getX() - current.getX();
        int dz = endNode.getZ() - current.getZ();
        int dy = endNode.getY() - current.getY();
        
        // Sort neighbors to prioritize those closer to the target and in open areas
        Collections.sort(neighbours, (a, b) -> {
            // Calculate direction vectors for both nodes
            int axRel = a.getX() - current.getX();
            int azRel = a.getZ() - current.getZ();
            int ayRel = a.getY() - current.getY();
            
            int bxRel = b.getX() - current.getX();
            int bzRel = b.getZ() - current.getZ();
            int byRel = b.getY() - current.getY();
            
            // Calculate dot products of movement direction and target direction
            // Higher values mean the movement is more aligned with the target direction
            double aDot = (axRel * dx + azRel * dz + ayRel * dy) / 
                    (Math.sqrt(axRel*axRel + azRel*azRel + ayRel*ayRel) * 
                     Math.sqrt(dx*dx + dz*dz + dy*dy) + 0.0001); // Add small value to prevent div by zero
            
            double bDot = (bxRel * dx + bzRel * dz + byRel * dy) / 
                    (Math.sqrt(bxRel*bxRel + bzRel*bzRel + byRel*byRel) * 
                     Math.sqrt(dx*dx + dz*dz + dy*dy) + 0.0001);
                     
            // Prioritize nodes that move more directly toward the target
            if (Math.abs(aDot - bDot) > 0.2) { // Only use direction if significant difference
                return Double.compare(bDot, aDot); // Higher dot product first
            }
            
            // If direction is similar, fall back to total cost
            return Double.compare(a.getTotalCost(), b.getTotalCost());
        });

        for(AStarNode neighbour : neighbours) {
            // Create BlockPos for the neighbor for faster lookups
            BlockPos pos = new BlockPos(neighbour.getX(), neighbour.getY(), neighbour.getZ());
            
            // Skip if in closed set (already processed)
            if(closedSet.contains(pos))
                continue;

            // Check if traversable before expensive operations
            if(!neighbour.canBeTraversed())
                continue;
                
            // Get existing node from open set if it exists
            AStarNode existingNode = openSet.get(pos);
            
            if(existingNode == null) {
                // New node - add to open queue and set
                openQueue.add(neighbour);
                openSet.put(pos, neighbour);
            } else if(neighbour.getGCost() < existingNode.getGCost()) {
                // Found a better path to this node - update it
                openQueue.remove(existingNode);
                openQueue.add(neighbour);
                openSet.put(pos, neighbour);
            }
        }
    }

    private static List<AStarNode> getPath(AStarNode currentNode) {
        List<AStarNode> path = new ArrayList<>();
        path.add(currentNode);
        AStarNode parent;
        while ((parent = currentNode.getParent()) != null) {
            path.add(0, parent);
            currentNode = parent;
        }
        return path;
    }
}
