package com.mucifex.pathfinding.internal.pathfind.main.walk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Tuple;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import com.mucifex.pathfinding.internal.util.LookUtil;
import com.mucifex.pathfinding.internal.util.Util;
import com.mucifex.pathfinding.internal.pathfind.main.LookManager;
import com.mucifex.pathfinding.internal.pathfind.main.PathRenderer;
import com.mucifex.pathfinding.internal.pathfind.main.processor.ProcessorManager;
import com.mucifex.pathfinding.internal.pathfind.main.astar.AStarNode;
import com.mucifex.pathfinding.internal.pathfind.main.astar.AStarPathFinder;
import com.mucifex.pathfinding.internal.pathfind.main.path.PathElm;
import com.mucifex.pathfinding.internal.pathfind.main.path.impl.FallNode;
import com.mucifex.pathfinding.internal.pathfind.main.path.impl.JumpNode;
import com.mucifex.pathfinding.internal.pathfind.main.path.impl.TravelNode;
import com.mucifex.pathfinding.internal.pathfind.main.path.impl.TravelVector;
import com.mucifex.pathfinding.internal.pathfind.main.walk.target.WalkTarget;
import com.mucifex.pathfinding.internal.pathfind.main.walk.target.impl.FallTarget;
import com.mucifex.pathfinding.internal.pathfind.main.walk.target.impl.JumpTarget;
import com.mucifex.pathfinding.internal.pathfind.main.walk.target.impl.TravelTarget;
import com.mucifex.pathfinding.internal.pathfind.main.walk.target.impl.TravelVectorTarget;

import java.util.ArrayList;
import java.util.List;

public class Walker {
    private static Walker instance;
    private boolean isActive;
    private boolean enableRendering = true;

    List<PathElm> path;
    WalkTarget currentTarget;

    public Walker() {
        instance = this;
        // Initialize path as empty list to avoid null pointer exceptions
        path = new ArrayList<>();
    }
    
