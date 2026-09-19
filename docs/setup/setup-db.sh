#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GRADLEW="$ROOT_DIR/gradlew"

MARIADB_CONTAINER="${MARIADB_CONTAINER:-dataloader-mariadb}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-dataloader-postgres}"
MARIADB_IMAGE="${MARIADB_IMAGE:-mariadb:12.3.2}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:17.6}"
MARIADB_PORT="${MARIADB_PORT:-3307}"
POSTGRES_PORT="${POSTGRES_PORT:-5433}"
MARIADB_VOLUME="${MARIADB_VOLUME:-dataloader-mariadb-data}"
POSTGRES_VOLUME="${POSTGRES_VOLUME:-dataloader-postgres-data}"

MARIADB_DATABASE="${MARIADB_DATABASE:-acs_ball_by_ball}"
MARIADB_USER="${MARIADB_USER:-acs_ball_by_ball}"
MARIADB_PASSWORD="${MARIADB_PASSWORD:-acs_ball_by_ball-local-password}"
MARIADB_ROOT_PASSWORD="${MARIADB_ROOT_PASSWORD:-mariadb-local-root-password}"

POSTGRES_DATABASE="${POSTGRES_DATABASE:-acs_ball_by_ball}"
POSTGRES_USER="${POSTGRES_USER:-acs_ball_by_ball}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-acs_ball_by_ball-local-password}"
POSTGRES_ADMIN_PASSWORD="${POSTGRES_ADMIN_PASSWORD:-acs_ball_by_ball-local-admin-password}"

