package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Command to click on a specific slot in an inventory
 */
public class InventoryClickCommand implements PlayerCommand {
    private final int slotId;
    private final String clickType;
    
    /**
     * Create a new inventory click command
     * @param slotId The slot ID to click (0-based index)
     * @param clickType The type of click: "left", "right", or "shift"
     */
    public InventoryClickCommand(int slotId, String clickType) {
        this.slotId = slotId;
        this.clickType = clickType.toLowerCase();
    }
    
    @Override
    public void execute() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            
            // Check if a container is open
            if (mc.thePlayer.openContainer == null) {
                Mucifex.LOGGER.error("No container is currently open");
                return;
            }
            
            Container container = mc.thePlayer.openContainer;
            if (slotId < 0 || slotId >= container.inventorySlots.size()) {
                Mucifex.LOGGER.error("Invalid slot ID: " + slotId + ". Valid range: 0-" + (container.inventorySlots.size() - 1));
                return;
            }
            
            // Get the slot
            Slot slot = (Slot) container.inventorySlots.get(slotId);
            
            // Convert click type to parameters
            int mouseButton = getMouseButton();
            int clickMode = getClickMode();
            
            // Try multiple approaches to click the slot
            boolean success = false;
            
            // Log what we're about to do
            Mucifex.LOGGER.info("Attempting to click inventory slot " + slotId + " with " + clickType + 
                               " click (mouseButton=" + mouseButton + ", clickMode=" + clickMode + ")");
            
