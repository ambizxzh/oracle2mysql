#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# Pull images when Docker Hub is unreachable (optional mirrors)
if ! docker image inspect ghcr.io/gvenzl/oracle-xe:11 &>/dev/null; then
  docker pull ghcr.io/gvenzl/oracle-xe:11
fi
if ! docker image inspect mysql:8.0.25 &>/dev/null; then
  docker pull m.daocloud.io/docker.io/library/mysql:8.0.25
  docker tag m.daocloud.io/docker.io/library/mysql:8.0.25 mysql:8.0.25
fi

docker compose up -d
echo "Waiting for databases..."
until docker inspect -f '{{.State.Health.Status}}' o2m-mysql8025 2>/dev/null | grep -q healthy; do sleep 2; done
until docker exec o2m-oracle11g healthcheck.sh &>/dev/null; do sleep 3; done
echo "Oracle 11g XE and MySQL 8.0.25 are ready."
