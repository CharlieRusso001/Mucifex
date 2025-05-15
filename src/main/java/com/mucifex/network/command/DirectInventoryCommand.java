package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

/**
 * Command to directly manipulate inventory slots bypassing Minecraft's click handling
 */
public class DirectInventoryCommand implements PlayerCommand {
    private final int slotId;
    private final String action;
    
    /**
     * Create a direct inventory command
     * @param slotId The slot ID to manipulate (0-based index)
     * @param action The action to perform: "pickup", "drop", "move"
     */
    public DirectInventoryCommand(int slotId, String action) {
        this.slotId = slotId;
        this.action = action.toLowerCase();
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
            EntityPlayer player = mc.thePlayer;
            
            switch (action) {
                case "move":
                    // Attempt to directly move an item from one inventory to another
                    moveItemToPlayerInventory(slot, player);
                    break;
                    
                case "pickup":
                    // Pick up item from slot (similar to left click)
                    pickupItem(slot, player);
                    break;
                    
                case "drop":
                    // Drop the item in the slot outside the inventory
                    dropItem(slot, player);
                    break;
                    
                default:
                    Mucifex.LOGGER.error("Unknown direct inventory action: " + action);
            }
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error executing direct inventory command: " + e.getMessage());
        }
    }
    
    /**
     * Pick up item from a slot to the cursor
     */
    private void pickupItem(Slot slot, EntityPlayer player) {
        try {
            ItemStack slotStack = slot.getStack();
            if (slotStack == null) {
                sendChatMessage("§c[Mucifex] §fNo item in this slot");
                return;
            }
            
            // Log information about the item
            Mucifex.LOGGER.info("Picking up " + slotStack.getDisplayName() + " from slot " + slotId);
            sendChatMessage("§a[Mucifex] §fPicked up §e" + slotStack.getDisplayName() + "§f from slot §e" + slotId);

            // Try to set the held item directly
            player.inventory.setItemStack(slotStack.copy());
            
            // Clear the slot
            slot.putStack(null);
            
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error picking up item: " + e.getMessage());
        }
    }
    
    /**
     * Drop the item in the slot
     */
    private void dropItem(Slot slot, EntityPlayer player) {
        try {
            ItemStack slotStack = slot.getStack();
            if (slotStack == null) {
                sendChatMessage("§c[Mucifex] §fNo item in this slot");
                return;
            }
            
            // Log information about the item
            Mucifex.LOGGER.info("Dropping " + slotStack.getDisplayName() + " from slot " + slotId);
            sendChatMessage("§a[Mucifex] §fDropped §e" + slotStack.getDisplayName() + "§f from slot §e" + slotId);
            
            // Create a copy of the item for dropping
            ItemStack dropStack = slotStack.copy();
            
            // Clear the slot
            slot.putStack(null);
            
            // Drop the item in the world
            player.dropPlayerItemWithRandomChoice(dropStack, true);
            
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error dropping item: " + e.getMessage());
        }
    }
    
    /**
     * Move an item to the player's inventory 
     * (This is a basic implementation - a complete one would need to handle different container types)
     */
    private void moveItemToPlayerInventory(Slot slot, EntityPlayer player) {
        try {
            ItemStack slotStack = slot.getStack();
            if (slotStack == null) {
                sendChatMessage("§c[Mucifex] §fNo item in this slot");
                return;
            }
            
            // Log information about the item
            Mucifex.LOGGER.info("Moving " + slotStack.getDisplayName() + " from slot " + slotId);
            
            // Check if we're moving from player inventory to container or vice versa
            boolean isPlayerInventorySlot = slot.inventory instanceof InventoryPlayer;
            
            // Create a copy of the stack to move
            ItemStack moveStack = slotStack.copy();
            int originalSize = moveStack.stackSize;
            
            if (isPlayerInventorySlot) {
                // Moving from player inventory to container
                // Find first empty slot in container that's not player inventory
                Container container = player.openContainer;
                boolean moved = false;
                
                // Try to find a non-player slot that can accept this item
                for (int i = 0; i < container.inventorySlots.size(); i++) {
                    Slot targetSlot = (Slot) container.inventorySlots.get(i);
                    
                    // Skip player inventory slots
                    if (targetSlot.inventory instanceof InventoryPlayer) {
                        continue;
                    }
                    
                    // Check if this slot can accept the item
                    if (targetSlot.isItemValid(moveStack) && targetSlot.getStack() == null) {
                        targetSlot.putStack(moveStack);
                        slot.putStack(null);
                        moved = true;
                        sendChatMessage("§a[Mucifex] §fMoved §e" + moveStack.getDisplayName() + 
                                        "§f from slot §e" + slotId + "§f to container slot §e" + i);
                        break;
                    }
                }
                
                if (!moved) {
                    sendChatMessage("§c[Mucifex] §fCould not find a valid slot in container");
                }
            } else {
                // Moving from container to player inventory
                // Try to add to player's inventory
                boolean added = player.inventory.addItemStackToInventory(moveStack);
                
                if (added) {
                    // If we successfully added to inventory, remove from source slot
                    if (moveStack.stackSize <= 0) {
                        // Full stack was moved
                        slot.putStack(null);
                        sendChatMessage("§a[Mucifex] §fMoved §e" + originalSize + "x " + moveStack.getDisplayName() + 
                                        "§f from slot §e" + slotId + "§f to player inventory");
                    } else {
                        // Partial stack was moved
                        int movedAmount = originalSize - moveStack.stackSize;
                        slotStack.stackSize = moveStack.stackSize;
                        slot.putStack(slotStack);
                        sendChatMessage("§a[Mucifex] §fMoved §e" + movedAmount + "x " + moveStack.getDisplayName() + 
                                        "§f from slot §e" + slotId + "§f to player inventory");
                    }
                } else {
                    sendChatMessage("§c[Mucifex] §fCould not move item to player inventory - no space");
                }
            }
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error moving item: " + e.getMessage());
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