            // 1. Try the handleMouseClick method which is the most direct way to interact with slots
            // This method directly simulates what happens when a player clicks in the GUI
            try {
                // First try with specific method name for handleMouseClick
                String[] handleMouseClickNames = {
                    "handleMouseClick", "func_146984_a", "a"
                };
                
                for (String methodName : handleMouseClickNames) {
                    try {
                        Method handleMouseClickMethod = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredMethod(
                                methodName, Slot.class, int.class, int.class, int.class);
                        
                        handleMouseClickMethod.setAccessible(true);
                        
                        // If we have an open GUI container, use it directly
                        if (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer) {
                            net.minecraft.client.gui.inventory.GuiContainer gui = 
                                (net.minecraft.client.gui.inventory.GuiContainer) mc.currentScreen;
                            
                            handleMouseClickMethod.invoke(gui, slot, slotId, mouseButton, clickMode);
                            Mucifex.LOGGER.info("Successfully clicked inventory slot using GuiContainer." + methodName);
                            success = true;
                            break;
                        }
                    } catch (NoSuchMethodException e) {
                        // Method doesn't exist with this name, try next one
                    } catch (Exception e) {
                        Mucifex.LOGGER.error("Error clicking slot with GuiContainer." + methodName + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Mucifex.LOGGER.error("Error finding GuiContainer class: " + e.getMessage());
            }
            
            // 2. If that failed, try the windowClick methods in Minecraft class
            if (!success) {
                String[] windowClickNames = {
                    "windowClick", "func_78753_a", "a", // Possible names in different environments
                };
                
                for (String methodName : windowClickNames) {
                    try {
                        Method windowClickMethod = Minecraft.class.getDeclaredMethod(
                                methodName, 
                                int.class, int.class, int.class, int.class, net.minecraft.entity.player.EntityPlayer.class);
                        
                        windowClickMethod.setAccessible(true);
                        windowClickMethod.invoke(mc, 
                                container.windowId, 
                                slotId, 
                                mouseButton, 
                                clickMode, 
                                mc.thePlayer);
                        
                        Mucifex.LOGGER.info("Successfully clicked inventory slot using Minecraft." + methodName);
                        success = true;
                        break;
                    } catch (NoSuchMethodException e) {
                        // Method doesn't exist with this name, try next one
                        Mucifex.LOGGER.debug("Method " + methodName + " not found, trying alternatives");
                    } catch (Exception e) {
                        Mucifex.LOGGER.error("Error clicking slot with " + methodName + ": " + e.getMessage());
                    }
                }
            }
            
            // 3. If that failed, try Container methods
            if (!success) {
                String[] slotClickNames = {
                    "slotClick", "func_75144_a", "a", // Possible names in different environments
                };
                
                for (String methodName : slotClickNames) {
                    try {
                        Method slotClickMethod = Container.class.getDeclaredMethod(
                                methodName, int.class, int.class, int.class, net.minecraft.entity.player.EntityPlayer.class);
                        
                        slotClickMethod.setAccessible(true);
                        slotClickMethod.invoke(container, slotId, mouseButton, clickMode, mc.thePlayer);
                        
                        Mucifex.LOGGER.info("Successfully clicked inventory slot using Container." + methodName);
                        success = true;
                        break;
                    } catch (NoSuchMethodException e) {
                        // Method doesn't exist with this name, try next one
                        Mucifex.LOGGER.debug("Method " + methodName + " not found, trying alternatives");
                    } catch (Exception e) {
                        Mucifex.LOGGER.error("Error clicking slot with " + methodName + ": " + e.getMessage());
                    }
                }
            }
            
            // 4. Last resort - try direct interaction for GUI elements
            if (!success && "left".equals(clickType)) {
                try {
                    // Try to find any GUI-specific interaction method
                    // This is a simplified approach - in a real implementation we might need more
                    if (mc.currentScreen != null) {
                        // Get item in the slot for logging
                        ItemStack slotStack = slot.getStack();
                        String itemName = slotStack != null ? slotStack.getDisplayName() : "empty slot";
                        
                        Mucifex.LOGGER.info("Trying direct GUI interaction on " + itemName + " in slot " + slotId);
                        
                        // Simulate mouse click event on GUI
                        // We can't directly call mouseClicked as it's protected, but we can try
                        // to make the game think the mouse was clicked at this slot's position
                        
                        // Note: This is a basic implementation. A more complex one would calculate
                        // the exact screen coordinates for the slot.
                        int guiLeft = 0;
                        int guiTop = 0;
                        
                        // Try to extract guiLeft and guiTop from GuiContainer via reflection
                        if (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer) {
                            try {
                                net.minecraft.client.gui.inventory.GuiContainer gui = 
                                    (net.minecraft.client.gui.inventory.GuiContainer) mc.currentScreen;
                                
                                // Find guiLeft field (name may be obfuscated)
                                Field[] fields = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredFields();
                                for (Field field : fields) {
                                    field.setAccessible(true);
                                    if (field.getType() == int.class) {
                                        // Skip width, height, etc.
                                        if (field.getName().equals("width") || field.getName().equals("height") || 
                                            field.getName().equals("xSize") || field.getName().equals("ySize")) {
                                            continue;
                                        }
                                        
                                        // Try to get this int field and see if it could be guiLeft or guiTop
                                        int value = field.getInt(gui);
                                        if (value > 0 && value < 300) {  // Reasonable values for GUI position
                                            if (guiLeft == 0) {
                                                guiLeft = value;
                                            } else if (guiTop == 0) {
                                                guiTop = value;
                                                break;  // We have both values, can stop
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Mucifex.LOGGER.error("Error trying to find GUI position: " + e.getMessage());
                            }
                        }
                        
                        // If we got values, try to calculate the slot position
                        if (guiLeft > 0 && guiTop > 0) {
                            try {
                                // Estimate slot position
                                // This is a very crude estimation - actual slot positions depend on the specific GUI
                                int slotX = guiLeft + 8 + (slotId % 9) * 18;
                                int slotY = guiTop + 18 + (slotId / 9) * 18;
                                
                                Mucifex.LOGGER.info("Estimated slot position: " + slotX + ", " + slotY);
                                
                                // Try to find and call mouseClicked method
                                Method mouseClickedMethod = null;
                                for (Method method : mc.currentScreen.getClass().getDeclaredMethods()) {
                                    if (method.getName().equals("mouseClicked") || 
                                        method.getName().equals("func_73864_a") || 
                                        method.getName().equals("a")) {
                                        if (method.getParameterTypes().length == 3) {
                                            // Likely the mouseClicked(int, int, int) method
                                            mouseClickedMethod = method;
                                            break;
                                        }
                                    }
                                }
                                
                                if (mouseClickedMethod != null) {
                                    mouseClickedMethod.setAccessible(true);
                                    mouseClickedMethod.invoke(mc.currentScreen, slotX, slotY, 0); // 0 = left click
                                    Mucifex.LOGGER.info("Successfully simulated mouse click on GUI at " + slotX + ", " + slotY);
                                    success = true;
                                }
                            } catch (Exception e) {
                                Mucifex.LOGGER.error("Error attempting direct GUI click: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    Mucifex.LOGGER.error("Error in direct GUI interaction: " + e.getMessage());
                }
            }
            
            // 5. Report results
            if (success) {
                Mucifex.LOGGER.info("Successfully clicked inventory slot " + slotId + " with " + clickType + " click");
            } else {
                Mucifex.LOGGER.error("All attempts to click slot " + slotId + " failed. Check logs for details.");
            }
            
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error executing inventory click: " + e.getMessage());
        }
    }
    
    /**
     * Get the mouse button ID for the click type
     * @return 0 for left click, 1 for right click
     */
    private int getMouseButton() {
        switch (clickType) {
            case "right":
                return 1;
            case "left":
            case "shift":
            default:
                return 0;
        }
    }
    
    /**
     * Get the click mode
     * @return 0 for normal click, 1 for shift click
     */
    private int getClickMode() {
        switch (clickType) {
            case "shift":
                return 1;
            case "left":
            case "right":
            default:
                return 0;
        }
    }
} 