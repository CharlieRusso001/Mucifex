package com.mucifex.network.servers;

import com.mucifex.Mucifex;
import com.mucifex.network.SocketManager;
import com.mucifex.network.command.SendChatCommand;

/**
 * Socket server for sending chat messages
 */
public class ChatSocketServer extends BaseSocketServer {
    
    public ChatSocketServer(int port, SocketManager manager) {
        super(port, manager);
    }
    
    @Override
    protected void processMessage(String message) {
        Mucifex.LOGGER.info("Received chat message: " + message);
        // Queue the chat message to be sent on the main Minecraft thread
        manager.queueCommand(new SendChatCommand(message));
    }
    
    @Override
    protected String getServerType() {
        return "Chat";
    }
} 