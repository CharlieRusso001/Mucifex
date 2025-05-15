package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;

/**
 * Command to execute a Minecraft command
 */
public class ExecuteCommandCommand implements PlayerCommand {
    private final String command;
    
    public ExecuteCommandCommand(String command) {
        // Remove leading slash if present
        this.command = command.startsWith("/") ? command.substring(1) : command;
    }
    
    @Override
    public void execute() {
        try {
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/" + command);
            Mucifex.LOGGER.info("Executed command: /" + command);
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error executing command: " + e.getMessage());
        }
    }
} 