BALL_BY_BALL_DIR="${BALL_BY_BALL_DIR:-}"
PLAYER_REGISTRY="${PLAYER_REGISTRY:-people.csv}"

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_identifier() {
    local name="$1"
    local value="$2"
    [[ "$value" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "$name must contain only letters, numbers, and underscores: $value"
}

require_password() {
    local name="$1"
    local value="$2"
    case "$value" in
        *"'"*|*"\\"*|*$'\n'*|*$'\r'*)
            die "$name must not contain a quote, backslash, or newline because it is passed to an in-container SQL statement"
            ;;
    esac
}

require_configuration() {
    require_identifier MARIADB_DATABASE "$MARIADB_DATABASE"
    require_identifier MARIADB_USER "$MARIADB_USER"
    require_identifier POSTGRES_DATABASE "$POSTGRES_DATABASE"
    require_identifier POSTGRES_USER "$POSTGRES_USER"
    require_password MARIADB_PASSWORD "$MARIADB_PASSWORD"
    require_password MARIADB_ROOT_PASSWORD "$MARIADB_ROOT_PASSWORD"
    require_password POSTGRES_PASSWORD "$POSTGRES_PASSWORD"
    require_password POSTGRES_ADMIN_PASSWORD "$POSTGRES_ADMIN_PASSWORD"
}

container_exists() {
    docker container inspect "$1" >/dev/null 2>&1
}

container_running() {
    [[ "$(docker container inspect --format '{{.State.Running}}' "$1" 2>/dev/null || true)" == "true" ]]
}

start_mariadb() {
    if container_exists "$MARIADB_CONTAINER"; then
        if ! container_running "$MARIADB_CONTAINER"; then
            docker start "$MARIADB_CONTAINER" >/dev/null
        fi
    else
        docker run --detach \
            --name "$MARIADB_CONTAINER" \
            --restart unless-stopped \
            --publish "$MARIADB_PORT:3306" \
            --volume "$MARIADB_VOLUME:/var/lib/mysql" \
            --env "MARIADB_ROOT_PASSWORD=$MARIADB_ROOT_PASSWORD" \
            "$MARIADB_IMAGE" >/dev/null
    fi
}

start_postgres() {
    if container_exists "$POSTGRES_CONTAINER"; then
        if ! container_running "$POSTGRES_CONTAINER"; then
            docker start "$POSTGRES_CONTAINER" >/dev/null
        fi
    else
        docker run --detach \
            --name "$POSTGRES_CONTAINER" \
            --restart unless-stopped \
            --publish "$POSTGRES_PORT:5432" \
            --volume "$POSTGRES_VOLUME:/var/lib/postgresql/data" \
            --env POSTGRES_USER=postgres \
            --env "POSTGRES_PASSWORD=$POSTGRES_ADMIN_PASSWORD" \
            --env POSTGRES_DB=postgres \
            "$POSTGRES_IMAGE" >/dev/null
    fi
}

wait_for_mariadb() {
    local attempt
    for attempt in {1..60}; do
        if docker exec "$MARIADB_CONTAINER" mariadb-admin \
            --protocol=socket \
            --user=root \
            "--password=$MARIADB_ROOT_PASSWORD" \
            ping --silent >/dev/null 2>&1; then
            return
        fi
        sleep 2
    done
    docker logs "$MARIADB_CONTAINER" >&2 || true
    die "MariaDB did not become ready"
}

wait_for_postgres() {
    local attempt
    for attempt in {1..60}; do
        if docker exec "$POSTGRES_CONTAINER" pg_isready \
            --username=postgres \
            --dbname=postgres >/dev/null 2>&1; then
            return
        fi
        sleep 2
    done
    docker logs "$POSTGRES_CONTAINER" >&2 || true
    die "PostgreSQL did not become ready"
}

configure_mariadb() {
    docker exec -i "$MARIADB_CONTAINER" mariadb \
        --protocol=socket \
        --user=root \
        "--password=$MARIADB_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS \`$MARIADB_DATABASE\`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$MARIADB_USER'@'%' IDENTIFIED BY '$MARIADB_PASSWORD';
ALTER USER '$MARIADB_USER'@'%' IDENTIFIED BY '$MARIADB_PASSWORD';
GRANT ALL PRIVILEGES ON \`$MARIADB_DATABASE\`.* TO '$MARIADB_USER'@'%';
FLUSH PRIVILEGES;
SQL
}

configure_postgres() {
    docker exec -i "$POSTGRES_CONTAINER" psql \
        --username=postgres \
        --dbname=postgres \
        --set=ON_ERROR_STOP=1 \
        --set="app_user=$POSTGRES_USER" \
        --set="app_password=$POSTGRES_PASSWORD" \
        --set="db_name=$POSTGRES_DATABASE" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'app_user') \gexec
ALTER ROLE :"app_user" WITH LOGIN PASSWORD :'app_password';
SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'app_user')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db_name') \gexec
ALTER DATABASE :"db_name" OWNER TO :"app_user";
GRANT ALL PRIVILEGES ON DATABASE :"db_name" TO :"app_user";
SQL
}

start_containers() {
    require_command docker
    require_configuration
    start_mariadb
    start_postgres
    wait_for_mariadb
    wait_for_postgres
    configure_mariadb
    configure_postgres
    printf 'MariaDB is ready on localhost:%s (%s)\n' "$MARIADB_PORT" "$MARIADB_CONTAINER"
    printf 'PostgreSQL is ready on localhost:%s (%s)\n' "$POSTGRES_PORT" "$POSTGRES_CONTAINER"
}

migrate_mariadb() {
    (
        cd "$ROOT_DIR"
        FLYWAY_DATABASE=mysql \
            FLYWAY_URL="jdbc:mariadb://127.0.0.1:$MARIADB_PORT/$MARIADB_DATABASE" \
            FLYWAY_USER="$MARIADB_USER" \
            FLYWAY_PASSWORD="$MARIADB_PASSWORD" \
            "$GRADLEW" :bbb-update-database:flywayMigrate --no-daemon
    )
}

migrate_postgres() {
    (
        cd "$ROOT_DIR"
        FLYWAY_DATABASE=postgres \
            FLYWAY_URL="jdbc:postgresql://127.0.0.1:$POSTGRES_PORT/$POSTGRES_DATABASE" \
            FLYWAY_USER="$POSTGRES_USER" \
            FLYWAY_PASSWORD="$POSTGRES_PASSWORD" \
            "$GRADLEW" :bbb-update-database:flywayMigrate --no-daemon
    )
}

migrate_databases() {
    start_containers
    migrate_mariadb
    migrate_postgres
}

quote_gradle_arg() {
    local value="$1"
    value=${value//\\/\\\\}
    value=${value//"/\\"}
    printf '"%s"' "$value"
}

require_import_inputs() {
    [[ -n "$BALL_BY_BALL_DIR" ]] || die "set BALL_BY_BALL_DIR to the root of the Ball-by-Ball data directory"
    [[ -d "$BALL_BY_BALL_DIR" ]] || die "BALL_BY_BALL_DIR does not exist: $BALL_BY_BALL_DIR"
    [[ -f "$BALL_BY_BALL_DIR/$PLAYER_REGISTRY" ]] || die "player registry does not exist: $BALL_BY_BALL_DIR/$PLAYER_REGISTRY"
}

load_database() {
    local database_name="$1"
    local connection_string="$2"
    local user_name="$3"
    local password="$4"
    local args

    args="--outputType DATABASE"
    args+=" --baseDirectory $(quote_gradle_arg "$BALL_BY_BALL_DIR")"
    args+=" --playerRegistry $(quote_gradle_arg "$PLAYER_REGISTRY")"
    args+=" --connectionString $(quote_gradle_arg "$connection_string")"
    args+=" --userName $(quote_gradle_arg "$user_name")"
    args+=" --password $(quote_gradle_arg "$password")"

    printf 'Loading Ball-by-Ball data into %s\n' "$database_name"
    (
        cd "$ROOT_DIR"
        "$GRADLEW" :bbb-update-database:run --no-daemon --args="$args"
    )
}

load_databases() {
    require_import_inputs
    start_containers
    load_database \
        MariaDB \
        "jdbc:mariadb://127.0.0.1:$MARIADB_PORT/$MARIADB_DATABASE" \
        "$MARIADB_USER" \
        "$MARIADB_PASSWORD"
    load_database \
        PostgreSQL \
        "jdbc:postgresql://127.0.0.1:$POSTGRES_PORT/$POSTGRES_DATABASE" \
        "$POSTGRES_USER" \
        "$POSTGRES_PASSWORD"
}

stop_containers() {
    require_command docker
    if container_exists "$MARIADB_CONTAINER"; then
        docker stop "$MARIADB_CONTAINER" >/dev/null || true
    fi
    if container_exists "$POSTGRES_CONTAINER"; then
        docker stop "$POSTGRES_CONTAINER" >/dev/null || true
    fi
}

reset_containers() {
    [[ "${CONFIRM_RESET:-}" == "1" ]] || die "reset deletes both named database volumes; rerun with CONFIRM_RESET=1"
    require_command docker
    stop_containers
    docker rm "$MARIADB_CONTAINER" "$POSTGRES_CONTAINER" 2>/dev/null || true
    docker volume rm "$MARIADB_VOLUME" "$POSTGRES_VOLUME" 2>/dev/null || true
    printf 'Removed containers and volumes for both test databases\n'
}

status() {
    require_command docker
    docker ps --all \
        --filter "name=^/${MARIADB_CONTAINER}$" \
        --filter "name=^/${POSTGRES_CONTAINER}$" \
        --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
}

usage() {
    cat <<'USAGE'
Usage: docs/setup/setup-db.sh <command>

Commands:
  up       Start both containers and create the application users/databases
  migrate  Start/configure both containers and apply both Flyway migrations
  load     Start/configure both containers and import Ball-by-Ball data into both
  all      Run up, migrate, and load for both databases
  down     Stop both containers without deleting their data
  reset    Delete both containers and volumes (requires CONFIRM_RESET=1)
  status   Show the container status

Set BALL_BY_BALL_DIR and PLAYER_REGISTRY before using load or all.
All configuration values can be overridden with environment variables; see
docs/setup/SETUP-DB.md for the complete list.
USAGE
}

command="${1:-}"
case "$command" in
    up)
        start_containers
        ;;
    migrate)
        migrate_databases
        ;;
    load)
        load_databases
        ;;
    all)
        require_import_inputs
        start_containers
        migrate_mariadb
        migrate_postgres
        load_database \
            MariaDB \
            "jdbc:mariadb://127.0.0.1:$MARIADB_PORT/$MARIADB_DATABASE" \
            "$MARIADB_USER" \
            "$MARIADB_PASSWORD"
        load_database \
            PostgreSQL \
            "jdbc:postgresql://127.0.0.1:$POSTGRES_PORT/$POSTGRES_DATABASE" \
            "$POSTGRES_USER" \
            "$POSTGRES_PASSWORD"
        ;;
    down)
        stop_containers
        ;;
    reset)
        reset_containers
        ;;
    status)
        status
        ;;
    -h|--help|"")
        usage
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac