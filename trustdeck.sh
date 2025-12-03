#!/usr/bin/env bash
set -euo pipefail

# Get absolute path of the directory where this script is in
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT_DIR/trustdeck.env"
DEV_COMPOSE="$ROOT_DIR/docker/docker-compose.dev.yml"
PROD_COMPOSE="$ROOT_DIR/docker/docker-compose.prod.yml"
APP_COMPOSE="$ROOT_DIR/docker/docker-compose.app.yml"

# Container_names (same as in the compose files)
POSTGRES_CONTAINER="trustdeck-postgresql"
KEYCLOAK_CONTAINER="trustdeck-keycloak"

# Helper for printing how to use
usage() {
  cat <<EOF
Usage: $0 <dev|prod> <start|stop>

  dev   - Run PostgreSQL and Keycloak in Docker, backend via Maven
  prod  - Run PostgreSQL, Keycloak, and backend in Docker

Examples:
  $0 dev start
  $0 dev stop
  $0 prod start
  $0 prod stop
EOF
  exit 1
}

# Decide whether to prefix commands with sudo for Docker
if docker info >/dev/null 2>&1; then
  SUDO_DOCKER=""
elif command -v sudo >/dev/null 2>&1 && sudo -n docker info >/dev/null 2>&1; then
  SUDO_DOCKER="sudo"
else
  echo "Cannot talk to Docker (even with sudo)."
  echo "   - Is the Docker daemon running?"
  echo "   - Do you need to be in the 'docker' group or use sudo?"
  exit 1
fi

# Method for checking docker container health status
wait_for_healthy() {
  local container="$1"
  local retries="${2:-30}"
  local delay="${3:-5}"

  echo "Waiting for container '$container' to become healthy ..."

  for i in $(seq 1 "$retries"); do
    # Read health status (or 'unknown' if no healthcheck / container not ready)
    local status
    status="$($SUDO_DOCKER docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' "$container" 2>/dev/null || echo "unknown")"

    if [[ "$status" == "healthy" ]]; then
      echo "✅ $container is healthy."
      return 0
    elif [[ "$status" == "unhealthy" ]]; then
      echo "❌ $container is UNHEALTHY. Aborting."
      return 1
    fi

    echo "  [$i/$retries] $container status: $status (retrying in ${delay}s...)"
    sleep "$delay"
  done

  echo "Timed out waiting for $container to become healthy."
  return 1
}

# Method that encapsulates the development startup commands
start_dev() {
  echo "Starting Keycloak and PostgreSQL in dev mode ..."
  $SUDO_DOCKER docker compose --project-name "trustdeck-dev" --env-file "$ENV_FILE" -f "$DEV_COMPOSE" up -d

  # Wait for DB + Keycloak to be ready
  wait_for_healthy "$POSTGRES_CONTAINER"
  wait_for_healthy "$KEYCLOAK_CONTAINER"

  echo "Running backend via Maven ..."

  # Export all variables from trustdeck.env to current shell
  set -a
  . "$ENV_FILE"
  set +a

  mvn clean compile -f "$ROOT_DIR/pom.xml" -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector org.springframework.boot:spring-boot-maven-plugin:run -Dorg.jooq.no-logo=true -Dorg.jooq.no-tips=true
}

# Method that encapsulates the development stop commands
stop_dev() { 
  echo "Stopping dev containers (PostgreSQL and Keycloak) ..."
  $SUDO_DOCKER docker compose --project-name trustdeck --env-file "$ENV_FILE" -f "$DEV_COMPOSE" down

  echo "✅ Dev stack stopped."
}

# Method that encapsulates the production startup commands
start_prod() {
  echo "Building and starting full production stack (PostgreSQL, Keycloak, TrustDeck backend) ..."
  $SUDO_DOCKER docker compose \
    --project-name "trustdeck" \
	--env-file "$ENV_FILE" \
    -f "$PROD_COMPOSE" \
    -f "$APP_COMPOSE" \
    up --build -d

  # Optional but nice: wait for infra to be healthy
  wait_for_healthy "$POSTGRES_CONTAINER"
  wait_for_healthy "$KEYCLOAK_CONTAINER"

  echo "✅ Production stack is up (containers running in background)."
}

# Method that encapsulates the production stop commands
stop_prod() {
  echo "Stopping production stack ..."
  $SUDO_DOCKER docker compose \
    --project-name "trustdeck" \
    --env-file "$ENV_FILE" \
    -f "$PROD_COMPOSE" \
    -f "$APP_COMPOSE" \
    down

  echo "✅ Production stack stopped."
}

# --- Main script ---
# Get command line args
MODE="${1:-}"
ACTION="${2:-}"

if [[ -z "$MODE" || -z "$ACTION" ]]; then
  usage
fi

# Decide on what to do or print "how to use"
case "$MODE" in
  dev)
    case "$ACTION" in
      start) start_dev ;;
      stop)  stop_dev ;;
      *) usage ;;
    esac
    ;;
  prod)
    case "$ACTION" in
      start) start_prod ;;
      stop)  stop_prod ;;
      *) usage ;;
    esac
    ;;
  *)
    usage
    ;;
esac
