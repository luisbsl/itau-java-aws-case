#!/usr/bin/env bash
set -euo pipefail
docker compose up -d
gradle wrapper --gradle-version 8.9
chmod +x gradlew
./gradlew :lambdas:payment-intake:shadowJar
make package-lambda
make apply
./gradlew :services:payment-worker:bootRun
