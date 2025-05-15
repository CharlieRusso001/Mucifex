package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Command to press a specific key
 */
public class KeyPressCommand implements PlayerCommand {
    private final String keyName;
    private final int duration;
    
    public KeyPressCommand(String keyName) {
        this(keyName, 100); // Default duration of 100ms
    }
    
    public KeyPressCommand(String keyName, int duration) {
        this.keyName = keyName;
        this.duration = duration;
    }
    
    @Override
    public void execute() {
        try {
            KeyBinding keyBinding = getKeyBindingForName();
            
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
                
                Mucifex.LOGGER.info("Pressed key: " + keyName + " for " + duration + "ms");
            } else {
                // Special case for keys not bound to Minecraft controls
                int keyCode = getKeyCodeForName();
                if (keyCode != -1) {
                    // Simulate direct key press
                    KeyBinding.setKeyBindState(keyCode, true);
                    
                    // Schedule key release after duration
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            KeyBinding.setKeyBindState(keyCode, false);
                        }
                    }, duration);
                    
                    Mucifex.LOGGER.info("Pressed key: " + keyName + " (code: " + keyCode + ") for " + duration + "ms");
                } else {
                    Mucifex.LOGGER.error("Unknown key: " + keyName);
                }
            }
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error pressing key: " + e.getMessage());
        }
    }
    
    /**
     * Get the KeyBinding for the named key
     */
    private KeyBinding getKeyBindingForName() {
        Minecraft mc = Minecraft.getMinecraft();
        
        switch (keyName.toLowerCase()) {
            case "inventory":
                return mc.gameSettings.keyBindInventory;
            case "escape":
                // Escape is special as it's not a standard keybind
                return null;
            case "forward":
                return mc.gameSettings.keyBindForward;
            case "backward":
            case "back":
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
    
    /**
     * Get the key code for named keys that aren't standard Minecraft bindings
     */
    private int getKeyCodeForName() {
        switch (keyName.toLowerCase()) {
            case "escape":
                return Keyboard.KEY_ESCAPE;
            case "e":
                return Keyboard.KEY_E;
            default:
                // Try to parse as a keyboard key name
                try {
                    return Keyboard.getKeyIndex(keyName.toUpperCase());
                } catch (Exception e) {
                    return -1;
                }
        }
    }
} 