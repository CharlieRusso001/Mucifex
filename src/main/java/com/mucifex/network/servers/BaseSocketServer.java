package com.mucifex.network.servers;

import com.mucifex.Mucifex;
import com.mucifex.network.SocketManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.InetAddress;

/**
 * Base class for all socket servers
 */
public abstract class BaseSocketServer implements Runnable {
    protected final int port;
    protected final SocketManager manager;
    protected ServerSocket serverSocket;
    protected Thread serverThread;
    protected volatile boolean running = false;
    
    public BaseSocketServer(int port, SocketManager manager) {
        this.port = port;
        this.manager = manager;
    }
    
    /**
     * Starts the socket server
     */
    public void start() {
        if (running) return;
        
        try {
            // Explicitly bind to loopback address (127.0.0.1) for better cross-platform compatibility
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
            running = true;
            serverThread = new Thread(this);
            serverThread.setName("Mucifex-" + getServerType() + "-" + port);
            serverThread.setDaemon(true); // Make it a daemon thread so it doesn't prevent Minecraft from closing
            serverThread.start();
            Mucifex.LOGGER.info(getServerType() + " server started on port " + port);
        } catch (IOException e) {
            Mucifex.LOGGER.error("Failed to start " + getServerType() + " server: " + e.getMessage());
        }
    }
    
    /**
     * Stops the socket server
     */
    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
                Mucifex.LOGGER.info(getServerType() + " server stopped");
            } catch (IOException e) {
                Mucifex.LOGGER.error("Error stopping " + getServerType() + " server: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void run() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            } catch (SocketException e) {
                if (running) {
                    Mucifex.LOGGER.error("Socket error in " + getServerType() + " server: " + e.getMessage());
                }
                // If we're not running, this is expected during shutdown
            } catch (IOException e) {
                if (running) {
                    Mucifex.LOGGER.error("IO error in " + getServerType() + " server: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Handles a client connection
     */
    protected void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                processMessage(inputLine);
            }
        } catch (IOException e) {
            Mucifex.LOGGER.error("Error handling client in " + getServerType() + " server: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Mucifex.LOGGER.error("Error closing client socket: " + e.getMessage());
            }
        }
    }
    
    /**
     * Process a message from a client
     */
    protected abstract void processMessage(String message);
    
    /**
     * Get the type of this server for logging
     */
    protected abstract String getServerType();
} 