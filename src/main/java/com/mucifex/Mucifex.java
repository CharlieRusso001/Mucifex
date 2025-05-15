package com.mucifex;

import com.mucifex.network.SocketManager;
import com.mucifex.socket.PathfindingSocketServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.MouseInputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@Mod(modid = Mucifex.MODID, name = Mucifex.NAME, version = Mucifex.VERSION)
public class Mucifex {
    public static final String MODID = "mucifex";
    public static final String NAME = "Mucifex";
    public static final String VERSION = "2.0";
    
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    
    // Static instance for global access
    private static Mucifex instance;
    
    private SocketManager socketManager;
    private PathfindingSocketServer pathfindingServer;
    
    // Pathfinding server port
    public static final int PATHFINDING_PORT = 25566;
    
    // Key bindings
    public static KeyBinding keyUnfocus;
    public static KeyBinding keyShowInfo;
    public static KeyBinding keyEscape;
    
    // State tracking
    private boolean unfocusModeActive = false;
    private boolean wasPausedOnScreen = false;
    private float lastYaw = 0;
    private float lastPitch = 0;
    private boolean hasShownWelcomeMessage = false;
    
    public Mucifex() {
        instance = this;
    }
    
    /**
     * Get the singleton instance of the mod
     */
    public static Mucifex getInstance() {
        return instance;
    }
    
    /**
     * Get the socket manager
     */
    public SocketManager getSocketManager() {
        return socketManager;
    }
    
    /**
     * Get the pathfinding server
     */
    public PathfindingSocketServer getPathfindingServer() {
        return pathfindingServer;
    }
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Mucifex is loading!");
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Mucifex is initializing!");
        
        // Register key bindings
        keyUnfocus = new KeyBinding("key.mucifex.unfocus", Keyboard.KEY_Z, "key.categories.mucifex");
        keyShowInfo = new KeyBinding("key.mucifex.showinfo", Keyboard.KEY_M, "key.categories.mucifex");
        keyEscape = new KeyBinding("key.mucifex.escape", Keyboard.KEY_ESCAPE, "key.categories.mucifex");
        ClientRegistry.registerKeyBinding(keyUnfocus);
        ClientRegistry.registerKeyBinding(keyShowInfo);
        ClientRegistry.registerKeyBinding(keyEscape);
        
        // Initialize socket manager with different ports for different functions
        socketManager = new SocketManager();
        socketManager.startAllServers();
        
