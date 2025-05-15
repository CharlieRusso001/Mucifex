package com.mucifex.network.servers;

import com.mucifex.Mucifex;
import com.mucifex.network.SocketManager;
import com.mucifex.network.command.MovePlayerCommand;
import com.mucifex.network.command.RightClickCommand;

/**
 * Socket server for player movement (WASD keys) and mouse actions
 */
public class MovementSocketServer extends BaseSocketServer {
    
    public MovementSocketServer(int port, SocketManager manager) {
        super(port, manager);
    }
    
    @Override
    protected void processMessage(String message) {
        Mucifex.LOGGER.info("Received movement command: " + message);
        try {
            // Check if it's a right-click command
            if (message.startsWith("right_click")) {
                // Format: right_click,duration (optional)
                String[] parts = message.split(",");
                int duration = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 100;
                manager.queueCommand(new RightClickCommand(duration));
                return;
            }
            
            // Regular movement command
            // Format: direction,duration
            // direction: forward, backward, left, right, jump, sneak
            // duration: how long to press the key in milliseconds (optional, default 100ms)
            String[] parts = message.split(",");
            String direction = parts[0].trim().toLowerCase();
            int duration = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 100;
            
            manager.queueCommand(new MovePlayerCommand(direction, duration));
        } catch (Exception e) {
            Mucifex.LOGGER.error("Invalid movement command format: " + message);
        }
    }
    
    @Override
    protected String getServerType() {
        return "Movement";
    }
} 