#!/usr/bin/env python3
import socket
import argparse
import time
import threading
import sys

def pathfind_to(x, y, z):
    """Send pathfinding command to navigate to specified coordinates"""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(5)  # Add timeout to prevent hanging
            s.connect(('localhost', 25566))
            command = f"{x},{y},{z}\n"
            s.sendall(command.encode())
            response = s.recv(1024).decode().strip()
            return response
    except socket.error as e:
        return f"Error: {e}"

def cancel_pathfinding():
    """Cancel any active pathfinding"""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(5)  # Add timeout to prevent hanging
            s.connect(('localhost', 25566))
            s.sendall(b"cancel\n")
            response = s.recv(1024).decode().strip()
            return response
    except socket.error as e:
        return f"Error: {e}"

def get_pathfinding_status():
    """Check if pathfinding is currently active"""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(5)  # Add timeout to prevent hanging
            s.connect(('localhost', 25566))
            s.sendall(b"status\n")
            response = s.recv(1024).decode().strip()
            return response
    except socket.error as e:
        return f"Error: {e}"

def track_pathfinding_progress():
    """Continuously monitor pathfinding status until completed or cancelled"""
    tracking = True
    start_time = time.time()
    
    print("Tracking pathfinding progress (Ctrl+C to stop tracking)...")
    last_check_time = 0
    check_interval = 2  # seconds
    
    try:
        while tracking:
            current_time = time.time()
            elapsed = current_time - start_time
            
            # Only check status every check_interval seconds
            if current_time - last_check_time >= check_interval:
                status_response = get_pathfinding_status()
                last_check_time = current_time
                
                if "STATUS:ACTIVE" in status_response:
                    print(f"[{elapsed:.1f}s] Pathfinding active, moving to destination...")
                elif "STATUS:INACTIVE" in status_response:
                    print(f"[{elapsed:.1f}s] Pathfinding complete or not started.")
                    tracking = False
                else:
                    print(f"Unknown status: {status_response}")
                    if "Error" in status_response:
                        tracking = False
            
            # Brief sleep to prevent CPU overuse
            time.sleep(0.1)
            
    except KeyboardInterrupt:
        print("\nTracking stopped by user.")
        # Optionally cancel pathfinding when user stops tracking
        response = cancel_pathfinding()
        print(f"Cancelled pathfinding: {response}")
    
    return "Tracking finished"

def safe_pathfind(x, y, z):
    """Pathfind with automatic tracking and safe fallback"""
    start_time = time.time()
    
    # Start pathfinding
    print(f"Starting pathfinding to coordinates: {x}, {y}, {z}")
    response = pathfind_to(x, y, z)
    print(f"Initial response: {response}")
    
    if "SUCCESS" not in response:
        print("Failed to start pathfinding. Please check if the game is running.")
        return
        
    # Start tracking in a separate thread
    tracking_thread = threading.Thread(target=track_pathfinding_progress)
    tracking_thread.daemon = True
    tracking_thread.start()
    
    # Set a timeout for very long pathfinding (10 minutes)
    timeout = 600  # seconds
    
    print("Tracking pathfinding (press Ctrl+C to cancel)...")
    try:
        # Keep the main thread alive, but implement a timeout
        while tracking_thread.is_alive():
            if time.time() - start_time > timeout:
                print(f"\nPathfinding timeout after {timeout} seconds.")
                response = cancel_pathfinding()
                print(f"Cancelled pathfinding: {response}")
                break
            
            tracking_thread.join(1)
    except KeyboardInterrupt:
        print("\nCancelling pathfinding...")
        response = cancel_pathfinding()
        print(f"Cancelled pathfinding: {response}")
    finally:
        total_elapsed = time.time() - start_time
        print(f"Pathfinding operation completed after {total_elapsed:.1f} seconds")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Minecraft Pathfinding Control")
    
    # Create a subparser for different commands
    subparsers = parser.add_subparsers(dest="command", help="Command to execute")
    
    # Pathfind command
    pathfind_parser = subparsers.add_parser("goto", help="Navigate to coordinates")
    pathfind_parser.add_argument("x", type=int, help="X coordinate")
    pathfind_parser.add_argument("y", type=int, help="Y coordinate")
    pathfind_parser.add_argument("z", type=int, help="Z coordinate")
    pathfind_parser.add_argument("--track", action="store_true", help="Track pathfinding progress")
    pathfind_parser.add_argument("--safe", action="store_true", help="Use safe pathfinding with automatic tracking")
    
    # Cancel command
    cancel_parser = subparsers.add_parser("cancel", help="Cancel active pathfinding")
    
    # Status command
    status_parser = subparsers.add_parser("status", help="Check pathfinding status")
    status_parser.add_argument("--watch", action="store_true", help="Continuously watch pathfinding status")
    
    args = parser.parse_args()
    
    if args.command == "goto":
        if args.safe:
            safe_pathfind(args.x, args.y, args.z)
        else:
            print(f"Pathfinding to coordinates: {args.x}, {args.y}, {args.z}")
            response = pathfind_to(args.x, args.y, args.z)
            print(f"Response: {response}")
            
            if args.track:
                # Start tracking in a separate thread so we can still cancel if needed
                tracking_thread = threading.Thread(target=track_pathfinding_progress)
                tracking_thread.daemon = True
                tracking_thread.start()
                
                print("Tracking pathfinding (press Ctrl+C to exit)...")
                try:
                    # Keep the main thread alive
                    while tracking_thread.is_alive():
                        tracking_thread.join(1)
                except KeyboardInterrupt:
                    print("\nExiting...")
                    sys.exit(0)
    
    elif args.command == "cancel":
        print("Cancelling pathfinding")
        response = cancel_pathfinding()
        print(f"Response: {response}")
    
    elif args.command == "status":
        if args.watch:
            track_pathfinding_progress()
        else:
            print("Checking pathfinding status")
            response = get_pathfinding_status()
            print(f"Response: {response}")
    
    else:
        parser.print_help() 