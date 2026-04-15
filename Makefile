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
	rm -f server/plugins/$(PLUGIN_NAME)*.jar
	cp bin/$(PLUGIN_NAME)*.jar server/plugins/

.PHONY: server-clear-plugin-data
server-clear-plugin-data:
	rm -rf server/plugins/$(PLUGIN_NAME)/

.PHONY: server-start
server-start:
	cd server && java -Xmx2G -Xms2G -jar paper-1.21.11-55.jar nogui

.PHONY: server
server: build server-plugin-copy server-start

.PHONY: all
all: clean build server

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
MINECRAFT_VERSION ?= 1.21.11

$(DOWNLOAD_CACHE)/paper-%.jar:
	@mkdir -p $(DOWNLOAD_CACHE)
	$(eval BUILD := $(shell curl -s "https://api.papermc.io/v2/projects/paper/versions/$*/builds" | jq -r '.builds[-1].build'))
	curl -o $@ "https://api.papermc.io/v2/projects/paper/versions/$*/builds/$(BUILD)/downloads/paper-$*-$(BUILD).jar"

$(DOWNLOAD_CACHE)/purpur-%.jar:
	@mkdir -p $(DOWNLOAD_CACHE)
	$(eval BUILD := $(shell curl -s "https://api.purpurmc.org/v2/purpur/$*" | jq -r '.builds.latest'))
	curl -o $@ "https://api.purpurmc.org/v2/purpur/$*/$(BUILD)/download"

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
	mkfifo server_input 2>/dev/null || true; \
	tail -f server_input | java -Xmx1G -Xms1G -jar server.jar nogui > server.log 2>&1 & \
	SERVER_PID=$$!; \
	sleep 0.5; \
	echo "Waiting for server to start..."; \
	for i in $$(seq 1 600); do \
		if grep -q "Done.*For help" server.log 2>/dev/null; then \
			echo ""; \
			echo "========== Server started successfully =========="; \
			break; \
		fi; \
		if ! kill -0 $$SERVER_PID 2>/dev/null; then \
			echo ""; \
			echo "========== Server process died unexpectedly =========="; \
			cat server.log; \
			exit 1; \
		fi; \
		sleep 1; \
	done; \
	if ! grep -q "Done.*For help" server.log 2>/dev/null; then \
		echo ""; \
		echo "========== Server startup timed out =========="; \
		cat server.log; \
		kill $$SERVER_PID 2>/dev/null || true; \
		rm -f server_input; \
		exit 1; \
	fi; \
	if ! grep -q "$(PLUGIN_NAME).*enabled" server.log; then \
		echo "$(PLUGIN_NAME) plugin failed to load"; \
		cat server.log; \
		kill $$SERVER_PID 2>/dev/null || true; \
		rm -f server_input; \
		exit 1; \
	fi; \
	echo "$(PLUGIN_NAME) plugin loaded"; \
	echo ""; \
	echo "========== Shutting down server =========="; \
	echo "stop" > server_input; \
	for i in $$(seq 1 30); do \
		if ! kill -0 $$SERVER_PID 2>/dev/null; then \
			break; \
		fi; \
		sleep 1; \
	done; \
	kill $$SERVER_PID 2>/dev/null || true; \
	rm -f server_input; \
	rm -f errors.log; \
	FAILED=0; \
	if grep -qE "ERROR.*$(PLUGIN_NAME)|$(PLUGIN_NAME).*Exception" server.log 2>/dev/null; then \
		echo "" | tee -a errors.log; \
		echo "=== SERVER ERRORS ===" | tee -a errors.log; \
		grep -E "ERROR.*$(PLUGIN_NAME)|$(PLUGIN_NAME).*Exception" server.log | tee -a errors.log; \
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
