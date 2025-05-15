package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;

/**
 * Command to execute a Minecraft command
 */
public class CommandExecuteCommand implements PlayerCommand {
    private final String command;
    
    /**
     * Create a new command execution
     * @param command The command to execute (without leading slash)
     */
    public CommandExecuteCommand(String command) {
        // Remove leading slash if present
        this.command = command.startsWith("/") ? command.substring(1) : command;
    }
    
    @Override
    public void execute() {
        try {
            // Make sure player exists
            if (Minecraft.getMinecraft().thePlayer == null) {
                Mucifex.LOGGER.error("Cannot execute command: Player not available");
                return;
            }
            
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/" + command);
            Mucifex.LOGGER.info("Executed command: /" + command);
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error executing command: " + e.getMessage());
        }
    }
} 