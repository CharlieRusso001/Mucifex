package com.mucifex.pathfinding.internal.pathfind.main.processor;

import com.mucifex.pathfinding.internal.pathfind.main.path.PathElm;

import java.util.List;

public abstract class Processor {
    public abstract void process(List<PathElm> elms);
}
