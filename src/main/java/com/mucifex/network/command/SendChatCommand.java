package com.mucifex.network.command;

import com.mucifex.Mucifex;
import net.minecraft.client.Minecraft;

/**
 * Command to send a chat message
 */
public class SendChatCommand implements PlayerCommand {
    private final String message;
    
    public SendChatCommand(String message) {
        this.message = message;
    }
    
    @Override
    public void execute() {
        try {
            // Check if message starts with "/" to determine if it's a command
            if (message.startsWith("/")) {
                Minecraft.getMinecraft().thePlayer.sendChatMessage(message);
            } else {
                Minecraft.getMinecraft().thePlayer.sendChatMessage(message);
            }
            Mucifex.LOGGER.info("Sent chat message: " + message);
        } catch (Exception e) {
            Mucifex.LOGGER.error("Error sending chat message: " + e.getMessage());
        }
    }
} 