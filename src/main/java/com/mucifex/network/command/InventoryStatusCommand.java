package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

/**
 * Command to get information about the currently open inventory
 */
public class InventoryStatusCommand implements PlayerCommand {
    
    @Override
    public void execute() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            
            // Check if a container is open
            if (mc.thePlayer.openContainer == null) {
                sendChatMessage("§c[Mucifex] §fNo container is currently open");
                return;
            }
            
            Container container = mc.thePlayer.openContainer;
            sendChatMessage("§a§l[Inventory Status]");
            sendChatMessage("§a• §fContainer type: §e" + container.getClass().getSimpleName());
            sendChatMessage("§a• §fContainer size: §e" + container.inventorySlots.size() + " §7slots");
            
            // Show items in container
            int itemCount = 0;
            StringBuilder itemSlots = new StringBuilder();
            
            for (int i = 0; i < container.inventorySlots.size(); i++) {
                Slot slot = (Slot) container.inventorySlots.get(i);
                ItemStack stack = slot.getStack();
                
                if (stack != null) {
                    itemCount++;
                    if (itemSlots.length() > 0) {
                        itemSlots.append(", ");
                    }
                    itemSlots.append("§e").append(i).append("§7:§f").append(stack.getDisplayName());
                    
                    // Limit to avoid chat overflow
                    if (itemSlots.length() > 100 && i < container.inventorySlots.size() - 1) {
                        itemSlots.append(", §7...");
                        break;
                    }
                }
            }
            
            sendChatMessage("§a• §fItems found: §e" + itemCount);
            if (itemCount > 0) {
                sendChatMessage("§a• §fSlots with items: §7" + itemSlots.toString());
            }
            
            sendChatMessage("§a• §fUse §7click,slotId§f to click on a slot");
            
            Mucifex.LOGGER.info("Displayed inventory status to player");
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error executing inventory status: " + e.getMessage());
        }
    }
    
    /**
     * Send a chat message to the player
     */
    private void sendChatMessage(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(message));
        }
    }
} 