    private void sendDebugMessage(String message) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                // Uncomment for debug: mc.thePlayer.addChatMessage(new ChatComponentText("§e[Debug] §f" + message));
            }
        } catch (Exception e) {
            // Ignore chat errors
        }
    }

    public void walk(BlockPos start, BlockPos end, int nodeCount) {
        try {
            if (start == null || end == null) {
                sendDebugMessage("Cannot pathfind with null positions");
                return;
            }
            
            isActive = true;
            
            // Always enable rendering regardless of distance
            enableRendering = true;
            
            // Compute path nodes with the A* algorithm
            List<AStarNode> nodes = AStarPathFinder.compute(start, end, nodeCount);
            
            // Process nodes into path elements
            path = ProcessorManager.process(nodes);
            
            if (path == null || path.isEmpty()) {
                sendDebugMessage("No valid path found");
                isActive = false;
                currentTarget = null;
                return;
            }
            
            // Always render the path
            PathRenderer.getInstance().render(path);
            
            currentTarget = null;
            
        } catch (Exception e) {
            sendDebugMessage("Error in walk method: " + e.getMessage());
            isActive = false;
            path = new ArrayList<>();
            currentTarget = null;
        }
    }


    // Key press in here
    @SubscribeEvent
    public void onClientTickPre(TickEvent.ClientTickEvent event) {
        try {
            if (event.phase == TickEvent.Phase.END || Minecraft.getMinecraft().thePlayer == null)
                return;
    
            if (!isActive)
                return;
    
            // Add null and empty check for path
            if (path == null || path.isEmpty()) {
                isActive = false;
                currentTarget = null;
                return;
            }
    
            if (currentTarget == null) {
                try {
                    currentTarget = getCurrentTarget(path.get(0));
                } catch (Exception e) {
                    // If we can't get a current target, abort pathfinding
                    sendDebugMessage("Error getting target: " + e.getMessage());
                    isActive = false;
                    path = new ArrayList<>();
                    return;
                }
            }
    
            // Add null check for currentTarget
            if (currentTarget == null) {
                isActive = false;
                path = new ArrayList<>();
                return;
            }
    
            WalkTarget playerOnTarget;
            try {
                playerOnTarget = onTarget();
                if (playerOnTarget != null) {
                    currentTarget = playerOnTarget;
                }
            } catch (Exception e) {
                sendDebugMessage("Error in onTarget: " + e.getMessage());
                // Continue with current target if onTarget fails
            }
    
            // while, so we don't skip ticks
            try {
                while (tick(currentTarget)) {
                    // removes it
                    path.remove(0);
    
                    if (path.isEmpty()) {
                        isActive = false;
                        currentTarget = null;
                        releaseAllKeys();
                        LookManager.getInstance().cancel();
                        return;
                    }
    
                    currentTarget = getCurrentTarget(path.get(0));
                    if (currentTarget == null) {
                        isActive = false;
                        releaseAllKeys();
                        LookManager.getInstance().cancel();
                        return;
                    }
                }
            } catch (Exception e) {
                sendDebugMessage("Error during tick: " + e.getMessage());
                // If tick fails, continue with navigation
            }
    
            try {
                KeyBinding.setKeyBindState(Keyboard.KEY_LCONTROL, true);
                Tuple<Double, Double> angles = LookUtil.getAngles(currentTarget.getCurrentTarget());
                LookManager.getInstance().setTarget(angles.getFirst().floatValue(), currentTarget instanceof JumpTarget ? -10 : 10);
                
                pressKeys(angles.getFirst().floatValue());
            } catch (Exception e) {
                sendDebugMessage("Error setting keys/angles: " + e.getMessage());
                // If key setting fails, try to continue
            }
        } catch (Exception e) {
            // Catch-all for any other errors in the tick method
            sendDebugMessage("General error in onClientTickPre: " + e.getMessage());
            isActive = false;
            releaseAllKeys();
            
            if (path != null) {
                path.clear();
            } else {
                path = new ArrayList<>();
            }
            
            currentTarget = null;
        }
    }
    
    /**
     * Release all movement keys to prevent stuck movement
     */
    private void releaseAllKeys() {
        try {
            KeyBinding.setKeyBindState(Keyboard.KEY_W, false);
            KeyBinding.setKeyBindState(Keyboard.KEY_A, false);
            KeyBinding.setKeyBindState(Keyboard.KEY_D, false);
            KeyBinding.setKeyBindState(Keyboard.KEY_S, false);
            KeyBinding.setKeyBindState(Keyboard.KEY_LCONTROL, false);
        } catch (Exception e) {
            sendDebugMessage("Error releasing keys: " + e.getMessage());
        }
    }

    private void pressKeys(double targetYaw) {
        try {
            double difference = targetYaw - Minecraft.getMinecraft().thePlayer.rotationYaw;
            releaseAllKeys();
    
            if (22.5 > difference && difference > -22.5) {   // Forwards
                KeyBinding.setKeyBindState(Keyboard.KEY_W, true);
            } else if (-22.5 > difference && difference > -67.5) {   // Forwards+Right
                KeyBinding.setKeyBindState(Keyboard.KEY_W, true);
                KeyBinding.setKeyBindState(Keyboard.KEY_A, true);
            } else if (-67.5 > difference && difference > -112.5) { // Right
                KeyBinding.setKeyBindState(Keyboard.KEY_A, true);
            } else if (-112.5 > difference && difference > -157.5) { // Backwards + Right
                KeyBinding.setKeyBindState(Keyboard.KEY_A, true);
                KeyBinding.setKeyBindState(Keyboard.KEY_S, true);
            } else if ((-157.5 > difference && difference > -180) || (180 > difference && difference > 157.5)) { // Backwards
                KeyBinding.setKeyBindState(Keyboard.KEY_S, true);
            } else if (67.5 > difference && difference > 22.5) { // Forwards + Left
                KeyBinding.setKeyBindState(Keyboard.KEY_W, true);
                KeyBinding.setKeyBindState(Keyboard.KEY_D, true);
            } else if (112.5 > difference && difference > 67.5) { // Left
                KeyBinding.setKeyBindState(Keyboard.KEY_D, true);
            } else if (157.5 > difference && difference > 112.5) {  // Backwards+Left
                KeyBinding.setKeyBindState(Keyboard.KEY_S, true);
                KeyBinding.setKeyBindState(Keyboard.KEY_D, true);
            }
        } catch (Exception e) {
            sendDebugMessage("Error in pressKeys: " + e.getMessage());
        }
    }

    // This checks if the player is on any nodes further in the queue, which means the player, due to probably high speed, has skipped some. Then
    // this removes the nodes behind it and sets it as the current target.
    private WalkTarget onTarget() {
        // Add null check for path
        if (path == null || path.isEmpty()) {
            return null;
        }

        for (int i = 0; i < path.size(); i++) {
            PathElm elm = path.get(i);
            
            try {
                if (elm != null && elm.playerOn(Minecraft.getMinecraft().thePlayer.getPositionVector())) {
                    sendDebugMessage("Player on path element: " + elm);
    
                    if (currentTarget != null && elm == currentTarget.getElm())
                        return null;
    
                    // Get the next one if the player is on it
                    // if its travel vector, we don't get the next one, cos we need to go to the dest.
                    // if its jump, we don't get the next one, cos we need to jump.
                    if (path.size() > i + 1 && !(elm instanceof TravelVector) && !(elm instanceof JumpNode)) {
                        sendDebugMessage("Clearing path to next element");
                        path.subList(0, i + 1).clear();
                    } else {
                        path.subList(0, i).clear();
                    }
    
                    // cutting off might end jump target so stop jumping
                    KeyBinding.setKeyBindState(Keyboard.KEY_SPACE, false);
    
                    // Add check to ensure path is not empty after clearing
                    if (path.isEmpty()) {
                        return null;
                    }
    
                    return getCurrentTarget(path.get(0));
                }
            } catch (Exception e) {
                sendDebugMessage("Error checking if player is on path element: " + e.getMessage());
                // Continue with the loop even if one element fails
            }
        }

        return null;
    }

    // The return value of this is if the node has been satisfied, and the next one should be polled.
    private boolean tick(WalkTarget current) {
        if (current == null) {
            return false;
        }

        try {
            // We should improve the predicted motion calculation. Right now it's based on the estimate that the motion will last for 12 ticks, but this is different across speeds.
            Vec3 offset = new Vec3(Minecraft.getMinecraft().thePlayer.motionX, 0, Minecraft.getMinecraft().thePlayer.motionZ);
            Vec3 temp = offset;
            offset = offset.add(temp);

            for (int i = 0; i < 12; i++) {
                // 0.54600006f is how much the motion stops after every tick after not moving.
                offset = offset.add((temp = Util.vecMultiply(temp, 0.54600006f)));
            }

            return current.tick(offset, Minecraft.getMinecraft().thePlayer.getPositionVector());
        } catch (Exception e) {
            sendDebugMessage("Error in tick: " + e.getMessage());
            return false;
        }
    }

    private WalkTarget getCurrentTarget(PathElm elm) {
        if (elm == null) {
            return null;
        }
        
        try {
            if (elm instanceof FallNode)
                return new FallTarget((FallNode) elm);
            if (elm instanceof TravelNode)
                return new TravelTarget((TravelNode) elm);
            if (elm instanceof TravelVector)
                return new TravelVectorTarget((TravelVector) elm);
            if (elm instanceof JumpNode) {
                if (path.size() > 1)
                    return new JumpTarget((JumpNode) elm, getCurrentTarget(path.get(1)));
                return new JumpTarget((JumpNode) elm, null);
            }
            sendDebugMessage("Unknown path element type: " + elm.getClass().getSimpleName());
            return null;
        } catch (Exception e) {
            sendDebugMessage("Error getting current target: " + e.getMessage());
            return null;
        }
    }
    
    public boolean isActive() {
        return isActive;
    }

    public static Walker getInstance() {
        return instance;
    }

    public void cancel() {
        isActive = false;
        currentTarget = null;
        
        if (path != null) {
            path.clear();
        } else {
            path = new ArrayList<>();
        }
        
        // Release all keys
        releaseAllKeys();
        KeyBinding.setKeyBindState(Keyboard.KEY_SPACE, false);
        
        // Cancel look manager
        try {
            LookManager instance = LookManager.getInstance();
            if (instance != null) {
                instance.cancel();
            }
        } catch (Exception e) {
            sendDebugMessage("Error cancelling look manager: " + e.getMessage());
        }
        
        // Clear any rendered paths
        clearRenderedPath();
    }
    
    /**
     * Clear the rendered path without cancelling active pathfinding
     */
    public void clearRenderedPath() {
        try {
            PathRenderer renderer = PathRenderer.getInstance();
            if (renderer != null) {
                renderer.render(new ArrayList<>());
            }
        } catch (Exception e) {
            sendDebugMessage("Error clearing path renderer: " + e.getMessage());
        }
    }
}

