package com.mucifex.pathfinding.internal.pathfind.main.astar;

import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

public class AStarNode {
    private double hCost;
    private int gCost;

    private final int x;
    private final int y;
    private final int z;

    private AStarNode parent;
    private BlockPos blockPos;

    private boolean isJumpNode;

    private boolean isFallNode;

    public AStarNode(BlockPos pos, AStarNode parentNode, AStarNode endNode) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.blockPos = pos;

        calculateHeuristic(endNode);
        setParent(parentNode);
    }

    public AStarNode(int xRel, int yRel, int zRel, AStarNode parentNode, AStarNode endNode) {
        this.x = xRel + parentNode.getX();
        this.y = yRel + parentNode.getY();
        this.z = zRel + parentNode.getZ();
        this.blockPos = new BlockPos(x, y, z);

        calculateHeuristic(endNode);
        setParent(parentNode);
    }

    public AStarNode(BlockPos pos, AStarNode endNode) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.blockPos = pos;

        calculateHeuristic(endNode);
    }

    public AStarNode(BlockPos pos) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
    }

    public boolean canBeTraversed() {
        try {
            // Quick check for parent being null
            if (parent == null) {
                return false;
            }
            
            // Check current position and head space (2 blocks high for player)
            if (isBlockSolid(blockPos) || isBlockSolid(new BlockPos(x, y+1, z))) {
                return false;
            }
    
            // Check if we're trying to continue a fall without falling
            if (parent.isFallNode() && parent.getY() == y) {
                return false;
            }
    
            // Check if there's solid ground to stand on
            BlockPos blockBelow = new BlockPos(x, y-1, z);
            boolean hasSolidGround = isBlockSolid(blockBelow);
            
            // If we have solid ground, this is a valid walking position
            if (hasSolidGround) {
                return true;
            }
    
            // Handle jumping - player can jump up 1 block
            if (parent.blockPos.getY()-1 == y-2 && isBlockSolid(new BlockPos(x, y-2, z))) {
                setJumpNode(true);
                return true;
            }
    
            // Handle falling - continue falling if we're already falling
            if (parent.isFallNode() && y == parent.getY() - 1) {
                setFallNode(true);
                return true;
            }
    
            // Start falling if we're at an edge
            BlockPos parentBelow = new BlockPos(parent.blockPos.getX(), parent.blockPos.getY() - 1, parent.blockPos.getZ());
            if (parent.blockPos.getY() == y && isBlockSolid(parentBelow)) {
                setFallNode(true);
                return true;
            }
    
            // No valid traversal found
            return false;
        } catch (Exception e) {
            System.err.println("Error in canBeTraversed: " + e.getMessage());
            return false;
        }
    }

    private boolean isBlockSolid(BlockPos block) {
        // Cache the block state to avoid multiple lookups
        net.minecraft.block.state.IBlockState state = Minecraft.getMinecraft().theWorld.getBlockState(block);
        net.minecraft.block.Block blockType = state.getBlock();
        
        // Fast path for air blocks (most common case)
        if (blockType.getMaterial() == net.minecraft.block.material.Material.air) {
            return false;
        }
        
        // Fast path for full solid blocks
        if (blockType.getMaterial().blocksMovement() && !blockType.getMaterial().isLiquid()) {
            // Check if it's a full cube (most solid blocks)
            if (blockType.isFullCube()) {
                return true;
            }
        }
        
        // For non-standard blocks, check specific types
        return blockType.isBlockSolid(Minecraft.getMinecraft().theWorld, block, null) ||
               blockType instanceof BlockSlab ||
               blockType instanceof BlockStainedGlass ||
               blockType instanceof BlockPane ||
               blockType instanceof BlockFence ||
               blockType instanceof BlockPistonExtension ||
               blockType instanceof BlockEnderChest ||
               blockType instanceof BlockTrapDoor ||
               blockType instanceof BlockPistonBase ||
               blockType instanceof BlockChest ||
               blockType instanceof BlockStairs ||
               blockType instanceof BlockCactus ||
               blockType instanceof BlockWall ||
               blockType instanceof BlockGlass ||
               blockType instanceof BlockSkull ||
               blockType instanceof BlockSand;
    }
    private void calculateHeuristic(AStarNode endNode) {
        // Use diagonal distance heuristic instead of Manhattan distance
        // This better accounts for the fact that diagonal movement is allowed
        int dx = Math.abs(endNode.getX() - x);
        int dy = Math.abs(endNode.getY() - y);
        int dz = Math.abs(endNode.getZ() - z);
        
        // Diagonal distance formula: Use the longer of the two horizontal distances 
        // plus a small fraction of the shorter one to encourage diagonal movement
        int straightCost = 10; // Cost of moving in cardinal direction (N,E,S,W)
        int diagonalCost = 14; // Cost of moving diagonally (roughly sqrt(2) * straightCost)
        
        int max = Math.max(dx, dz);
        int min = Math.min(dx, dz);
        
        // This creates a preference for diagonal paths when they're more direct
        this.hCost = (straightCost * max) + ((diagonalCost - (2 * straightCost)) * min) + (straightCost * dy);
    }

    /**
     * Checks if there are walls adjacent to this node (horizontally)
     * @return Number of adjacent wall blocks (0-4)
     */
    private int countAdjacentWalls() {
        int wallCount = 0;
        
        // Check the four cardinal directions (N, E, S, W)
        if (isBlockSolid(new BlockPos(x+1, y, z))) wallCount++;
        if (isBlockSolid(new BlockPos(x-1, y, z))) wallCount++;
        if (isBlockSolid(new BlockPos(x, y, z+1))) wallCount++;
        if (isBlockSolid(new BlockPos(x, y, z-1))) wallCount++;
        
        return wallCount;
    }

    public void setParent(AStarNode parent) {
        this.parent = parent;

        int xDiff = Math.abs(x - parent.getX());
        int yDiff = Math.abs(y - parent.getY());
        int zDiff = Math.abs(z - parent.getZ());

        // Calculate base movement cost
        int baseCost;
        if (xDiff > 0 && zDiff > 0) {
            // Diagonal movement in XZ plane (costs ~1.4 instead of 2)
            baseCost = 14 + (yDiff * 10);
        } else {
            // Straight movement
            baseCost = (xDiff + yDiff + zDiff) * 10;
        }
        
        // Apply wall hugging penalty based on adjacent walls
        // Each wall adds a penalty to discourage wall-hugging behavior
        int wallPenalty = countAdjacentWalls() * 8; // 8 points per adjacent wall
        
        // Set the g-cost (movement cost from start to this node)
        this.gCost = parent.getGCost() + baseCost + wallPenalty;
    }

    public double getTotalCost() {
        return hCost + gCost;
    }




    public int getX() {
        return x;
    }

    public int getGCost() {
        return gCost;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public AStarNode getParent() {
        return parent;
    }

    public Vec3 asVec3(double xAdd, double yAdd, double zAdd) {
        return new Vec3(x + xAdd, y + yAdd, z + zAdd);
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof AStarNode))
            return false;

        AStarNode other = (AStarNode)o;

        return x == other.getX() && y == other.getY() && z == other.getZ();
    }

    public void setJumpNode(boolean jumpNode) {
        isJumpNode = jumpNode;
    }

    public void setFallNode(boolean fallNode) {
        isFallNode = fallNode;
    }

    public boolean isFallNode() {
        return isFallNode;
    }

    public boolean isJumpNode() {
        return isJumpNode;
    }
}


