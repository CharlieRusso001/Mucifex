package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Command to move the player by simulating key presses
 */
public class MovePlayerCommand implements PlayerCommand {
    private final String direction;
    private final int duration;
    
    public MovePlayerCommand(String direction, int duration) {
        this.direction = direction;
        this.duration = duration;
    }
    
    @Override
    public void execute() {
        try {
            KeyBinding keyBinding = getKeyBindingForDirection();
            if (keyBinding != null) {
                // Press the key down
                KeyBinding.setKeyBindState(keyBinding.getKeyCode(), true);
                
                // Schedule key release after duration
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        KeyBinding.setKeyBindState(keyBinding.getKeyCode(), false);
                    }
                }, duration);
                
                Mucifex.LOGGER.info("Moving player " + direction + " for " + duration + "ms");
            } else {
                Mucifex.LOGGER.error("Unknown movement direction: " + direction);
            }
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error moving player: " + e.getMessage());
        }
    }
    
    private KeyBinding getKeyBindingForDirection() {
        Minecraft mc = Minecraft.getMinecraft();
        switch (direction) {
            case "forward":
                return mc.gameSettings.keyBindForward;
            case "backward":
                return mc.gameSettings.keyBindBack;
            case "left":
                return mc.gameSettings.keyBindLeft;
            case "right":
                return mc.gameSettings.keyBindRight;
            case "jump":
                return mc.gameSettings.keyBindJump;
            case "sneak":
                return mc.gameSettings.keyBindSneak;
            default:
                return null;
        }
    }
} 