#!/bin/bash

PATHFINDING_DIR="/home/charlie/mucifex/src/main/java/com/mucifex/pathfinding/internal"

echo "Updating package references in Java files..."

# Replace package declarations
find "$PATHFINDING_DIR" -name "*.java" -type f -exec sed -i 's/package roger/package com.mucifex.pathfinding.internal/g' {} \;
find "$PATHFINDING_DIR" -name "*.java" -type f -exec sed -i 's/package roger\./package com.mucifex.pathfinding.internal./g' {} \;

# Replace import statements
find "$PATHFINDING_DIR" -name "*.java" -type f -exec sed -i 's/import roger\./import com.mucifex.pathfinding.internal./g' {} \;

# Fix imports in pathfind package
find "$PATHFINDING_DIR/pathfind" -name "*.java" -type f -exec sed -i 's/package roger\.pathfind/package com.mucifex.pathfinding.internal.pathfind/g' {} \;

# Fix imports in util package
find "$PATHFINDING_DIR/util" -name "*.java" -type f -exec sed -i 's/package roger\.util/package com.mucifex.pathfinding.internal.util/g' {} \;

echo "Package updates complete!" 