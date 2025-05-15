package com.mucifex.network.command;

/**
 * Interface for commands that will be executed on the main Minecraft thread
 */
public interface PlayerCommand {
    /**
     * Execute the command
     */
    void execute();
} 