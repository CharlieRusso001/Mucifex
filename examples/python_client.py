#!/usr/bin/env python3
"""
Example Python client for the Mucifex Minecraft mod.
This demonstrates how to control a Minecraft player using socket connections.
"""

import socket
import time
import sys

# Default server settings
DEFAULT_HOST = "localhost"
CHAT_PORT = 25560
COMMAND_PORT = 25561
MOVEMENT_PORT = 25562
LOOK_PORT = 25563

def send_to_socket(host, port, message):
    """Send a message to a socket."""
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((host, port))
        sock.sendall((message + "\n").encode())
        sock.close()
        print(f"Sent to port {port}: {message}")
        return True
    except Exception as e:
        print(f"Error sending to port {port}: {e}")
        return False

def send_chat(host, message):
    """Send a chat message."""
    return send_to_socket(host, CHAT_PORT, message)

def send_command(host, command):
    """Send a Minecraft command."""
    return send_to_socket(host, COMMAND_PORT, command)

def move_player(host, direction, duration=100):
    """Move the player in a direction for a specified duration."""
    return send_to_socket(host, MOVEMENT_PORT, f"{direction},{duration}")

def look_at_direction(host, yaw, pitch):
    """Set the player's look direction using yaw and pitch."""
    return send_to_socket(host, LOOK_PORT, f"{yaw},{pitch}")

def look_at_position(host, x, y, z):
    """Make the player look at a specific position."""
    return send_to_socket(host, LOOK_PORT, f"{x},{y},{z}")

def example_sequence(host=DEFAULT_HOST):
    """Run an example sequence of commands."""
    # Send a chat message
    send_chat(host, "Hello from Python! I'm controlled by Mucifex!")
    time.sleep(1)
    
    # Execute a command
    send_command(host, "time set day")
    time.sleep(1)
    
    # Look around
    for yaw in range(0, 360, 45):
        look_at_direction(host, yaw, 0)
        time.sleep(0.5)
    
    # Move forward
    move_player(host, "forward", 1000)
    time.sleep(1.5)
    
    # Jump
    move_player(host, "jump", 100)
    time.sleep(0.5)
    
    # Look up
    look_at_direction(host, 0, -45)
    time.sleep(0.5)
    
    # Send a final message
    send_chat(host, "Demonstration complete!")

if __name__ == "__main__":
    host = DEFAULT_HOST
    if len(sys.argv) > 1:
        host = sys.argv[1]
    
    print(f"Running Mucifex example with host: {host}")
    example_sequence(host) 