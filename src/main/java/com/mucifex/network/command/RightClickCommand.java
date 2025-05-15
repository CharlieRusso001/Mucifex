package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Command to simulate a right-click action
 */
public class RightClickCommand implements PlayerCommand {
    private final int duration;
    
    public RightClickCommand(int duration) {
        this.duration = duration;
    }
    
    @Override
    public void execute() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            
            // Get the use/right-click key binding
            KeyBinding keyUse = mc.gameSettings.keyBindUseItem;
            
            Mucifex.LOGGER.info("Attempting right-click with multiple methods...");
            boolean success = false;
            
            // Method 1: Try direct method call first (most reliable)
            try {
                // Try multiple possible method names (obfuscated and deobfuscated)
                String[] methodNames = {
                    "rightClickMouse", "func_147121_ag", "ag"
                };
                
                for (String methodName : methodNames) {
                    try {
                        Method rightClickMethod = Minecraft.class.getDeclaredMethod(methodName);
                        rightClickMethod.setAccessible(true);
                        rightClickMethod.invoke(mc);
                        Mucifex.LOGGER.info("Successfully executed right-click using direct method call: " + methodName);
                        success = true;
                        break;
                    } catch (NoSuchMethodException e) {
                        // Method name not found, try next one
                        continue;
                    } catch (Exception e) {
                        Mucifex.LOGGER.error("Error calling " + methodName + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Mucifex.LOGGER.error("Error trying to find rightClickMouse method: " + e.getMessage());
            }
            
            // Method 2: Use the mouse button directly
            if (!success) {
                try {
                    // Simulate pressing the right mouse button directly
                    Mucifex.LOGGER.info("Trying to set right mouse button state directly");
                    
                    // This uses mouseButton = 1 for right click
                    Method mouseButtonMethod = null;
                    try {
                        // Look for methods like processMouseBinds, mouseClick, etc.
                        for (Method method : Minecraft.class.getDeclaredMethods()) {
                            if (method.getName().contains("mouse") || method.getName().contains("Mouse")) {
                                Mucifex.LOGGER.info("Found potential mouse method: " + method.getName());
                            }
                        }
                        
                        // Try to call Mouse.isButtonDown with reflection to check if it's working
                        Class<?> mouseClass = Class.forName("org.lwjgl.input.Mouse");
                        Method isButtonDownMethod = mouseClass.getDeclaredMethod("isButtonDown", int.class);
                        boolean rightButtonDown = (boolean) isButtonDownMethod.invoke(null, 1);
                        Mucifex.LOGGER.info("Current right button state: " + rightButtonDown);
                    } catch (Exception e) {
                        Mucifex.LOGGER.error("Error inspecting mouse state: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Mucifex.LOGGER.error("Error setting mouse button state: " + e.getMessage());
                }
            }
            
            // Method 3: Fall back to key simulation as a last resort
            Mucifex.LOGGER.info("Simulating key press for right-click (use item) key");
            KeyBinding.setKeyBindState(keyUse.getKeyCode(), true);
            
            // Schedule releasing the button after the specified duration
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    KeyBinding.setKeyBindState(keyUse.getKeyCode(), false);
                    Mucifex.LOGGER.info("Released right-click key after " + duration + "ms");
                }
            }, duration);
            
            Mucifex.LOGGER.info("Executed right-click for " + duration + "ms");
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error executing right-click: " + e.getMessage());
        }
    }
} 