        // Initialize pathfinding socket server
        pathfindingServer = new PathfindingSocketServer();
        pathfindingServer.start();
        LOGGER.info("Pathfinding socket server started on port " + PATHFINDING_PORT);
    }
    
    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        LOGGER.info("Mucifex has loaded!");
    }
    
    @EventHandler
    public void onServerStopping(net.minecraftforge.fml.common.event.FMLServerStoppingEvent event) {
        // Shutdown the pathfinding socket server
        if (pathfindingServer != null) {
            pathfindingServer.stop();
            LOGGER.info("Pathfinding socket server stopped");
        }
    }
    
    /**
     * Handler for chat messages received by the client
     */
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        // Forward the chat message to our chat receiver server
        socketManager.handleChatMessage(event);
    }
    
    /**
     * Handler for when the player joins a server
     */
    @SubscribeEvent
    public void onClientConnectedToServer(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        // Schedule the welcome message to be shown after the player has fully loaded
        Minecraft.getMinecraft().addScheduledTask(() -> {
            hasShownWelcomeMessage = false; // Reset flag when connecting to a new server
        });
    }
    
    /**
     * Handler for when the player enters a world
     */
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        // Schedule the welcome message to be shown after the player has fully loaded
        if (!hasShownWelcomeMessage && Minecraft.getMinecraft().thePlayer != null) {
            // Delayed execution to make sure the player is ready
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (!hasShownWelcomeMessage && Minecraft.getMinecraft().thePlayer != null) {
                    showWelcomeMessage();
                    hasShownWelcomeMessage = true;
                }
            });
        }
    }
    
    /**
     * Display a welcome message when joining a server
     */
    private void showWelcomeMessage() {
        sendChatMessage("§a§l[Mucifex] §cWelcome to Mucifex! Use at your own risk on public servers.");
        sendChatMessage("§a§l[Mucifex] §fPress §7Z§f to toggle unfocus mode, §7M§f for socket info.");
        LOGGER.info("Mucifex welcome message displayed");
    }
    
    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        
        // Check if the unfocus key is pressed
        if (keyUnfocus.isPressed()) {
            // Toggle unfocus mode
            unfocusModeActive = !unfocusModeActive;
            
            if (unfocusModeActive) {
                // Store current pause state and rotation
                wasPausedOnScreen = mc.isGamePaused();
                if (mc.thePlayer != null) {
                    lastYaw = mc.thePlayer.rotationYaw;
                    lastPitch = mc.thePlayer.rotationPitch;
                }
                
                // Ungrab mouse and allow alt-tab without pausing
                mc.gameSettings.pauseOnLostFocus = false;
                mc.mouseHelper.ungrabMouseCursor();
                sendChatMessage("§a[Mucifex] §fUnfocus mode §aACTIVATED§f - Mouse released and head rotation locked");
                LOGGER.info("Unfocus mode activated - Mouse released and head rotation locked");
            } else {
                // Restore mouse and pause settings
                mc.gameSettings.pauseOnLostFocus = true;
                if (!mc.inGameHasFocus) {
                    mc.mouseHelper.grabMouseCursor();
                }
                sendChatMessage("§a[Mucifex] §fUnfocus mode §cDEACTIVATED§f - Normal mouse behavior restored");
                LOGGER.info("Unfocus mode deactivated - Normal mouse behavior restored");
            }
        }
        
        // Check if the show info key is pressed
        if (keyShowInfo.isPressed()) {
            displaySocketInfo();
        }
        
        // Check if the escape key is pressed
        if (keyEscape.isPressed()) {
            // Close any open container/GUI
            if (mc.currentScreen != null) {
                LOGGER.info("Escape key pressed via Mucifex - closing screen");
                mc.displayGuiScreen(null);
            }
        }
    }
    
    /**
     * Displays information about all open sockets in the chat
     */
    private void displaySocketInfo() {
        sendChatMessage("§a§l[Mucifex Socket Information]");
        sendChatMessage("§a• §fChat Server: §e" + SocketManager.CHAT_PORT + " §7(Send chat messages)");
        sendChatMessage("§a• §fCommand Server: §e" + SocketManager.COMMAND_PORT + " §7(Execute Minecraft commands)");
        sendChatMessage("§a• §fMovement Server: §e" + SocketManager.MOVEMENT_PORT + " §7(Control player movement)");
        sendChatMessage("§a• §fLook Server: §e" + SocketManager.LOOK_PORT + " §7(Control head rotation)");
        sendChatMessage("§a• §fInventory Server: §e" + SocketManager.INVENTORY_PORT + " §7(Manipulate inventories)");
        sendChatMessage("§a• §fChat Receiver: §e" + SocketManager.CHAT_RECEIVER_PORT + " §7(Receive chat messages)");
        sendChatMessage("§a• §fPathfinding Server: §e" + PATHFINDING_PORT + " §7(Advanced player pathfinding)");
        
        // Add a clickable example to copy to clipboard
        ChatComponentText text = new ChatComponentText("§a• §fExample: §7python -c \"import socket; s=socket.socket(); s.connect(('localhost'," + 
                SocketManager.CHAT_PORT + ")); s.send(b'Hello from Python\\n'); s.close()\"");
        text.setChatStyle(new ChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                        "python -c \"import socket; s=socket.socket(); s.connect(('localhost'," + 
                        SocketManager.CHAT_PORT + ")); s.send(b'Hello from Python\\n'); s.close()\"")));
        
        if (Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(text);
        }
        
        // Add a pathfinding example
        ChatComponentText pathfindText = new ChatComponentText("§a• §fPathfinding Example: §7python -c \"import socket; s=socket.socket(); s.connect(('localhost'," + 
                PATHFINDING_PORT + ")); s.send(b'100,64,100\\n'); s.close()\"");
        pathfindText.setChatStyle(new ChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                        "python -c \"import socket; s=socket.socket(); s.connect(('localhost'," + 
                        PATHFINDING_PORT + ")); s.send(b'100,64,100\\n'); s.close()\"")));
        
        if (Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(pathfindText);
        }
        
        // Examples for inventory commands
        sendChatMessage("§a• §fInventory Commands:");
        sendChatMessage("§7  - open_inventory §f- Open player inventory");
        sendChatMessage("§7  - close §f- Close open container");
        sendChatMessage("§7  - click,23 §f- Left click slot 23");
        sendChatMessage("§7  - click,23,right §f- Right click slot 23");
        sendChatMessage("§7  - status §f- Show inventory information");
        
        // Examples for pathfinding commands
        sendChatMessage("§a• §fPathfinding Commands:");
        sendChatMessage("§7  - x,y,z §f- Travel to coordinates (e.g., 100,64,100)");
        sendChatMessage("§7  - cancel §f- Cancel current pathfinding");
        sendChatMessage("§7  - status §f- Check pathfinding status");
        
        sendChatMessage("§a• §fUnfocus Mode: §e" + (unfocusModeActive ? "§aACTIVE" : "§cINACTIVE") + 
                " §7(Press §fZ§7 to " + (unfocusModeActive ? "disable" : "enable") + ")");
    }
    
    /**
     * Send a formatted chat message to the player
     */
    private void sendChatMessage(String message) {
        if (Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(message));
        }
    }
    
    /**
     * Intercept mouse input to prevent head rotation when in unfocus mode
     */
    @SubscribeEvent
    public void onMouseInput(MouseInputEvent event) {
        if (unfocusModeActive && Minecraft.getMinecraft().thePlayer != null) {
            // If in unfocus mode, cancel mouse movement affecting head rotation
            // by resetting the player's rotation to the values when unfocus was activated
            Minecraft.getMinecraft().thePlayer.rotationYaw = lastYaw;
            Minecraft.getMinecraft().thePlayer.rotationPitch = lastPitch;
        }
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Process any pending commands from socket on each tick
            socketManager.processCommands();
            
            // Handle unfocus mode if active
            if (unfocusModeActive) {
                Minecraft mc = Minecraft.getMinecraft();
                
                // Prevent game from pausing when not in focus
                if (mc.isGamePaused() && !wasPausedOnScreen) {
                    // Force the game to unpause if it was auto-paused
                    mc.setIngameNotInFocus();
                }
                
                // Keep the mouse ungrabbed
                if (keyUnfocus.isKeyDown() && mc.inGameHasFocus) {
                    mc.mouseHelper.ungrabMouseCursor();
                }
                
                // If player exists, lock rotation to saved values unless changed by socket commands
                if (mc.thePlayer != null) {
                    // Update saved values if a socket command has changed them
                    if (Math.abs(mc.thePlayer.rotationYaw - lastYaw) > 5 || 
                        Math.abs(mc.thePlayer.rotationPitch - lastPitch) > 5) {
                        // If a significant change happened, it's likely from a socket command
                        // So update our "locked" values to the new rotation
                        lastYaw = mc.thePlayer.rotationYaw;
                        lastPitch = mc.thePlayer.rotationPitch;
                    } else {
                        // Otherwise enforce the locked values
                        mc.thePlayer.rotationYaw = lastYaw;
                        mc.thePlayer.rotationPitch = lastPitch;
                    }
                }
            }
        }
    }
} 