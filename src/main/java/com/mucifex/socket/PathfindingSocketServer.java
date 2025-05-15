package com.mucifex.socket;

import com.mucifex.pathfinding.internal.PathHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Socket server for pathfinding functionality
 * Listens on port 25566 for pathfinding commands in format: x,y,z
 */
public class PathfindingSocketServer {
    private static final int PORT = 25566; // New port for pathfinding
    private ServerSocket serverSocket;
    private boolean running = false;
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    private PathHandler pathHandler;
    
    // Flag to track if pathfinding is in progress
    private static final AtomicBoolean pathfindingInProgress = new AtomicBoolean(false);
    
    // Flag to track if we're currently initializing a new pathfinding request
    private static final AtomicBoolean initializingPathfinding = new AtomicBoolean(false);
    
    // Track the timestamp of the last pathfinding activity
    private static final AtomicLong lastPathfindingActivityTime = new AtomicLong(0);
    
    // Maximum time a pathfinding operation can be active without updates (in milliseconds)
    private static final long MAX_PATHFINDING_IDLE_TIME = 30000; // Increased from 10s to 30s
    
    // Maximum time the initialization state can be active (in milliseconds)
    private static final long MAX_INIT_TIME = 5000; // Increased from 3s to 5s
    
    public PathfindingSocketServer() {
        try {
            pathHandler = new PathHandler();
        } catch (Exception e) {
            System.err.println("Error initializing PathHandler in PathfindingSocketServer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            // Explicitly bind to loopback address (127.0.0.1) for better cross-platform compatibility
            serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"));
            running = true;
            System.out.println("Pathfinding socket server started on port " + PORT);
            
            // Main thread for accepting connections
            new Thread(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        threadPool.submit(() -> handleClient(clientSocket));
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("Error accepting client connection: " + e.getMessage());
                        }
                    }
                }
            }).start();
            
            // Add watchdog thread to check for stuck flags every 2 seconds
            new Thread(() -> {
                while (running) {
                    try {
                        Thread.sleep(2000); // Check every 2 seconds
                        checkPathfindingState();
                    } catch (InterruptedException e) {
                        // Ignore interruption
                    } catch (Exception e) {
                        System.err.println("Error in watchdog thread: " + e.getMessage());
                    }
                }
            }).start();
            
        } catch (IOException e) {
            System.err.println("Could not start pathfinding socket server: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String inputLine = in.readLine();
            if (inputLine != null) {
                String[] parts = inputLine.split(",");
                if (parts.length >= 3) {
                    try {
                        final int x = Integer.parseInt(parts[0]);
                        final int y = Integer.parseInt(parts[1]);
                        final int z = Integer.parseInt(parts[2]);
                        
                        // Prevent multiple initialization requests from occurring simultaneously
                        if (initializingPathfinding.get()) {
                            out.println("PATHFINDING:INITIALIZING");
                            System.out.println("Pathfinding request rejected: already initializing another pathfinding operation");
                            return;
                        }
                        
                        // Check if pathfinding is already in progress
                        if (pathfindingInProgress.get()) {
                            // Before rejecting, let's check if the pathfinding has been idle for too long
                            long currentTime = System.currentTimeMillis();
                            long lastActivity = lastPathfindingActivityTime.get();
                            
                            if (currentTime - lastActivity > MAX_PATHFINDING_IDLE_TIME) {
                                // It's been too long, force reset the state
                                System.out.println("Forcing reset of pathfinding state due to inactivity (" + 
                                    (currentTime - lastActivity) / 1000 + " seconds)");
                                pathfindingInProgress.set(false);
                                
                                // Continue with the new pathfinding request
                            } else {
                                out.println("PATHFINDING:BUSY");
                                System.out.println("Pathfinding request rejected: another pathfinding operation is in progress");
                                return;
                            }
                        }
                        
                        // Set initializing flag to prevent race conditions
                        initializingPathfinding.set(true);
                        // Update the last activity timestamp
                        lastPathfindingActivityTime.set(System.currentTimeMillis());
                        
                        // Call pathfinding system on the main Minecraft thread to prevent threading issues
                        Minecraft mc = Minecraft.getMinecraft();
                        if (mc != null && mc.thePlayer != null) {
                            System.out.println("Starting pathfinding to: " + x + ", " + y + ", " + z);
                            
                            // Schedule pathfinding on the main thread
                            mc.addScheduledTask(() -> {
                                try {
                                    // Make sure any previous pathfinding is completely cancelled first
                                    if (pathHandler != null) {
                                        // Cancel any existing pathfinding
                                        System.out.println("Cancelling any existing pathfinding...");
                                        pathHandler.cancel();
                                        
                                        // Sleep for a brief moment to ensure cancellation completes
                                        try {
                                            Thread.sleep(200);
                                        } catch (InterruptedException e) {
                                            // Ignore interruption
                                        }
                                        
                                        // Now we can start a new pathfinding session
                                        System.out.println("Now starting new pathfinding...");
                                        pathfindingInProgress.set(true);
                                        lastPathfindingActivityTime.set(System.currentTimeMillis());
                                        pathHandler.travel(x, y, z);
                                    } else {
                                        System.err.println("PathHandler is null, cannot start pathfinding");
                                    }
                                } catch (Exception e) {
                                    System.err.println("Error executing pathfinding: " + e.getMessage());
                                    e.printStackTrace();
                                    pathfindingInProgress.set(false);
                                } finally {
                                    // Clear initializing flag once the process is done
                                    initializingPathfinding.set(false);
                                }
                            });
                            
                            out.println("PATHFINDING:SUCCESS");
                        } else {
                            out.println("PATHFINDING:PLAYER_NULL");
                            initializingPathfinding.set(false);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid coordinates format: " + e.getMessage());
                        out.println("PATHFINDING:INVALID_COORDS");
                        initializingPathfinding.set(false);
                    }
                } else if ("cancel".equalsIgnoreCase(inputLine.trim())) {
                    // Handle cancel command on the main thread
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc != null) {
                        mc.addScheduledTask(() -> {
                            try {
                                if (pathHandler != null) {
                                    System.out.println("Explicitly cancelling pathfinding via cancel command");
                                    pathHandler.cancel();
                                }
                            } catch (Exception e) {
                                System.err.println("Error cancelling pathfinding: " + e.getMessage());
                            } finally {
                                // Always reset flags on explicit cancel
                                System.out.println("Resetting pathfinding flags due to explicit cancel");
                                pathfindingInProgress.set(false);
                                initializingPathfinding.set(false);
                            }
                        });
                    }
                    out.println("PATHFINDING:CANCELLED");
                } else if ("status".equalsIgnoreCase(inputLine.trim())) {
                    // Handle status command
                    boolean active = false;
                    try {
                        if (pathHandler != null) {
                            active = pathHandler.isActive();
                            
                            // Update activity timestamp if active
                            if (active) {
                                lastPathfindingActivityTime.set(System.currentTimeMillis());
                            }
                            
                            // Synchronize the status with our atomic flag
                            if (!active && pathfindingInProgress.get()) {
                                // If pathHandler says it's not active but our flag thinks it is, 
                                // then pathfinding must have completed/failed without us being notified
                                System.out.println("Status check found inactive pathfinding but active flag - resetting");
                                pathfindingInProgress.set(false);
                            } else if (active && !pathfindingInProgress.get()) {
                                // If pathHandler says it's active but our flag thinks it's not,
                                // update our flag to match reality
                                System.out.println("Status check found active pathfinding but inactive flag - correcting");
                                pathfindingInProgress.set(true);
                                lastPathfindingActivityTime.set(System.currentTimeMillis());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error checking pathfinding status: " + e.getMessage());
                    }
                    out.println("PATHFINDING:STATUS:" + (active ? "ACTIVE" : "INACTIVE"));
                } else if ("forcereset".equalsIgnoreCase(inputLine.trim())) {
                    // Emergency reset command for debugging or admin use
                    System.out.println("Force resetting all pathfinding state flags");
                    
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc != null) {
                        mc.addScheduledTask(() -> {
                            try {
                                if (pathHandler != null) {
                                    pathHandler.cancel();
                                }
                            } catch (Exception e) {
                                System.err.println("Error cancelling pathfinding during force reset: " + e.getMessage());
                            } finally {
                                pathfindingInProgress.set(false);
                                initializingPathfinding.set(false);
                            }
                        });
                    }
                    
                    out.println("PATHFINDING:RESET");
                } else {
                    out.println("PATHFINDING:INVALID_FORMAT");
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        threadPool.shutdown();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
        
        // Ensure pathfinding is cancelled when the server stops
        try {
            if (pathHandler != null) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null) {
                    mc.addScheduledTask(() -> {
                        try {
                            pathHandler.cancel();
                        } catch (Exception e) {
                            System.err.println("Error cancelling pathfinding during server shutdown: " + e.getMessage());
                        } finally {
                            pathfindingInProgress.set(false);
                            initializingPathfinding.set(false);
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Error during pathfinding server shutdown: " + e.getMessage());
        }
    }
    
    /**
     * Static method to notify that pathfinding has completed
     * This is called by the PathHandler when pathfinding finishes
     */
    public static void notifyPathfindingComplete() {
        // Always clear the pathfinding flag when we get a notification that pathfinding is complete
        pathfindingInProgress.set(false);
        System.out.println("Pathfinding complete notification received - socket server freed for new commands");
    }
    
    /**
     * Add method to check and fix stuck flags - will be called periodically
     */
    public void checkPathfindingState() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Check if the initialization flag has been stuck too long
            if (initializingPathfinding.get()) {
                long lastActivity = lastPathfindingActivityTime.get();
                if (currentTime - lastActivity > MAX_INIT_TIME) {
                    System.out.println("Warning: Initialization flag has been set for too long (" + 
                        (currentTime - lastActivity) / 1000 + " seconds) - forcing reset.");
                    initializingPathfinding.set(false);
                }
            }
            
            // If pathfinding is supposed to be running but hasn't had activity for too long
            if (pathfindingInProgress.get()) {
                long lastActivity = lastPathfindingActivityTime.get();
                if (currentTime - lastActivity > MAX_PATHFINDING_IDLE_TIME) {
                    System.out.println("Warning: Pathfinding has been inactive for " + 
                        (currentTime - lastActivity) / 1000 + " seconds - forcing reset.");
                    pathfindingInProgress.set(false);
                }
            }
            
            // Check if pathHandler says it's inactive but our flag says it's active
            if (pathfindingInProgress.get() && pathHandler != null && !pathHandler.isActive()) {
                System.out.println("Detected stale pathfinding flag! Resetting socket server state.");
                pathfindingInProgress.set(false);
                
                // Also check if the original pathfinder needs extra cleanup
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null) {
                    mc.addScheduledTask(() -> {
                        try {
                            // Call cancel anyway just to be super sure
                            if (pathHandler != null) {
                                pathHandler.cancel();
                            }
                        } catch (Exception e) {
                            // Ignore errors in emergency cleanup
                        }
                    });
                }
            }
        } catch (Exception e) {
            // If we can't check state, reset flags anyway to avoid deadlock
            System.err.println("Error checking pathfinding state - resetting all flags: " + e.getMessage());
            e.printStackTrace();
            
            // Always reset on error
            pathfindingInProgress.set(false);
            initializingPathfinding.set(false);
        }
    }
} 