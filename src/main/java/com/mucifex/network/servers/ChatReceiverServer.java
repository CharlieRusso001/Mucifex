package com.mucifex.network.servers;

import com.mucifex.Mucifex;
import com.mucifex.network.SocketManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Server that sends all chat messages received by the client to connected sockets
 */
public class ChatReceiverServer extends BaseSocketServer {
    
    // Queue for outgoing messages
    private final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();
    
    // List of active client connections
    private final List<ClientConnection> clientConnections = new ArrayList<>();
    
    // Socket for client connections
    private ServerSocket serverSocket;
    
    // Thread for accepting client connections
    private Thread connectionThread;
    
    // Flag to control the server
    private boolean running = false;
    
    public ChatReceiverServer(int port, SocketManager manager) {
        super(port, manager);
    }
    
    @Override
    public void start() {
        running = true;
        
        // Start the server socket
        try {
            // Explicitly bind to loopback address (127.0.0.1) for better cross-platform compatibility
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
            Mucifex.LOGGER.info("Started " + getServerType() + " Server on port " + port);
            
            // Start thread to accept connections
            connectionThread = new Thread(this::acceptConnections);
            connectionThread.setName("ChatReceiverConnection-Thread");
            connectionThread.start();
            
            // Start the message sending thread
            super.start();
        } catch (IOException e) {
            Mucifex.LOGGER.error("Failed to start " + getServerType() + " Server on port " + port, e);
        }
    }
    
    /**
     * Accept client connections in a loop
     */
    private void acceptConnections() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                Mucifex.LOGGER.info("New chat receiver client connected from " + clientSocket.getInetAddress());
                
                // Create and start a new client handler
                ClientConnection clientConnection = new ClientConnection(clientSocket);
                synchronized (clientConnections) {
                    clientConnections.add(clientConnection);
                }
                clientConnection.start();
            } catch (IOException e) {
                if (running) {
                    Mucifex.LOGGER.error("Error accepting chat receiver client connection", e);
                }
            }
        }
    }
    
    @Override
    public void stop() {
        running = false;
        
        // Close the server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Mucifex.LOGGER.error("Error closing chat receiver server socket", e);
        }
        
        // Close all client connections
        synchronized (clientConnections) {
            for (ClientConnection client : clientConnections) {
                client.close();
            }
            clientConnections.clear();
        }
        
        super.stop();
    }
    
    /**
     * Handle a chat message received by the client
     */
    public void handleChatMessage(ClientChatReceivedEvent event) {
        IChatComponent message = event.message;
        String formattedMessage = message.getUnformattedText();
        
        // Skip message-type events (0 is chat message, 1 is system message, 2 is game info)
        // We still want system messages, but can filter based on other criteria if needed
        
        Mucifex.LOGGER.debug("Chat message received: " + formattedMessage);
        
        // Add to queue
        messageQueue.add(formattedMessage);
        
        // Send to all connected clients
        synchronized (clientConnections) {
            for (ClientConnection client : clientConnections) {
                client.sendMessage(formattedMessage);
            }
        }
    }
    
    @Override
    protected void processMessage(String message) {
        // This server doesn't process incoming messages from the socket, it only sends out messages
        // But we'll implement a few special commands
        if (message.toLowerCase().startsWith("status")) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                synchronized (clientConnections) {
                    String status = "Chat Receiver Server Status: " + clientConnections.size() + " clients connected";
                    messageQueue.add(status);
                    
                    // Send to all connected clients
                    for (ClientConnection client : clientConnections) {
                        client.sendMessage(status);
                    }
                    
                    // Also display in-game
                    if (Minecraft.getMinecraft().thePlayer != null) {
                        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("§a[Mucifex] §f" + status));
                    }
                }
            });
        }
    }
    
    @Override
    protected String getServerType() {
        return "ChatReceiver";
    }
    
    /**
     * Class representing a client connection
     */
    private class ClientConnection {
        private final Socket socket;
        private PrintWriter out;
        private boolean active = true;
        
        public ClientConnection(Socket socket) {
            this.socket = socket;
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                Mucifex.LOGGER.error("Error creating client connection", e);
                active = false;
            }
        }
        
        public void start() {
            // Send a welcome message
            sendMessage("--- Mucifex Chat Receiver Connected ---");
            sendMessage("--- All in-game chat messages will be sent here ---");
        }
        
        public void sendMessage(String message) {
            if (active && out != null) {
                try {
                    out.println(message);
                    if (out.checkError()) {
                        close();
                    }
                } catch (Exception e) {
                    Mucifex.LOGGER.error("Error sending message to client", e);
                    close();
                }
            }
        }
        
        public void close() {
            active = false;
            try {
                if (out != null) {
                    out.close();
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                Mucifex.LOGGER.error("Error closing client connection", e);
            }
            
            // Remove from the list of connections
            synchronized (clientConnections) {
                clientConnections.remove(this);
            }
        }
    }
} 