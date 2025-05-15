package com.mucifex.network;

import com.mucifex.Mucifex;
import com.mucifex.network.servers.*;
import com.mucifex.network.command.*;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages all socket servers for the Mucifex mod
 */
public class SocketManager {
    // Default ports for different functions
    public static final int CHAT_PORT = 25560;
    public static final int COMMAND_PORT = 25561;
    public static final int MOVEMENT_PORT = 25562;
    public static final int LOOK_PORT = 25563;
    public static final int INVENTORY_PORT = 25564;
    public static final int CHAT_RECEIVER_PORT = 25565;
    
    private ChatSocketServer chatServer;
    private CommandSocketServer commandServer;
    private MovementSocketServer movementServer;
    private LookSocketServer lookServer;
    private InventorySocketServer inventoryServer;
    private ChatReceiverServer chatReceiverServer;
    
    // Queue for commands to be processed on the main Minecraft thread
    private final Queue<PlayerCommand> commandQueue = new ConcurrentLinkedQueue<>();
    
    public SocketManager() {
        chatServer = new ChatSocketServer(CHAT_PORT, this);
        commandServer = new CommandSocketServer(COMMAND_PORT, this);
        movementServer = new MovementSocketServer(MOVEMENT_PORT, this);
        lookServer = new LookSocketServer(LOOK_PORT, this);
        inventoryServer = new InventorySocketServer(INVENTORY_PORT, this);
        chatReceiverServer = new ChatReceiverServer(CHAT_RECEIVER_PORT, this);
    }
    
    /**
     * Starts all socket servers
     */
    public void startAllServers() {
        chatServer.start();
        commandServer.start();
        movementServer.start();
        lookServer.start();
        inventoryServer.start();
        chatReceiverServer.start();
        Mucifex.LOGGER.info("All socket servers started");
    }
    
    /**
     * Stops all socket servers
     */
    public void stopAllServers() {
        chatServer.stop();
        commandServer.stop();
        movementServer.stop();
        lookServer.stop();
        inventoryServer.stop();
        chatReceiverServer.stop();
        Mucifex.LOGGER.info("All socket servers stopped");
    }
    
    /**
     * Adds a command to the queue for processing on the main Minecraft thread
     */
    public void queueCommand(PlayerCommand command) {
        commandQueue.add(command);
    }
    
    /**
     * Processes all queued commands on the main Minecraft thread
     * Called from the client tick event
     */
    public void processCommands() {
        PlayerCommand command;
        while ((command = commandQueue.poll()) != null) {
            try {
                command.execute();
            } catch (Exception e) {
                Mucifex.LOGGER.error("Error executing command: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handles a chat message received by the client and forwards it to the chat receiver server
     */
    public void handleChatMessage(ClientChatReceivedEvent event) {
        chatReceiverServer.handleChatMessage(event);
    }
} 