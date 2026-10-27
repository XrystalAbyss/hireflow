#!/usr/bin/env bash
set -euo pipefail

echo "=============================================================================="
echo "HireFlow - Unified Multi-Artifact Reproducible Build Script"
echo "=============================================================================="
echo "Java Version:"
java -version

echo ""
echo "------------------------------------------------------------------------------"
echo "Step 1: Building and installing HireFlow core, WAR, and desktop JAR"
echo "------------------------------------------------------------------------------"
mvn clean install "$@"

echo ""
echo "------------------------------------------------------------------------------"
echo "Step 2: Building and packaging HireFlow Spring Boot REST API"
echo "------------------------------------------------------------------------------"
mvn -f hireflow-api/pom.xml clean package "$@"

echo ""
echo "=============================================================================="
echo "[SUCCESS] All HireFlow artifacts compiled, tested, and packaged successfully!"
echo "Artifact 1: target/hireflow-1.0.0.war          (Servlet 6.0 Web Portal)"
echo "Artifact 2: target/hireflow-1.0.0-desktop.jar  (Stage 2 Swing Desktop App)"
echo "Artifact 3: target/hireflow-1.0.0-classes.jar  (Core Domain/Service Classes)"
echo "Artifact 4: hireflow-api/target/hireflow-api-1.0.0.jar (Spring Boot Executable)"
echo "=============================================================================="
