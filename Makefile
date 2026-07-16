PLUGIN_NAME := Backrooms

.PHONY: build
build:
	gradle clean shadowJar
	mkdir -p bin
	cp build/libs/$(PLUGIN_NAME)*.jar bin/

.PHONY: clean
clean:
	gradle clean
	rm -rf bin/

.PHONY: server-plugin-copy
server-plugin-copy:
	mkdir -p server/plugins/
	rm -f server/plugins/$(PLUGIN_NAME)*.jar
	cp bin/$(PLUGIN_NAME)*.jar server/plugins/

.PHONY: server-clear-plugin-data
server-clear-plugin-data:
	rm -rf server/plugins/$(PLUGIN_NAME)/

.PHONY: server-clean
server-clean:
	cd server && find . -mindepth 1 \
		! -name 'paper-*.jar' \
		! -name 'eula.txt' \
		! -name 'ops.json' \
		! -name 'server.properties' \
		-delete 2>/dev/null || true

.PHONY: server-start
server-start:
	cd server && java -Xmx2G -Xms2G -jar paper-*.jar nogui

.PHONY: server
server: build server-plugin-copy server-start

.PHONY: all
all: clean build server

.PHONY: check-book-lengths
check-book-lengths:
	python3 scripts/check_book_lengths.py

# =============================================================================
# Test Server
# =============================================================================
#
# LOCAL TESTING (two terminals):
#   Terminal 1: make build test-server-download-all test-server-setup test-server-local
#   Terminal 2: (connect a bot or client)
#
# =============================================================================

TEST_SERVER_DIR := test-server
DOWNLOAD_CACHE := .download-cache
SERVER_VARIANT ?= paper
MINECRAFT_VERSION ?= 26.1.2

# Delete partially-written targets when a recipe fails, so a bad download
# doesn't get cached and treated as a valid jar on the next run.
.DELETE_ON_ERROR:

$(DOWNLOAD_CACHE)/paper-%.jar:
	@mkdir -p $(DOWNLOAD_CACHE)
	url=$$(curl -fsSL -H "User-Agent: backrooms-ci (github actions)" \
		"https://fill.papermc.io/v3/projects/paper/versions/$*/builds/latest" \
		| jq -r '.downloads."server:default".url'); \
	if [ -z "$$url" ] || [ "$$url" = "null" ]; then \
		echo "ERROR: could not resolve Paper $* download URL from fill.papermc.io" >&2; \
		exit 1; \
	fi; \
	curl -fsSL -o $@ "$$url" || { echo "ERROR: failed to download Paper $* from $$url" >&2; exit 1; }

$(DOWNLOAD_CACHE)/purpur-%.jar:
	@mkdir -p $(DOWNLOAD_CACHE)
	build=$$(curl -fsSL "https://api.purpurmc.org/v2/purpur/$*" | jq -r '.builds.latest'); \
	if [ -z "$$build" ] || [ "$$build" = "null" ]; then \
		echo "ERROR: could not resolve latest Purpur build for $* from api.purpurmc.org" >&2; \
		exit 1; \
	fi; \
	curl -fsSL -o $@ "https://api.purpurmc.org/v2/purpur/$*/$$build/download" \
		|| { echo "ERROR: failed to download Purpur $* build $$build" >&2; exit 1; }

.PHONY: test-server-download
test-server-download: $(DOWNLOAD_CACHE)/$(SERVER_VARIANT)-$(MINECRAFT_VERSION).jar
	mkdir -p $(TEST_SERVER_DIR)
	cp $< $(TEST_SERVER_DIR)/server.jar

.PHONY: test-server-download-all
test-server-download-all: test-server-download

