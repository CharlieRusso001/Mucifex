package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;

/**
 * Command to drag an item from one slot to another
 */
public class InventoryDragCommand implements PlayerCommand {
    private final int sourceSlot;
    private final int targetSlot;
    
    /**
     * Create a new inventory drag command
     * @param sourceSlot The source slot ID (0-based index)
     * @param targetSlot The target slot ID (0-based index)
     */
    public InventoryDragCommand(int sourceSlot, int targetSlot) {
        this.sourceSlot = sourceSlot;
        this.targetSlot = targetSlot;
    }
    
    @Override
    public void execute() {
        // To drag an item, we need to:
        // 1. Click the source slot (pick up the item)
        // 2. Click the target slot (place the item)
        
        try {
            Minecraft mc = Minecraft.getMinecraft();
            
            // Check if a container is open
            if (mc.thePlayer.openContainer == null) {
                Mucifex.LOGGER.error("No container is currently open");
                return;
            }
            
            Container container = mc.thePlayer.openContainer;
            if (sourceSlot < 0 || sourceSlot >= container.inventorySlots.size() ||
                targetSlot < 0 || targetSlot >= container.inventorySlots.size()) {
                Mucifex.LOGGER.error("Invalid slot ID. Valid range: 0-" + (container.inventorySlots.size() - 1));
                return;
            }
            
            // Perform the source click with a slight delay between clicks
            new InventoryClickCommand(sourceSlot, "left").execute();
            
            // Wait a small amount of time using a simple sleep
            // This is a bit of a hack, but necessary to make sure the first click registers
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }
            
            // Perform the target click
            new InventoryClickCommand(targetSlot, "left").execute();
            
            Mucifex.LOGGER.info("Dragged item from slot " + sourceSlot + " to slot " + targetSlot);
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error executing inventory drag: " + e.getMessage());
        }
    }
} 