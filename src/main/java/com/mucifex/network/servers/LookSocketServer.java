package com.mucifex.network.servers;

import com.mucifex.Mucifex;
import com.mucifex.network.SocketManager;
import com.mucifex.network.command.LookCommand;

/**
 * Socket server for controlling player's head direction
 */
public class LookSocketServer extends BaseSocketServer {
    
    public LookSocketServer(int port, SocketManager manager) {
        super(port, manager);
    }
    
    @Override
    protected void processMessage(String message) {
        Mucifex.LOGGER.info("Received look command: " + message);
        try {
            // Format: yaw,pitch  OR  x,y,z (to look at position)
            String[] parts = message.split(",");
            if (parts.length == 2) {
                // yaw,pitch format
                float yaw = Float.parseFloat(parts[0].trim());
                float pitch = Float.parseFloat(parts[1].trim());
                manager.queueCommand(new LookCommand(yaw, pitch));
            } else if (parts.length == 3) {
                // x,y,z format (look at position)
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());
                manager.queueCommand(new LookCommand(x, y, z));
            } else {
                Mucifex.LOGGER.error("Invalid look command format: " + message);
            }
        } catch (Exception e) {
            Mucifex.LOGGER.error("Invalid look command format: " + message);
        }
    }
    
    @Override
    protected String getServerType() {
        return "Look";
    }
} 