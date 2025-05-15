package com.mucifex.network.servers;

import com.mucifex.Mucifex;
import com.mucifex.network.SocketManager;
import com.mucifex.network.command.*;

/**
 * Socket server for inventory interactions
 */
public class InventorySocketServer extends BaseSocketServer {
    
    public InventorySocketServer(int port, SocketManager manager) {
        super(port, manager);
    }
    
    @Override
    protected void processMessage(String message) {
        Mucifex.LOGGER.info("Received inventory command: " + message);
        try {
            String[] parts = message.toLowerCase().split(",");
            String action = parts[0].trim();
            
            switch (action) {
                case "click": 
                    // Format: click,slotId[,type]
                    // type can be: left (default), right, shift
                    int slotId = Integer.parseInt(parts[1].trim());
                    String clickType = parts.length > 2 ? parts[2].trim() : "left";
                    manager.queueCommand(new InventoryClickCommand(slotId, clickType));
                    break;
                    
                case "direct":
                    // Format: direct,slotId,action
                    // action can be: move, pickup, drop
                    // This is a more aggressive approach to inventory manipulation
                    int directSlotId = Integer.parseInt(parts[1].trim());
                    String directAction = parts.length > 2 ? parts[2].trim() : "move";
                    Mucifex.LOGGER.info("Using direct inventory manipulation: " + directAction + " on slot " + directSlotId);
                    manager.queueCommand(new DirectInventoryCommand(directSlotId, directAction));
                    break;
                    
                case "try_all":
                    // Format: try_all,slotId
                    // Try all possible methods to interact with this slot
                    // This is for when you're desperate and nothing else works
                    int trySlotId = Integer.parseInt(parts[1].trim());
                    Mucifex.LOGGER.info("Trying all methods on slot " + trySlotId);
                    
                    // Queue multiple commands with slight delays between them
                    // First try direct move
                    manager.queueCommand(new DirectInventoryCommand(trySlotId, "move"));
                    
                    // Then try regular clicks
                    manager.queueCommand(new DelayCommand(200, new InventoryClickCommand(trySlotId, "shift")));
                    manager.queueCommand(new DelayCommand(400, new InventoryClickCommand(trySlotId, "left")));
                    manager.queueCommand(new DelayCommand(600, new InventoryClickCommand(trySlotId, "right")));
                    
                    break;
                    
                case "open_inventory":
                    // Format: open_inventory
                    // Presses E to open inventory
                    manager.queueCommand(new KeyPressCommand("inventory"));
                    break;
                    
                case "close":
                    // Format: close
                    // Presses ESC to close current GUI
                    manager.queueCommand(new KeyPressCommand("escape"));
                    break;
                    
                case "drag":
                    // Format: drag,sourceSlot,targetSlot
                    // Drag item from source to target slot
                    int sourceSlot = Integer.parseInt(parts[1].trim());
                    int targetSlot = Integer.parseInt(parts[2].trim());
                    manager.queueCommand(new InventoryDragCommand(sourceSlot, targetSlot));
                    break;
                    
                case "status":
                    // Format: status
                    // Get info about current open container
                    manager.queueCommand(new InventoryStatusCommand());
                    break;
                    
                default:
                    Mucifex.LOGGER.error("Unknown inventory action: " + action);
            }
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error processing inventory command: " + e.getMessage());
        }
    }
    
    @Override
    protected String getServerType() {
        return "Inventory";
    }
} 