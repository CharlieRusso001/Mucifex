package com.mucifex.network.servers;

import com.mucifex.Mucifex;
import com.mucifex.network.SocketManager;
import com.mucifex.network.command.CommandExecuteCommand;
import com.mucifex.network.command.KeyPressCommand;

/**
 * Socket server for executing Minecraft commands
 */
public class CommandSocketServer extends BaseSocketServer {
    
    public CommandSocketServer(int port, SocketManager manager) {
        super(port, manager);
    }
    
    @Override
    protected void processMessage(String message) {
        Mucifex.LOGGER.info("Received command: " + message);
        
        // Special case commands that don't need the /
        if (message.equalsIgnoreCase("esc") || message.equalsIgnoreCase("escape")) {
            // Handle escape key press to close menus
            manager.queueCommand(new KeyPressCommand("escape"));
            Mucifex.LOGGER.info("Queued ESC key press to close menu");
            return;
        }
        
        // Remove / if it exists at the start of the command
        if (message.startsWith("/")) {
            message = message.substring(1);
        }
        
        // Queue the command for execution on the main thread
        manager.queueCommand(new CommandExecuteCommand(message));
    }
    
    @Override
    protected String getServerType() {
        return "Command";
    }
} 