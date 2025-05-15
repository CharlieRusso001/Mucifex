package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

/**
 * Command to change where the player is looking
 */
public class LookCommand implements PlayerCommand {
    private final boolean isYawPitch;
    private float yaw;
    private float pitch;
    private double x;
    private double y;
    private double z;
    
    /**
     * Look with specific yaw and pitch
     */
    public LookCommand(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.isYawPitch = true;
    }
    
    /**
     * Look at a specific position
     */
    public LookCommand(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.isYawPitch = false;
    }
    
    @Override
    public void execute() {
        try {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            
            if (isYawPitch) {
                // Set yaw and pitch directly
                player.rotationYaw = yaw;
                player.rotationPitch = pitch;
                Mucifex.LOGGER.info("Player looking at yaw=" + yaw + ", pitch=" + pitch);
            } else {
                // Calculate yaw and pitch to look at coordinates
                double dX = x - player.posX;
                double dY = y - player.posY - player.getEyeHeight();
                double dZ = z - player.posZ;
                
                double distance = Math.sqrt(dX * dX + dZ * dZ);
                float newYaw = (float) Math.toDegrees(Math.atan2(dZ, dX)) - 90F;
                float newPitch = (float) -Math.toDegrees(Math.atan2(dY, distance));
                
                player.rotationYaw = newYaw;
                player.rotationPitch = newPitch;
                
                Mucifex.LOGGER.info("Player looking at x=" + x + ", y=" + y + ", z=" + z);
            }
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error setting player look direction: " + e.getMessage());
        }
    }
} 