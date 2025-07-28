#!/bin/bash

# Local Development Script for BAAS Project (Single-Module Edition)

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Print helpers
print_success() { echo -e "${GREEN}✅ $1${NC}"; }
print_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
print_error() { echo -e "${RED}❌ $1${NC}"; }
print_example() { echo -e "${CYAN}💡 $1${NC}"; }

# Config
SERVER_PORT=8080
SERVER_HOST="localhost"
PID_FILE=".baas-server.pid"
LOG_FILE="logs/baas-server.log"

# Locate latest JARs
detect_jars() {
    SERVER_JAR=$(ls -1t target/baas-server-*.jar 2>/dev/null | head -n 1)
    CLI_JAR=$(ls -1t target/baas-cli-*.jar 2>/dev/null | head -n 1)

    # Fallback: use unified JAR if server/CLI not found
    if [ -z "$SERVER_JAR" ] && [ -f "target/baas-unified-1.0-SNAPSHOT.jar" ]; then
        SERVER_JAR="target/baas-unified-1.0-SNAPSHOT.jar"
    fi
    if [ -z "$CLI_JAR" ]; then
        CLI_JAR="$SERVER_JAR"
    fi

    [ -z "$SERVER_JAR" ] && print_warning "No server JAR found in target/"
    [ -z "$CLI_JAR" ] && print_warning "No CLI JAR found in target/"
}

# Java check
check_java() {
    if ! command -v java &>/dev/null; then
        print_error "Java not found. Install Java 11+."
        exit 1
    fi
    print_info "Java version: $(java -version 2>&1 | head -n 1)"
}

# Maven build
build_project() {
    print_info "Building project..."
    if mvn clean package -DskipTests; then
        print_success "Build complete"
        detect_jars
    else
        print_error "Build failed"
        exit 1
    fi
}

# Check if server is running
is_server_running() {
    [ -f "$PID_FILE" ] && ps -p $(cat "$PID_FILE") > /dev/null 2>&1
}

# Start server
start_server() {
    detect_jars
    [ -z "$SERVER_JAR" ] && build_project

    if is_server_running; then
        print_warning "Server already running (PID: $(cat $PID_FILE))"
        return
    fi

    mkdir -p logs data

    print_info "Starting BAAS server on $SERVER_HOST:$SERVER_PORT..."
    nohup java -jar "$SERVER_JAR" > "$LOG_FILE" 2>&1 &

    echo $! > "$PID_FILE"
    print_success "Server started (PID: $(cat $PID_FILE))"

    # Give server a moment to initialize
    sleep 3
    print_info "Server should be ready for connections"
}

# Stop server
stop_server() {
    if is_server_running; then
        print_info "Stopping server (PID: $(cat $PID_FILE))..."
        kill $(cat "$PID_FILE") && rm -f "$PID_FILE"
        print_success "Server stopped"
    else
        print_warning "Server is not running"
    fi
}

# Restart server
restart_server() {
    stop_server
    sleep 2
    start_server
}

# Run CLI
run_cli() {
    detect_jars
    [ -z "$CLI_JAR" ] && build_project
    print_info "Using CLI: $CLI_JAR"
    java -jar "$CLI_JAR" -s "$SERVER_HOST" -p "$SERVER_PORT" "$@"
}

# Show status
show_status() {
    detect_jars
    echo "=== BAAS Status ==="
    if is_server_running; then
        print_success "Server running (PID: $(cat $PID_FILE))"
        echo "Server URL: $SERVER_HOST:$SERVER_PORT"
        echo "Log file: $LOG_FILE"
    else
        print_warning "Server not running"
    fi
    echo "Server JAR: ${SERVER_JAR:-not found}"
    echo "CLI JAR: ${CLI_JAR:-not found}"
}

# Show logs
show_logs() {
    [ -f "$LOG_FILE" ] && tail -f "$LOG_FILE" || print_warning "Log file not found"
}

# Show CLI examples
show_examples() {
    echo "=== BAAS CLI Usage Examples ==="
    echo ""
    echo "📝 INSERT Examples:"
    print_example "./local-dev.sh cli insert users '{\"name\":\"Alice\",\"age\":30}'"
    print_example "./local-dev.sh cli insert users '[{\"name\":\"Bob\",\"age\":25},{\"name\":\"Carol\",\"age\":35}]'"
    echo ""

    echo "📖 GET Examples:"
    print_example "./local-dev.sh cli get users --all"
    print_example "./local-dev.sh cli get users documentId"
    print_example "./local-dev.sh cli get users --where \"age>=25\""
    print_example "./local-dev.sh cli get users --where \"name=Alice\""
    print_example "./local-dev.sh cli get users --all --limit 10"
    print_example "./local-dev.sh cli get users --all --fields \"name,age\""
    print_example "./local-dev.sh cli get users --all --sort \"age DESC\""
    echo ""

    echo "🔍 QUERY Examples:"
    print_example "./local-dev.sh cli query users name Alice"
    print_example "./local-dev.sh cli query users age 30"
    echo ""

    echo "⏰ TTL Examples (auto-expiry):"
    print_example "./local-dev.sh cli insert sessions '{\"userId\":\"123\",\"token\":\"abc\",\"_ttl_ms\":300000}'"
    echo "   ↳ Document expires after 5 minutes"
    echo ""

    echo "🚀 Quick Start:"
    print_example "./local-dev.sh start          # Start server"
    print_example "./local-dev.sh cli insert users '[{\"name\":\"Alice\",\"age\":30},{\"name\":\"Bob\",\"age\":25}]'"
    print_example "./local-dev.sh cli get users --all"
}

# Main logic
case "$1" in
    build)
        check_java
        build_project
        ;;
    start|server)
        check_java
        start_server
        ;;
    stop)
        stop_server
        ;;
    restart)
        check_java
        restart_server
        ;;
    cli)
        shift
        run_cli "$@"
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    examples|help)
        show_examples
        ;;
    *)
        echo "Usage: $0 {build|start|stop|restart|cli|status|logs|examples}"
        echo ""
        echo "Examples:"
        echo "  $0 start"
        echo "  $0 cli insert users '{\"name\":\"Alice\",\"age\":30}'"
        echo "  $0 cli get users --all"
        echo "  $0 examples    # Show detailed usage examples"
        ;;
esac