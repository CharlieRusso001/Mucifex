package com.mucifex.pathfinding.internal.pathfind.main;

import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3i;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import com.mucifex.pathfinding.internal.util.RenderUtil;
import com.mucifex.pathfinding.internal.pathfind.main.path.Node;
import com.mucifex.pathfinding.internal.pathfind.main.path.PathElm;
import com.mucifex.pathfinding.internal.pathfind.main.path.impl.JumpNode;
import com.mucifex.pathfinding.internal.pathfind.main.path.impl.TravelVector;
import com.mucifex.pathfinding.internal.pathfind.main.walk.Walker;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PathRenderer {
    private static PathRenderer renderer;
    private List<PathElm> path = new ArrayList<>();
    private static final int MAX_RENDERED_NODES = 300;
    private static final double MAX_RENDER_DISTANCE = 150 * 150;
    private static final Color PATH_COLOR = new Color(0, 191, 255); // Bright sky blue
    private static final Color JUMP_COLOR = new Color(0, 255, 0); // Bright green
    private static final Color NODE_COLOR = new Color(255, 0, 127); // Pink

    public PathRenderer() {
        renderer = this;
    }

    public void render(List<PathElm> elms) {
        try {
            if (elms == null) {
                path = new ArrayList<>();
            } else {
                // Create a copy to prevent concurrent modification issues
                path = new ArrayList<>(elms);
            }
        } catch (Exception e) {
            // If there's an error, just clear the path
            path = new ArrayList<>();
        }
    }
    
    /**
     * Set the final destination for special rendering
     * This method is kept for compatibility but no longer does anything
     */
    public void setFinalDestination(BlockPos pos) {
        // This method is intentionally empty now - we're no longer rendering the destination
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        try {
            // Skip rendering if player is null
            if (Minecraft.getMinecraft().thePlayer == null) {
                return;
            }
            
            // Get current player position
            Vec3 playerPos = Minecraft.getMinecraft().thePlayer.getPositionVector();
            
            // Skip path rendering if walker is inactive or path is empty
            if (!Walker.getInstance().isActive() || path.isEmpty()) {
                return;
            }

            int nodesToRender = Math.min(path.size(), MAX_RENDERED_NODES);
            Node lastNode = null;
            
            // Only render nodes within a reasonable distance
            for (int i = 0; i < nodesToRender; i++) {
                PathElm elm = path.get(i);
                
                if (elm == null) {
                    continue; // Skip null elements
                }

                if (elm instanceof Node) {
                    Node node = (Node) elm;
                    
                    // If the node is too far from the player, don't render it
                    if (isTooFarToRender(playerPos, new Vec3(node.getBlockPos()))) {
                        continue;
                    }
                    
                    if (elm instanceof JumpNode) {
                        try {
                            RenderUtil.drawFilledEsp(node.getBlockPosUnder().subtract(new Vec3i(0, 1, 0)), JUMP_COLOR);
                        } catch (Exception e) {
                            // Skip if can't render
                        }
                    } else {
                        // Render regular nodes with NODE_COLOR
                        try {
                            RenderUtil.drawFilledEsp(node.getBlockPos(), NODE_COLOR);
                        } catch (Exception e) {
                            // Skip if can't render
                        }
                    }

                    if (lastNode != null) {
                        try {
                            List<Vec3> lines = new ArrayList<>();
                            lines.add(new Vec3(lastNode.getBlockPos()).subtract(0, 0.5, 0));
                            lines.add(new Vec3(node.getBlockPos()).subtract(0, 0.5, 0));
                            RenderUtil.drawLines(lines, 3.0f, event.partialTicks, PATH_COLOR.getRGB()); // Thicker lines
                        } catch (Exception e) {
                            // Skip if can't render lines
                        }
                    }

                    lastNode = node;
                }

                if (elm instanceof TravelVector) {
                    try {
                        TravelVector vector = (TravelVector) elm;
                        Node from = vector.getFrom();
                        Node to = vector.getTo();
                        
                        // Skip if nodes are too far to render
                        if (from == null || to == null || 
                            isTooFarToRender(playerPos, new Vec3(from.getBlockPos())) || 
                            isTooFarToRender(playerPos, new Vec3(to.getBlockPos()))) {
                            continue;
                        }

                        List<Vec3> lines = new ArrayList<>();
                        if (lastNode != null) {
                            lines.add(new Vec3(lastNode.getBlockPos()).subtract(0, 0.5, 0));
                        }

                        lines.add(new Vec3(from.getBlockPos()).subtract(0, 0.5, 0));
                        lines.add(new Vec3(to.getBlockPos()).subtract(0, 0.5, 0));

                        RenderUtil.drawLines(lines, 3.0f, event.partialTicks, PATH_COLOR.getRGB()); // Thicker lines

                        RenderUtil.drawFilledEsp(from.getBlockPosUnder(), NODE_COLOR);
                        RenderUtil.drawFilledEsp(to.getBlockPosUnder(), NODE_COLOR);

                        lastNode = to;
                    } catch (Exception e) {
                        // Skip if can't render travel vector
                    }
                }
            }
            
        } catch (Exception e) {
            // If rendering fails for any reason, just silently ignore
            // This prevents crashes during rendering
        }
    }
    
    /**
     * Check if a node is too far from the player to render safely
     */
    private boolean isTooFarToRender(Vec3 playerPos, Vec3 nodePos) {
        try {
            return getDistanceSquared(playerPos, nodePos) > MAX_RENDER_DISTANCE;
        } catch (Exception e) {
            return true; // If can't calculate distance, assume too far
        }
    }
    
    /**
     * Calculate squared distance between two points
     */
    private double getDistanceSquared(Vec3 pos1, Vec3 pos2) {
        double dx = pos1.xCoord - pos2.xCoord;
        double dy = pos1.yCoord - pos2.yCoord;
        double dz = pos1.zCoord - pos2.zCoord;
        return dx * dx + dy * dy + dz * dz;
    }

    public static PathRenderer getInstance() {
        return renderer;
    }
}