.PHONY: test-server-plugin-copy
test-server-plugin-copy:
	rm -rf $(TEST_SERVER_DIR)/plugins/
	mkdir -p $(TEST_SERVER_DIR)/plugins
	cp bin/*.jar $(TEST_SERVER_DIR)/plugins/

.PHONY: test-server-setup
test-server-setup: test-server-plugin-copy
	echo "eula=true" > $(TEST_SERVER_DIR)/eula.txt
	printf "online-mode=false\nserver-port=25565\nspawn-protection=0\nmax-tick-time=-1\n" > $(TEST_SERVER_DIR)/server.properties
	@mkdir -p $(TEST_SERVER_DIR)/plugins/bStats
	@printf 'enabled: false\nserverUuid: "00000000-0000-0000-0000-000000000000"\nlogFailedRequests: false\nlogSentData: false\nlogResponseStatusText: false\n' > $(TEST_SERVER_DIR)/plugins/bStats/config.yml

.PHONY: test-server-local
test-server-local:
	cd $(TEST_SERVER_DIR) && java -Xmx1G -Xms1G -jar server.jar nogui

.PHONY: test-server-ci
test-server-ci:
	@cd $(TEST_SERVER_DIR) && \
	rm -f server_input; \
	mkfifo server_input; \
	sleep 3600 > server_input & \
	FEEDER_PID=$$!; \
	java -Xmx1G -Xms1G -jar server.jar nogui < server_input > server.log 2>&1 & \
	SERVER_PID=$$!; \
	trap 'kill $$SERVER_PID $$FEEDER_PID 2>/dev/null; rm -f server_input' INT TERM; \
	echo "Waiting for server to start... (first boot patches the mojang jar and can take minutes)"; \
	STARTED=0; \
	for i in $$(seq 1 600); do \
		if grep -q "Done.*For help" server.log 2>/dev/null; then \
			STARTED=1; \
			break; \
		fi; \
		if ! kill -0 $$SERVER_PID 2>/dev/null; then \
			break; \
		fi; \
		sleep 1; \
	done; \
	if [ $$STARTED -ne 1 ]; then \
		echo ""; \
		echo "========== Server failed to start =========="; \
		cat server.log; \
		kill $$SERVER_PID $$FEEDER_PID 2>/dev/null || true; \
		rm -f server_input; \
		exit 1; \
	fi; \
	echo ""; \
	echo "========== Server started successfully =========="; \
	PLUGIN_OK=1; \
	if grep -q "$(PLUGIN_NAME).*enabled" server.log; then \
		echo "$(PLUGIN_NAME) plugin loaded"; \
	else \
		echo "$(PLUGIN_NAME) plugin failed to load"; \
		PLUGIN_OK=0; \
	fi; \
	echo ""; \
	echo "========== Shutting down server =========="; \
	if kill -0 $$SERVER_PID 2>/dev/null; then \
		echo "stop" > server_input; \
	fi; \
	for i in $$(seq 1 60); do \
		if ! kill -0 $$SERVER_PID 2>/dev/null; then \
			break; \
		fi; \
		sleep 1; \
	done; \
	kill $$SERVER_PID $$FEEDER_PID 2>/dev/null || true; \
	rm -f server_input; \
	echo ""; \
	echo "========== Server log =========="; \
	cat server.log; \
	echo ""; \
	FAILED=$$((1 - PLUGIN_OK)); \
	if grep -qE "ERROR.*$(PLUGIN_NAME)|$(PLUGIN_NAME).*Exception" server.log 2>/dev/null; then \
		echo "=== SERVER ERRORS ==="; \
		grep -E "ERROR.*$(PLUGIN_NAME)|$(PLUGIN_NAME).*Exception" server.log; \
		FAILED=1; \
	fi; \
	if [ $$FAILED -eq 1 ]; then \
		echo "Tests failed"; \
		exit 1; \
	else \
		echo "All checks passed"; \
	fi

.PHONY: clean-test-server
clean-test-server:
	rm -rf $(TEST_SERVER_DIR)

.PHONY: clean-download-cache
clean-download-cache:
	rm -rf $(DOWNLOAD_CACHE)

.PHONY: readme-external
readme-external:
	sed 's|docs/assets/|https://raw.githubusercontent.com/def9a2a4/backrooms/refs/heads/main/docs/assets/|g' README.md
