package com.mucifex.pathfinding.internal.pathfind.main.walk.target.impl;

import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import com.mucifex.pathfinding.internal.util.Util;
import com.mucifex.pathfinding.internal.pathfind.main.path.PathElm;
import com.mucifex.pathfinding.internal.pathfind.main.path.impl.TravelNode;
import com.mucifex.pathfinding.internal.pathfind.main.walk.target.WalkTarget;

public class TravelTarget extends WalkTarget {

    TravelNode node;
    public TravelTarget(TravelNode node) {
        this.node = node;
    }
    @Override
    public boolean tick(Vec3 predictedMotionOnStop, Vec3 playerPos) {
        setCurrentTarget(node.getBlockPos());

        Vec3 dest = new Vec3(node.getBlockPos()).addVector(0.5d, 0d, 0.5d);
        double predicatedPositionDistance = playerPos.distanceTo(playerPos.add(predictedMotionOnStop));
        double destPositionDistance = playerPos.distanceTo(dest);

        double angle = calculateAnglePredictionDest(predictedMotionOnStop, dest.subtract(playerPos));

        return (predicatedPositionDistance > destPositionDistance && angle < PREDICTED_MOTION_ANGLE) || Util.getPlayerBlockPos().equals(Util.toBlockPos(dest));
    }

    public BlockPos getNodeBlockPos() {
        return node.getBlockPos();
    }

    public PathElm getElm() {
        return node;
    }
}
