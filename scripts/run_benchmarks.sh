#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo "Building project..."
mvn clean package -DskipTests
echo "Running benchmarks..."
mkdir -p results/csv
java -jar target/benchmarks.jar -rf csv -rff results/csv/all_benchmarks.csv
echo "Done. Results in results/csv/all_benchmarks.csv"
