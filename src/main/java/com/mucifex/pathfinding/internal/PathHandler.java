package com.mucifex.pathfinding.internal;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import com.mucifex.pathfinding.internal.pathfind.main.LookManager;
import com.mucifex.pathfinding.internal.pathfind.main.PathRenderer;
import com.mucifex.pathfinding.internal.pathfind.main.walk.Walker;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public class PathHandler {
    public static final String MODID = "PathHandler";
    public static final String VERSION = "1.0";
    
    private Walker walker;
    private boolean isLongDistancePathfinding = false;
    private BlockPos finalDestination = null;
    private BlockPos lastPlayerPosition = null;
    private BlockPos currentWaypoint = null;
    private int stuckCounter = 0;
    private static final int SURVEY_RADIUS = 150; // Doubled from 75 to 150 for larger segments
    private static final int RECALCULATION_DELAY = 40; // Reduced for faster recalculation
    private static final double MIN_MOVEMENT_THRESHOLD = 3.0; // Minimum blocks moved to consider progress
    private int recalculationTicks = 0;
    private int successfulSegments = 0;
    private boolean segmentInProgress = false;
    
    // New fields for stuck detection and auto-restart
    private static final int STUCK_DETECTION_TICKS = 100; // 5 seconds (20 ticks per second)
    private int stuckDetectionCounter = 0;
    private boolean isMovementStuck = false;
    private long lastMovementTime = 0;
    private BlockPos lastStuckPosition = null;
    private int autoRestartCount = 0;
    
    public PathHandler() {
        try {
            // Initialize components but don't register commands
            walker = new Walker();
            registerListeners(walker, new PathRenderer(), new LookManager(), this);
        } catch (Exception e) {
            System.err.println("Error initializing PathHandler: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Travel to the specified coordinates with smarter segmented pathfinding
     */
    public void travel(int x, int y, int z) {
        try {
            if (Minecraft.getMinecraft().thePlayer == null) {
                System.err.println("Cannot pathfind: Player is null");
                return;
            }
            
            // Cancel any existing pathfinding without sending the completion notification
            // This prevents the race condition where cancel() triggers a completion notification
            // that would interfere with the new pathfinding we're about to start
            if (walker != null) {
                try {
                    walker.cancel();
                } catch (Exception e) {
                    System.err.println("Error cancelling pathfinding: " + e.getMessage());
                }
            }
            
            // Reset state without notifying sockets
            isLongDistancePathfinding = false;
            finalDestination = null;
            currentWaypoint = null;
            segmentInProgress = false;
            recalculationTicks = 0;
            
            // Clear the destination in the path renderer
            PathRenderer.getInstance().setFinalDestination(null);
            
            // Now start the new pathfinding operation
            BlockPos currentPos = com.mucifex.pathfinding.internal.util.Util.getPlayerBlockPos();
            if (currentPos == null) {
                System.err.println("Cannot pathfind: Current position is null");
                return;
            }
            
            // Store initial position
            lastPlayerPosition = currentPos;
            stuckCounter = 0;
            segmentInProgress = false;
            successfulSegments = 0;
            
            // Reset stuck detection state
            stuckDetectionCounter = 0;
            isMovementStuck = false;
            lastMovementTime = System.currentTimeMillis();
            lastStuckPosition = null;
            
            BlockPos targetPos = new BlockPos(x, y, z);
            
            // Set the final destination in the path renderer
            PathRenderer.getInstance().setFinalDestination(targetPos);
            
            // Check if the target is too far away
            double distance = getDistance(currentPos, targetPos);
            
            // Send a chat message about the pathfinding status
            String message;
            
            if (distance > SURVEY_RADIUS) {
                // For long-distance pathfinding, we store the final destination
                isLongDistancePathfinding = true;
                finalDestination = targetPos;
                
                // Find best intermediate waypoint within our survey radius
                BlockPos waypoint = findBestWaypointInRadius(currentPos, targetPos);
                currentWaypoint = waypoint;
                
                message = String.format("Starting segmented pathfinding to: %d, %d, %d (%.1f blocks away, using %d block segments)", 
                    x, y, z, distance, SURVEY_RADIUS);
                
                // Start pathfinding to nearby waypoint
                if (walker != null) {
                    segmentInProgress = true;
                    
                    double dx = waypoint.getX() - currentPos.getX();
                    double dz = waypoint.getZ() - currentPos.getZ();
                    double waypointDistance = getDistance(currentPos, waypoint);
                    
                    sendChatMessage(String.format("First waypoint: %d, %d, %d (%.1f blocks, direction: %.1f,%.1f)", 
                        waypoint.getX(), waypoint.getY(), waypoint.getZ(),
                        waypointDistance, dx, dz));
                        
                    // Use appropriate node count based on distance for efficiency while preventing timeouts
                    int nodeCount = Math.min(15000, (int)(waypointDistance * 250));
                    safeWalk(currentPos, waypoint, nodeCount);
                }
            } else {
                // Target is within survey radius, normal pathing
                isLongDistancePathfinding = false;
                finalDestination = null;
                
                message = String.format("Pathfinding to nearby location: %d, %d, %d (%.1f blocks away)", 
                    x, y, z, distance);
                
                if (walker != null) {
                    // Use node count based on distance for direct paths
                    // More nodes for shorter distances ensure accuracy, fewer for longer paths improve performance
                    int nodeCount = Math.min(12000, Math.max(5000, (int)(distance * 200)));
                    safeWalk(currentPos, targetPos, nodeCount);
                }
            }
            
            // Display message to player
            sendChatMessage(message);
            
        } catch (Exception e) {
            System.err.println("Error in travel method: " + e.getMessage());
            e.printStackTrace();
            cancel(); // Cancel pathfinding if an error occurs
        }
    }
    
    /**
     * A safer version of walk that handles possible rendering issues
     */
    private void safeWalk(BlockPos start, BlockPos target, int nodeCount) {
        try {
            if (walker != null) {
                // Add additional debug info
                System.out.println("DEBUG: Attempting to walk from " + posToString(start) + " to " + posToString(target) + " with " + nodeCount + " nodes");
                
                // Check if target position is even reachable - don't try to pathfind through solid blocks
                if (!isPositionWalkable(target)) {
                    System.out.println("ERROR: Target position " + posToString(target) + " is not walkable - choosing alternative target");
                    
                    // Try to find a walkable position nearby instead
                    BlockPos walkableTarget = findWalkablePositionNear(target);
                    if (walkableTarget != null) {
                        System.out.println("DEBUG: Found walkable alternative at " + posToString(walkableTarget));
                        target = walkableTarget;
                    }
                }
                
                // Check if there's a clear enough path to try direct movement
                boolean canUseDirectPath = isDirectPathClear(start, target);
                if (canUseDirectPath) {
                    System.out.println("DEBUG: Using more efficient direct path calculation");
                    // Use fewer nodes for clear direct paths to improve performance
                    nodeCount = Math.min(nodeCount, 5000);
                }
                
                walker.walk(start, target, nodeCount);
            }
        } catch (Exception e) {
            System.err.println("Error in safeWalk: " + e.getMessage());
            e.printStackTrace();
            cancel();
        }
    }
    
    /**
     * Checks if there appears to be a relatively clear path between two points
     * This is a quick approximation to determine if we can use a more direct pathfinding approach
     */
    private boolean isDirectPathClear(BlockPos start, BlockPos target) {
        try {
            // Calculate direct vector between points
            double dx = target.getX() - start.getX();
            double dy = target.getY() - start.getY();
            double dz = target.getZ() - start.getZ();
            double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
            
            // For very short distances, always return true
            if (distance < 10) return true;
            
            // Sample points along the line to check for obstructions
            int samples = Math.min(10, (int)(distance / 5)); // One sample every 5 blocks, max 10 samples
            
            int clearSamples = 0;
            for (int i = 1; i < samples; i++) {
                double fraction = i / (double)samples;
                int x = (int)(start.getX() + dx * fraction);
                int y = (int)(start.getY() + dy * fraction);
                int z = (int)(start.getZ() + dz * fraction);
                
                BlockPos pos = new BlockPos(x, y, z);
                BlockPos above = new BlockPos(x, y+1, z);
                
                // Check if this position and one above are clear for the player
                if (!isBlockSolid(pos) && !isBlockSolid(above)) {
                    clearSamples++;
                }
            }
            
            // If most sample points are clear, consider it a direct path
            return clearSamples >= (samples * 0.7);
        } catch (Exception e) {
            System.err.println("Error checking direct path: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if a position is air (walkable)
     */
    private boolean isPositionWalkable(BlockPos pos) {
        try {
            // Check current position and position above for solids
            return !isBlockSolid(pos) && !isBlockSolid(pos.up());
        } catch (Exception e) {
            System.err.println("Error checking if position is walkable: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if a block is solid/unwalkable
     */
    private boolean isBlockSolid(BlockPos block) {
        try {
            // Cache the block state to avoid multiple lookups
            net.minecraft.block.state.IBlockState state = Minecraft.getMinecraft().theWorld.getBlockState(block);
            net.minecraft.block.Block blockType = state.getBlock();
            
            // Fast path for air blocks (most common case)
            if (blockType.getMaterial() == net.minecraft.block.material.Material.air) {
                return false;
            }
            
            // Fast path for full solid blocks
            if (blockType.getMaterial().blocksMovement() && !blockType.getMaterial().isLiquid()) {
                // Check if it's a full cube (most solid blocks)
                if (blockType.isFullCube()) {
                    return true;
                }
            }
            
            // For non-standard blocks, check specific types
            return blockType.isBlockSolid(Minecraft.getMinecraft().theWorld, block, null) ||
                   blockType instanceof net.minecraft.block.BlockSlab ||
                   blockType instanceof net.minecraft.block.BlockStainedGlass ||
                   blockType instanceof net.minecraft.block.BlockPane ||
                   blockType instanceof net.minecraft.block.BlockFence ||
                   blockType instanceof net.minecraft.block.BlockPistonExtension ||
                   blockType instanceof net.minecraft.block.BlockEnderChest ||
                   blockType instanceof net.minecraft.block.BlockTrapDoor ||
                   blockType instanceof net.minecraft.block.BlockPistonBase ||
                   blockType instanceof net.minecraft.block.BlockChest ||
                   blockType instanceof net.minecraft.block.BlockStairs ||
                   blockType instanceof net.minecraft.block.BlockCactus ||
                   blockType instanceof net.minecraft.block.BlockWall ||
                   blockType instanceof net.minecraft.block.BlockGlass ||
                   blockType instanceof net.minecraft.block.BlockSkull ||
                   blockType instanceof net.minecraft.block.BlockSand;
        } catch (Exception e) {
            System.err.println("Error checking if block is solid: " + e.getMessage());
            return true; // Assume solid if error
        }
    }
    
    /**
     * Find a walkable position near the target
     */
    private BlockPos findWalkablePositionNear(BlockPos target) {
        try {
            // Try positions in a spiral pattern around the target - increased search radius
            for (int radius = 1; radius <= 30; radius++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        // Only check the outer shell of each radius increment
                        if (Math.abs(x) == radius || Math.abs(z) == radius) {
                            // Try positions at different heights
                            for (int y = -3; y <= 3; y++) {
                                BlockPos candidate = target.add(x, y, z);
                                if (isPositionWalkable(candidate)) {
                                    return candidate;
                                }
                            }
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error finding walkable position: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Convert BlockPos to readable string
     */
    private String posToString(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
    
    /**
     * Find the best waypoint within survey radius that's closest to destination
     */
    private BlockPos findBestWaypointInRadius(BlockPos current, BlockPos destination) {
        try {
            System.out.println("DEBUG: Finding best waypoint from " + posToString(current) + " towards " + posToString(destination));
            
            // Generate potential waypoints in a grid pattern within the survey radius
            List<BlockPos> potentialWaypoints = new ArrayList<>();
            List<BlockPos> walkableWaypoints = new ArrayList<>();
            
            // Keep track of the last waypoint to avoid repeating the same one
            BlockPos lastAttemptedWaypoint = currentWaypoint;
            if (lastAttemptedWaypoint != null) {
                System.out.println("DEBUG: Last attempted waypoint was " + posToString(lastAttemptedWaypoint));
            }
            
            // Calculate direction vector from current to destination
            double dx = destination.getX() - current.getX();
            double dy = destination.getY() - current.getY();
            double dz = destination.getZ() - current.getZ();
            
            System.out.println("DEBUG: Direction vector: (" + dx + ", " + dy + ", " + dz + ")");
            
            // If we've been stuck for multiple attempts, try more varied positions - scaled for larger survey radius
            int explorationDistance = stuckCounter * 10 + 20; // 20, 30, 40, 50...
            System.out.println("DEBUG: Using exploration distance: " + explorationDistance + " blocks based on stuckCounter " + stuckCounter);
            
            // If we've been stuck, scatter waypoints in all directions
            if (stuckCounter > 0) {
                System.out.println("DEBUG: Generating scatter waypoints due to being stuck");
                // Try points in cardinal directions first (more likely to find paths)
                int[][] cardinalDirections = {{1,0}, {0,1}, {-1,0}, {0,-1}, {1,1}, {-1,1}, {1,-1}, {-1,-1}};
                
                for (int[] dir : cardinalDirections) {
                    BlockPos cardinalWaypoint = new BlockPos(
                        current.getX() + dir[0] * explorationDistance,
                        current.getY(),
                        current.getZ() + dir[1] * explorationDistance
                    );
                    potentialWaypoints.add(cardinalWaypoint);
                    
                    // Also try at different Y levels
                    potentialWaypoints.add(cardinalWaypoint.up(1));
                    potentialWaypoints.add(cardinalWaypoint.down(1));
                }
                
                // Also try the opposite direction from our original path
                // This helps when we're stuck against a wall
                BlockPos oppositeWaypoint = new BlockPos(
                    current.getX() - (int)(dx * 0.3),
                    current.getY(),
                    current.getZ() - (int)(dz * 0.3)
                );
                potentialWaypoints.add(oppositeWaypoint);
                
                // After 2 attempts, try just going up or down
                if (stuckCounter >= 2) {
                    System.out.println("DEBUG: Adding vertical exploration waypoints");
                    potentialWaypoints.add(current.up(3));
                    potentialWaypoints.add(current.down(3));
                }
            }
            
            // Always add some short-range waypoints in the destination direction
            System.out.println("DEBUG: Adding directional short-range waypoints");
            for (double dist = 0.1; dist <= 0.4; dist += 0.1) {
                BlockPos shortWaypoint = new BlockPos(
                    current.getX() + (int)(dx * dist),
                    current.getY() + (int)(dy * dist),
                    current.getZ() + (int)(dz * dist)
                );
                potentialWaypoints.add(shortWaypoint);
                
                // Also add waypoints at different Y levels
                potentialWaypoints.add(shortWaypoint.up(1));
                potentialWaypoints.add(shortWaypoint.down(1));
            }
            
            // Normalize the direction vector for longer targets
            double length = Math.sqrt(dx*dx + dy*dy + dz*dz);
            double ndx = dx / length;
            double ndy = dy / length;
            double ndz = dz / length;
            
            // Create primary waypoint with moderate distance
            BlockPos primaryWaypoint = new BlockPos(
                current.getX() + (int)(ndx * SURVEY_RADIUS * 0.4),
                current.getY() + (int)(ndy * SURVEY_RADIUS * 0.4),
                current.getZ() + (int)(ndz * SURVEY_RADIUS * 0.4)
            );
            
            potentialWaypoints.add(primaryWaypoint);
            
            // Add waypoints at different distances along the path for better coverage with larger radius
            // 20%, 40%, and 60% of the survey radius
            double[] distanceFactors = {0.2, 0.4, 0.6};
            for (double factor : distanceFactors) {
                BlockPos distanceWaypoint = new BlockPos(
                    current.getX() + (int)(ndx * SURVEY_RADIUS * factor),
                    current.getY() + (int)(ndy * SURVEY_RADIUS * factor),
                    current.getZ() + (int)(ndz * SURVEY_RADIUS * factor)
                );
                potentialWaypoints.add(distanceWaypoint);
                // Also add at different heights
                potentialWaypoints.add(distanceWaypoint.up(1));
                potentialWaypoints.add(distanceWaypoint.down(1));
            }
            
            System.out.println("DEBUG: Generated " + potentialWaypoints.size() + " potential waypoints");
            
            // Filter out waypoints that aren't walkable
            for (BlockPos waypoint : potentialWaypoints) {
                // Skip the last waypoint to avoid getting stuck in a loop
                if (lastAttemptedWaypoint != null && 
                    waypoint.getX() == lastAttemptedWaypoint.getX() && 
                    waypoint.getY() == lastAttemptedWaypoint.getY() && 
                    waypoint.getZ() == lastAttemptedWaypoint.getZ()) {
                    System.out.println("DEBUG: Skipping previously attempted waypoint: " + posToString(waypoint));
                    continue;
                }
                
                if (isPositionWalkable(waypoint)) {
                    walkableWaypoints.add(waypoint);
                }
            }
            
            System.out.println("DEBUG: Found " + walkableWaypoints.size() + " walkable waypoints");
            
            // No walkable waypoints found, generate more aggressive options
            if (walkableWaypoints.isEmpty()) {
                System.out.println("DEBUG: No walkable waypoints found, trying more aggressive options");
                
                // Try positions closer to the player
                for (int x = -5; x <= 5; x += 2) {
                    for (int z = -5; z <= 5; z += 2) {
                        for (int y = -1; y <= 1; y++) {
                            BlockPos nearbyPos = current.add(x, y, z);
                            if (isPositionWalkable(nearbyPos) && 
                                (lastAttemptedWaypoint == null || 
                                 !nearbyPos.equals(lastAttemptedWaypoint))) {
                                walkableWaypoints.add(nearbyPos);
                            }
                        }
                    }
                }
            }
            
            // If still no walkable waypoints, return a fallback position
            if (walkableWaypoints.isEmpty()) {
                System.out.println("DEBUG: No walkable waypoints found even with aggressive options");
                
                // Random direction as a last resort
                double angle = Math.random() * Math.PI * 2;
                BlockPos randomWaypoint = new BlockPos(
                    current.getX() + (int)(Math.cos(angle) * 5),
                    current.getY(),
                    current.getZ() + (int)(Math.sin(angle) * 5)
                );
                
                System.out.println("DEBUG: Using random direction fallback waypoint: " + posToString(randomWaypoint));
                return randomWaypoint;
            }
            
            // Try to find a waypoint that's better than our current position
            BlockPos bestWaypoint = null;
            double bestScore = Double.MAX_VALUE;
            
            for (BlockPos wp : walkableWaypoints) {
                // Calculate a score based on distance to destination and distance from current position
                double distToDestination = getDistance(wp, destination);
                double distFromCurrent = getDistance(current, wp);
                
                // Avoid picking waypoints that are too close to current position
                if (distFromCurrent < 3) {
                    continue;
                }
                
                // Score is distance to destination, but we prefer waypoints that are at least a bit away from current
                double score = distToDestination - (distFromCurrent * 0.1);
                
                // Heavily penalize the last attempted waypoint to avoid loops
                if (lastAttemptedWaypoint != null && 
                    wp.getX() == lastAttemptedWaypoint.getX() && 
                    wp.getY() == lastAttemptedWaypoint.getY() && 
                    wp.getZ() == lastAttemptedWaypoint.getZ()) {
                    score += 1000;
                }
                
                if (bestWaypoint == null || score < bestScore) {
                    bestWaypoint = wp;
                    bestScore = score;
                }
            }
            
            // Use the best scoring waypoint
            if (bestWaypoint != null) {
                System.out.println("DEBUG: Chosen best waypoint: " + posToString(bestWaypoint) + " with score " + bestScore);
                return bestWaypoint;
            }
            
            // If no best waypoint, just pick any walkable waypoint
            BlockPos selectedWaypoint = walkableWaypoints.get(0);
            System.out.println("DEBUG: Using fallback walkable waypoint: " + posToString(selectedWaypoint));
            return selectedWaypoint;
        } catch (Exception e) {
            System.err.println("ERROR in findBestWaypointInRadius: " + e.getMessage());
            e.printStackTrace();
            
            // Fallback to a very simple waypoint as last resort
            BlockPos fallbackPos = current.add(stuckCounter + 5, 0, stuckCounter + 5);
            System.out.println("DEBUG: Using emergency fallback waypoint: " + posToString(fallbackPos) + " due to error");
            return fallbackPos;
        }
    }
    
    /**
     * Calculate distance between two block positions
     */
    private double getDistance(BlockPos pos1, BlockPos pos2) {
        if (pos1 == null || pos2 == null) {
            return Double.MAX_VALUE; // Return a large distance if positions are null
        }
        double dx = pos1.getX() - pos2.getX();
        double dy = pos1.getY() - pos2.getY();
        double dz = pos1.getZ() - pos2.getZ();
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
    
    /**
     * Check if currently pathing
     */
    public boolean isActive() {
        boolean active = (walker != null && walker.isActive()) || isLongDistancePathfinding;
        
        // If we're inactive but thought we were doing long-distance pathfinding,
        // complete the path to clean up resources and notify sockets
        if (!active && isLongDistancePathfinding) {
            completePath("Pathfinding stopped.");
        }
        
        return active;
    }

    /**
     * Monitor the pathfinding progress and restart pathing to next waypoint if needed
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        try {
            // Only process in END phase
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            
            // Check for movement stuckness regardless of pathfinding state
            checkPlayerStuck();
            
            // Return early if not in long distance pathfinding mode
            if (!isLongDistancePathfinding || finalDestination == null) {
                return;
            }
            
            // Increment delay timer
            recalculationTicks++;
            
            // Only check for recalculation if we've waited long enough
            if (recalculationTicks < RECALCULATION_DELAY) {
                return;
            }
            
            // Reset delay timer
            recalculationTicks = 0;
            
            // Get player position
            if (Minecraft.getMinecraft().thePlayer == null) {
                return; // Player not available
            }
            
            BlockPos currentPos = com.mucifex.pathfinding.internal.util.Util.getPlayerBlockPos();
            if (currentPos == null) {
                return; // Position not available
            }
            
            // If Walker is active, we're still executing a path segment
            // Let it continue until it finishes
            if (walker != null && walker.isActive()) {
                System.out.println("DEBUG: Walker still active, continuing current path");
                return;
            }
            
            System.out.println("DEBUG: Walker no longer active, evaluating next step");
            
            // If we get here, the current segment is no longer active (finished or failed)
            
            // Check if player has moved since last segment
            boolean hasMoved = false;
            double distanceMoved = 0;
            
            if (lastPlayerPosition != null) {
                distanceMoved = getDistance(lastPlayerPosition, currentPos);
                hasMoved = distanceMoved > MIN_MOVEMENT_THRESHOLD;
                
                System.out.println("DEBUG: Player moved " + distanceMoved + " blocks since last check, hasMoved=" + hasMoved);
                
                if (!hasMoved && segmentInProgress) {
                    // Player is stuck on current segment
                    stuckCounter++;
                    sendChatMessage(String.format("Not moving (attempt %d)! Trying different approach...", stuckCounter));
                    System.out.println("DEBUG: Player is stuck, stuckCounter now " + stuckCounter);
                    
                    // If we've tried too many times with the same waypoint, force a new waypoint
                    if (stuckCounter >= 3) {
                        sendChatMessage("Multiple failed attempts. Forcing new pathfinding approach.");
                        System.out.println("DEBUG: Forcing new pathfinding approach after multiple attempts");
                        stuckCounter = 0;
                    }
                } else if (hasMoved) {
                    // Reset stuck counter if we moved
                    stuckCounter = 0;
                    System.out.println("DEBUG: Player moved, resetting stuckCounter to 0");
                }
            }
            
            // Update last position
            lastPlayerPosition = currentPos;
            
            // Calculate distance to final destination
            double distanceToFinal = getDistance(currentPos, finalDestination);
            System.out.println("DEBUG: Distance to final destination: " + distanceToFinal + " blocks");
            
            // If a segment was in progress and now it's done, report progress
            if (segmentInProgress) {
                segmentInProgress = false;
                successfulSegments++;
                
                // Check if we moved closer to our waypoint
                double distanceToWaypoint = currentWaypoint != null ? getDistance(currentPos, currentWaypoint) : Double.MAX_VALUE;
                System.out.println("DEBUG: Distance to last waypoint: " + distanceToWaypoint + " blocks");
                
                if (distanceToWaypoint < 10) {
                    // Got close enough to waypoint
                    sendChatMessage(String.format("Reached waypoint! %.1f blocks remaining to destination.", distanceToFinal));
                    System.out.println("DEBUG: Successfully reached waypoint!");
                } else {
                    // Didn't reach waypoint but moved
                    sendChatMessage(String.format("Segment %d completed. Moved %.1f blocks. %.1f blocks remaining to destination.", 
                        successfulSegments, distanceMoved, distanceToFinal));
                    System.out.println("DEBUG: Segment completed but waypoint not reached");
                }
            }
            
            // Check if we've reached the final destination
            if (distanceToFinal <= 5) {
                System.out.println("DEBUG: Final destination reached (distance: " + distanceToFinal + ")");
                completePath("Reached destination!");
                return;
            } 
            
            // Start a new segment - this is the key part we need to improve
            if (distanceToFinal <= SURVEY_RADIUS * 0.7) {
                // Final destination is within reduced range - head directly there
                sendChatMessage(String.format("Final destination in range (%.1f blocks). Completing path.", distanceToFinal));
                System.out.println("DEBUG: Final destination in range, attempting direct path");
                segmentInProgress = true;
                currentWaypoint = finalDestination;
                
                // Try to pathfind directly to the final destination - optimized node count
                safeWalk(currentPos, finalDestination, 10000);
            } else {
                // This is where we find a new waypoint that we can actually reach
                System.out.println("DEBUG: Finding next waypoint towards final destination");
                BlockPos nextWaypoint = findBestWaypointInRadius(currentPos, finalDestination);
                
                // Only start new pathfinding if player is loaded
                if (Minecraft.getMinecraft().thePlayer != null) {
                    double waypointDistance = getDistance(currentPos, nextWaypoint);
                    double dx = nextWaypoint.getX() - currentPos.getX();
                    double dz = nextWaypoint.getZ() - currentPos.getZ();
                    
                    // Log the new waypoint details
                    sendChatMessage(String.format("Starting segment %d: Waypoint at %d, %d, %d (%.1f blocks, direction: %.1f,%.1f)", 
                        successfulSegments + 1, nextWaypoint.getX(), nextWaypoint.getY(), nextWaypoint.getZ(),
                        waypointDistance, dx, dz));
                    
                    System.out.println("DEBUG: Using waypoint: " + posToString(nextWaypoint) + 
                        " (distance: " + waypointDistance + " blocks)");
                    
                    // Set up for the new segment
                    currentWaypoint = nextWaypoint;
                    segmentInProgress = true;
                    
                    // Use optimized node counts for better performance - balanced approach
                    int nodeCount = Math.min(8000 + (stuckCounter * 2000), 14000);
                    System.out.println("DEBUG: Using nodeCount " + nodeCount + " for pathfinding");
                    
                    // Start pathfinding with adjusted node count
                    safeWalk(currentPos, nextWaypoint, nodeCount);
                }
            }
        } catch (Exception e) {
            System.err.println("ERROR in onClientTick: " + e.getMessage());
            e.printStackTrace();
            // Reset pathfinding state if an error occurs
            completePath("Error occurred during pathfinding.");
        }
    }

    /**
     * Complete pathfinding and clean up resources
     */
    private void completePath(String message) {
        // Send completion message
        sendChatMessage(message);
        
        // Reset all pathfinding state
        isLongDistancePathfinding = false;
        finalDestination = null;
        currentWaypoint = null;
        segmentInProgress = false;
        
        // Clear the path visualization
        PathRenderer.getInstance().setFinalDestination(null);
        // Clear the rendered path
        if (walker != null) {
            walker.clearRenderedPath();
        }
        
        // Reset socket state to allow new connections - always call this to ensure socket is freed
        notifySocketsPathfindingComplete();
    }
    
    /**
     * Notify socket servers that pathfinding is complete
     */
    private void notifySocketsPathfindingComplete() {
        try {
            // Send notification to the pathfinding socket server
            com.mucifex.socket.PathfindingSocketServer.notifyPathfindingComplete();
        } catch (Exception e) {
            System.err.println("Error notifying sockets of pathfinding completion: " + e.getMessage());
            // Log but don't rethrow to ensure cleanup continues
        }
    }
    
    /**
     * Cancel current pathing
     */
    public void cancel() {
        if (walker != null) {
            try {
                walker.cancel();
            } catch (Exception e) {
                System.err.println("Error cancelling pathfinding: " + e.getMessage());
            }
        }
        
        completePath("Pathfinding cancelled.");
    }
    
    /**
     * Send a chat message to the player
     */
    private void sendChatMessage(String message) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§a[Pathfinding] §f" + message));
            }
        } catch (Exception e) {
            System.err.println("Error sending chat message: " + e.getMessage());
        }
    }
    
    private void registerListeners(Object... listeners) {
        Arrays.stream(listeners).forEachOrdered(MinecraftForge.EVENT_BUS::register);
    }

    /**
     * Check if the player is stuck and attempt to restart pathfinding if needed
     */
    private void checkPlayerStuck() {
        try {
            if (!isLongDistancePathfinding || finalDestination == null) {
                // Reset stuck detection state when not pathfinding
                stuckDetectionCounter = 0;
                isMovementStuck = false;
                return;
            }
        
            // Get current player position
            if (Minecraft.getMinecraft().thePlayer == null) {
                return; // Player not available
            }
            
            BlockPos currentPos = com.mucifex.pathfinding.internal.util.Util.getPlayerBlockPos();
            if (currentPos == null) {
                return; // Position not available
            }
            
            // First time initialization
            if (lastStuckPosition == null) {
                lastStuckPosition = currentPos;
                lastMovementTime = System.currentTimeMillis();
                return;
            }
            
            // Check if player has moved
            double distanceMoved = getDistance(lastStuckPosition, currentPos);
            boolean hasMoved = distanceMoved > 0.3; // More sensitive threshold for stuck detection
            
            if (hasMoved) {
                // Reset stuck detection since player moved
                stuckDetectionCounter = 0;
                isMovementStuck = false;
                lastStuckPosition = currentPos;
                lastMovementTime = System.currentTimeMillis();
                return;
            }
            
            // Check if we were already in active pathfinding
            boolean isActive = walker != null && walker.isActive();
            
            // If not moving and in active pathfinding, increment stuck counter
            if (isActive && !hasMoved) {
                stuckDetectionCounter++;
                
                // If stuck for 5 seconds (100 ticks), trigger auto-restart
                if (stuckDetectionCounter >= STUCK_DETECTION_TICKS) {
                    long stuckTime = System.currentTimeMillis() - lastMovementTime;
                    System.out.println("DEBUG: Player is stuck at " + posToString(currentPos) + 
                        " for " + (stuckTime / 1000) + " seconds. Auto-restarting pathfinding.");
                    
                    // Limit the number of auto-restarts to prevent infinite loops
                    autoRestartCount++;
                    if (autoRestartCount > 3) {
                        // After 3 restarts, try a completely different approach
                        System.out.println("DEBUG: Multiple auto-restarts failed. Forcing new waypoint selection.");
                        stuckCounter += 2; // Increase stuck counter to force different waypoint selection
                        autoRestartCount = 0;
                    }
                    
                    // Cancel current pathfinding without notifying sockets since we're going to restart
                    if (walker != null) {
                        try {
                            walker.cancel();
                        } catch (Exception e) {
                            System.err.println("Error cancelling pathfinding: " + e.getMessage());
                        }
                    }
                    
                    sendChatMessage("Stuck for " + (stuckTime / 1000) + " seconds! Auto-restarting pathfinding...");
                    
                    // Reset stuck detection state
                    stuckDetectionCounter = 0;
                    isMovementStuck = false;
                    
                    // If we still have our final destination, restart pathfinding with a new waypoint
                    if (finalDestination != null) {
                        // Calculate new waypoint
                        BlockPos newWaypoint = findBestWaypointInRadius(currentPos, finalDestination);
                        if (newWaypoint != null) {
                            System.out.println("DEBUG: Chosen new waypoint: " + posToString(newWaypoint));
                            
                            // Start new pathfinding with the new waypoint
                            double waypointDistance = getDistance(currentPos, newWaypoint);
                            sendChatMessage(String.format("Auto-restart: New waypoint at %d, %d, %d (%.1f blocks)", 
                                newWaypoint.getX(), newWaypoint.getY(), newWaypoint.getZ(), waypointDistance));
                            
                            currentWaypoint = newWaypoint;
                            segmentInProgress = true;
                            
                            // Use more nodes for auto-restart to increase chances of success
                            // But keep it reasonable to avoid timeouts
                            int nodeCount = Math.min(10000 + (stuckCounter * 2000), 16000);
                            safeWalk(currentPos, newWaypoint, nodeCount);
                        }
                    }
                    
                    lastStuckPosition = currentPos;
                    lastMovementTime = System.currentTimeMillis();
                }
            } else if (!isActive) {
                // Reset stuck detection if not in active pathfinding
                stuckDetectionCounter = 0;
            }
        } catch (Exception e) {
            System.err.println("Error in stuck detection: " + e.getMessage());
            // Reset detection state to avoid loops
            stuckDetectionCounter = 0;
            isMovementStuck = false;
        }
    }
} 