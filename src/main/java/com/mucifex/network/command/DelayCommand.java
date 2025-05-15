package com.mucifex.network.command;

import com.mucifex.Mucifex;

/**
 * Command that waits for a delay before executing another command
 */
public class DelayCommand implements PlayerCommand {
    private final int delayMs;
    private final PlayerCommand command;
    private long scheduleTime;
    
    /**
     * Create a new delay command
     * @param delayMs The delay in milliseconds
     * @param command The command to execute after the delay
     */
    public DelayCommand(int delayMs, PlayerCommand command) {
        this.delayMs = delayMs;
        this.command = command;
        this.scheduleTime = System.currentTimeMillis() + delayMs;
    }
    
    @Override
    public void execute() {
        // Check if it's time to execute the command
        long currentTime = System.currentTimeMillis();
        if (currentTime >= scheduleTime) {
            try {
                // It's time to execute the command
                Mucifex.LOGGER.info("Executing delayed command after " + delayMs + "ms");
                command.execute();
            } catch (Exception e) {
                Mucifex.LOGGER.error("Error executing delayed command: " + e.getMessage());
            }
        } else {
            // Not time yet, reschedule
            Mucifex.LOGGER.debug("Rescheduling command, " + (scheduleTime - currentTime) + "ms remaining");
            
            // Requeue this command to check again in the next tick
            com.mucifex.Mucifex.getInstance().getSocketManager().queueCommand(this);
        }
    }
} 