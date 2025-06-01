# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0


# All documents to be used in spell check.
ALL_DOCS := $(shell find . -type f -name '*.md' -not -path './.github/*' -not -path '*/node_modules/*' -not -path '*/_build/*' -not -path '*/deps/*' -not -path */Pods/* -not -path */.expo/* | sort)
CUSTOM_SERVICES = accounting ad cart checkout currency email fraud-detection frontend frontend-proxy image-provider load-generator payment product-catalog quote recommendation shipping flagd-ui kafka
THIRD_PARTY_SERVICES = jaeger grafana otel-collector prometheus opensearch flagd valkey-cart
PWD := $(shell pwd)

TOOLS_DIR := ./internal/tools
MISSPELL_BINARY=bin/misspell
MISSPELL = $(TOOLS_DIR)/$(MISSPELL_BINARY)

DOCKER_COMPOSE_CMD ?= docker compose
DOCKER_COMPOSE_ENV=--env-file .env --env-file .env.override
DOCKER_COMPOSE_BUILD_ARGS=

# Java Workaround for macOS 15.2+ and M4 chips (see https://bugs.openjdk.org/browse/JDK-8345296)
ifeq ($(shell uname -m),arm64)
	ifeq ($(shell uname -s),Darwin)
		DOCKER_COMPOSE_ENV+= --env-file .env.arm64
		DOCKER_COMPOSE_BUILD_ARGS+= --build-arg=_JAVA_OPTIONS=-XX:UseSVE=0
	endif
endif

# see https://github.com/open-telemetry/build-tools/releases for semconvgen updates
# Keep links in semantic_conventions/README.md and .vscode/settings.json in sync!
SEMCONVGEN_VERSION=0.11.0
YAMLLINT_VERSION=1.30.0

.PHONY: all
all: install-tools markdownlint misspell yamllint

$(MISSPELL):
	cd $(TOOLS_DIR) && go build -o $(MISSPELL_BINARY) github.com/client9/misspell/cmd/misspell

.PHONY: misspell
misspell:	$(MISSPELL)
	$(MISSPELL) -error $(ALL_DOCS)

.PHONY: misspell-correction
misspell-correction:	$(MISSPELL)
	$(MISSPELL) -w $(ALL_DOCS)

.PHONY: markdownlint
markdownlint:
	@if ! npm ls markdownlint; then npm install; fi
	@for f in $(ALL_DOCS); do \
		echo $$f; \
		npx --no -p markdownlint-cli markdownlint -c .markdownlint.yaml $$f \
			|| exit 1; \
	done

.PHONY: install-yamllint
install-yamllint:
    # Using a venv is recommended
	yamllint --version >/dev/null 2>&1 || pip install -U yamllint~=$(YAMLLINT_VERSION)

.PHONY: yamllint
yamllint: install-yamllint
	yamllint .

.PHONY: checklicense
checklicense:
	@echo "Checking license headers..."
	@if ! npm ls @kt3k/license-checker; then npm install; fi
	npx @kt3k/license-checker -q

.PHONY: addlicense
addlicense:
	@echo "Adding license headers..."
	@if ! npm ls @kt3k/license-checker; then npm install; fi
	npx @kt3k/license-checker -q -i

.PHONY: checklinks
checklinks:
	@echo "Checking links..."
	@if ! npm ls @umbrelladocs/linkspector; then npm install; fi
	linkspector check

# Run all checks in order of speed / likely failure.
.PHONY: check
check: misspell markdownlint checklicense checklinks
	@echo "All checks complete"

# Attempt to fix issues / regenerate tables.
.PHONY: fix
fix: misspell-correction
	@echo "All autofixes complete"

.PHONY: install-tools
install-tools: $(MISSPELL)
	npm install
	@echo "All tools installed"

.PHONY: build
build:
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) build $(DOCKER_COMPOSE_BUILD_ARGS)

.PHONY: build-and-push
build-and-push:
	@echo "Building and pushing custom services with custom version..."
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) build $(DOCKER_COMPOSE_BUILD_ARGS) $(CUSTOM_SERVICES)
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) push $(CUSTOM_SERVICES)
	@echo "Pulling and pushing third-party services with original versions..."
	@set -a; \
	PIPELINE_IMAGE_NAME="$$IMAGE_NAME"; \
	[ -f .env ] && . ./.env; \
	[ -f .env.override ] && . ./.env.override; \
	if [ -n "$$PIPELINE_IMAGE_NAME" ]; then \
		export IMAGE_NAME="$$PIPELINE_IMAGE_NAME"; \
		echo "Using pipeline IMAGE_NAME: $$IMAGE_NAME"; \
	else \
		echo "Using default IMAGE_NAME: $$IMAGE_NAME"; \
	fi; \
	set +a; \
	failed_services=""; \
	\
	echo "Processing Jaeger..."; \
	if [ -n "$$JAEGERTRACING_IMAGE" ]; then \
		version=$$(echo $$JAEGERTRACING_IMAGE | cut -d':' -f2); \
		case "$$version" in v*) tag_version="$$version" ;; *) tag_version="v$$version" ;; esac; \
		target_image="$$IMAGE_NAME:$$tag_version-jaeger"; \
		if docker pull $$target_image; then \
			echo "Successfully pulled existing jaeger image from target registry"; \
		elif docker pull $$JAEGERTRACING_IMAGE && \
		     docker tag $$JAEGERTRACING_IMAGE $$target_image && \
		     docker push $$target_image; then \
			echo "Successfully processed jaeger from source"; \
		else \
			echo "Failed to process jaeger" >&2; \
			failed_services="$$failed_services jaeger"; \
		fi; \
	else \
		echo "ERROR: JAEGERTRACING_IMAGE is not set"; \
		failed_services="$$failed_services jaeger"; \
	fi; \
	\
	echo "Processing Grafana..."; \
	if [ -n "$$GRAFANA_IMAGE" ]; then \
		version=$$(echo $$GRAFANA_IMAGE | cut -d':' -f2); \
		case "$$version" in v*) tag_version="$$version" ;; *) tag_version="v$$version" ;; esac; \
		target_image="$$IMAGE_NAME:$$tag_version-grafana"; \
		if docker pull $$target_image; then \
			echo "Successfully pulled existing grafana image from target registry"; \
		elif docker pull $$GRAFANA_IMAGE && \
		     docker tag $$GRAFANA_IMAGE $$target_image && \
		     docker push $$target_image; then \
			echo "Successfully processed grafana from source"; \
		else \
			echo "Failed to process grafana" >&2; \
			failed_services="$$failed_services grafana"; \
		fi; \
	else \
		echo "ERROR: GRAFANA_IMAGE is not set"; \
		failed_services="$$failed_services grafana"; \
	fi; \
	\
	echo "Processing OpenTelemetry Collector..."; \
	if [ -n "$$COLLECTOR_CONTRIB_IMAGE" ]; then \
		version=$$(echo $$COLLECTOR_CONTRIB_IMAGE | cut -d':' -f2); \
		case "$$version" in v*) tag_version="$$version" ;; *) tag_version="v$$version" ;; esac; \
		target_image="$$IMAGE_NAME:$$tag_version-otel-collector"; \
		if docker pull $$target_image; then \
			echo "Successfully pulled existing otel-collector image from target registry"; \
		elif docker pull $$COLLECTOR_CONTRIB_IMAGE && \
		     docker tag $$COLLECTOR_CONTRIB_IMAGE $$target_image && \
		     docker push $$target_image; then \
			echo "Successfully processed otel-collector from source"; \
		else \
			echo "Failed to process otel-collector" >&2; \
			failed_services="$$failed_services otel-collector"; \
		fi; \
	else \
		echo "ERROR: COLLECTOR_CONTRIB_IMAGE is not set"; \
		failed_services="$$failed_services otel-collector"; \
	fi; \
	\
	echo "Processing Prometheus..."; \
	if [ -n "$$PROMETHEUS_IMAGE" ]; then \
		version=$$(echo $$PROMETHEUS_IMAGE | cut -d':' -f2); \
		case "$$version" in v*) tag_version="$$version" ;; *) tag_version="v$$version" ;; esac; \
		target_image="$$IMAGE_NAME:$$tag_version-prometheus"; \
		if docker pull $$target_image; then \
			echo "Successfully pulled existing prometheus image from target registry"; \
		elif docker pull $$PROMETHEUS_IMAGE && \
		     docker tag $$PROMETHEUS_IMAGE $$target_image && \
		     docker push $$target_image; then \
			echo "Successfully processed prometheus from source"; \
		else \
			echo "Failed to process prometheus" >&2; \
			failed_services="$$failed_services prometheus"; \
		fi; \
	else \
		echo "ERROR: PROMETHEUS_IMAGE is not set"; \
		failed_services="$$failed_services prometheus"; \
	fi; \
	\
	echo "Processing OpenSearch..."; \
	if [ -n "$$OPENSEARCH_IMAGE" ]; then \
		version=$$(echo $$OPENSEARCH_IMAGE | cut -d':' -f2); \
		case "$$version" in v*) tag_version="$$version" ;; *) tag_version="v$$version" ;; esac; \
		target_image="$$IMAGE_NAME:$$tag_version-opensearch"; \
		if docker pull $$target_image; then \
			echo "Successfully pulled existing opensearch image from target registry"; \
		elif docker pull $$OPENSEARCH_IMAGE && \
		     docker tag $$OPENSEARCH_IMAGE $$target_image && \
		     docker push $$target_image; then \
			echo "Successfully processed opensearch from source"; \
		else \
			echo "Failed to process opensearch" >&2; \
			failed_services="$$failed_services opensearch"; \
		fi; \
	else \
		echo "ERROR: OPENSEARCH_IMAGE is not set"; \
		failed_services="$$failed_services opensearch"; \
	fi; \
	\
	echo "Processing Flagd..."; \
	if [ -n "$$FLAGD_IMAGE" ]; then \
		version=$$(echo $$FLAGD_IMAGE | cut -d':' -f2); \
		case "$$version" in v*) tag_version="$$version" ;; *) tag_version="v$$version" ;; esac; \
		target_image="$$IMAGE_NAME:$$tag_version-flagd"; \
		if docker pull $$target_image; then \
			echo "Successfully pulled existing flagd image from target registry"; \
		elif docker pull $$FLAGD_IMAGE && \
		     docker tag $$FLAGD_IMAGE $$target_image && \
		     docker push $$target_image; then \
			echo "Successfully processed flagd from source"; \
		else \
			echo "Failed to process flagd" >&2; \
			failed_services="$$failed_services flagd"; \
		fi; \
	else \
		echo "ERROR: FLAGD_IMAGE is not set"; \
		failed_services="$$failed_services flagd"; \
	fi; \
	\
	echo "Processing Valkey..."; \
	if [ -n "$$VALKEY_IMAGE" ]; then \
		version=$$(echo $$VALKEY_IMAGE | cut -d':' -f2); \
		case "$$version" in v*) tag_version="$$version" ;; *) tag_version="v$$version" ;; esac; \
		target_image="$$IMAGE_NAME:$$tag_version-valkey-cart"; \
		if docker pull $$target_image; then \
			echo "Successfully pulled existing valkey-cart image from target registry"; \
		elif docker pull $$VALKEY_IMAGE && \
		     docker tag $$VALKEY_IMAGE $$target_image && \
		     docker push $$target_image; then \
			echo "Successfully processed valkey-cart from source"; \
		else \
			echo "Failed to process valkey-cart" >&2; \
			failed_services="$$failed_services valkey-cart"; \
		fi; \
	else \
		echo "ERROR: VALKEY_IMAGE is not set"; \
		failed_services="$$failed_services valkey-cart"; \
	fi; \
	\
	if [ -n "$$failed_services" ]; then \
		echo "The following services failed to process:$$failed_services" >&2; \
		echo "You can retry these services individually using:"; \
		for service in $$failed_services; do \
			echo "  make build-and-push-third-party service=$$service"; \
		done; \
		exit 1; \
	fi

.PHONY: build-and-push-third-party
build-and-push-third-party:
ifndef service
	@echo "Please specify a service name using service=<name>"
	@echo "Available services: jaeger grafana otel-collector prometheus opensearch flagd valkey-cart"
	exit 1
endif
	@set -a; \
	PIPELINE_IMAGE_NAME="$$IMAGE_NAME"; \
	[ -f .env ] && . ./.env; \
	[ -f .env.override ] && . ./.env.override; \
	if [ -n "$$PIPELINE_IMAGE_NAME" ]; then \
		export IMAGE_NAME="$$PIPELINE_IMAGE_NAME"; \
		echo "Using pipeline IMAGE_NAME: $$IMAGE_NAME"; \
	else \
		echo "Using default IMAGE_NAME: $$IMAGE_NAME"; \
	fi; \
	set +a; \
	case "$(service)" in \
		jaeger) \
			src="$$JAEGERTRACING_IMAGE"; \
			;; \
		grafana) \
			src="$$GRAFANA_IMAGE"; \
			;; \
		otel-collector) \
			src="$$COLLECTOR_CONTRIB_IMAGE"; \
			;; \
		prometheus) \
			src="$$PROMETHEUS_IMAGE"; \
			;; \
		opensearch) \
			src="$$OPENSEARCH_IMAGE"; \
			;; \
		flagd) \
			src="$$FLAGD_IMAGE"; \
			;; \
		valkey-cart) \
			src="$$VALKEY_IMAGE"; \
			;; \
		*) \
			echo "Unknown service: $(service)"; \
			echo "Available services: jaeger grafana otel-collector prometheus opensearch flagd valkey-cart"; \
			exit 1; \
			;; \
	esac; \
	if [ -z "$$src" ]; then \
		echo "ERROR: Environment variable for $(service) is not set"; \
		exit 1; \
	fi; \
	echo "Processing $$src -> $(service)"; \
	version=$$(echo $$src | cut -d':' -f2); \
	case "$$version" in v*) tag_version="$$version" ;; *) tag_version="v$$version" ;; esac; \
	target_image="$$IMAGE_NAME:$$tag_version-$(service)"; \
	if docker pull $$target_image; then \
		echo "Successfully pulled existing $(service) image from target registry"; \
	elif docker pull $$src && \
	     docker tag $$src $$target_image && \
	     docker push $$target_image; then \
		echo "Successfully processed $(service) from source"; \
	else \
		echo "Failed to process $(service)" >&2; \
		exit 1; \
	fi

# Create multiplatform builder for buildx
.PHONY: create-multiplatform-builder
create-multiplatform-builder:
	docker buildx create --name otel-demo-builder --bootstrap --use --driver docker-container --config ./buildkitd.toml

# Remove multiplatform builder for buildx
.PHONY: remove-multiplatform-builder
remove-multiplatform-builder:
	docker buildx rm otel-demo-builder

# Build and push multiplatform images (linux/amd64, linux/arm64) using buildx.
# Requires docker with buildx enabled and a multi-platform capable builder in use.
# Docker needs to be configured to use containerd storage for images to be loaded into the local registry.
.PHONY: build-multiplatform
build-multiplatform:
	# Because buildx bake does not support --env-file yet, we need to load it into the environment first.
	set -a; . ./.env.override; set +a && docker buildx bake -f docker-compose.yml --load --set "*.platform=linux/amd64,linux/arm64"

.PHONY: build-multiplatform-and-push
build-multiplatform-and-push:
    # Because buildx bake does not support --env-file yet, we need to load it into the environment first.
	set -a; . ./.env.override; set +a && docker buildx bake -f docker-compose.yml --push --set "*.platform=linux/amd64,linux/arm64"

.PHONY: clean-images
clean-images:
	@docker rmi $(shell docker images --filter=reference="ghcr.io/open-telemetry/demo:latest-*" -q); \
    if [ $$? -ne 0 ]; \
    then \
    	echo; \
        echo "Failed to removed 1 or more OpenTelemetry Demo images."; \
        echo "Check to ensure the Demo is not running by executing: make stop"; \
        false; \
    fi

.PHONY: run-tests
run-tests:
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) -f docker-compose-tests.yml run frontendTests
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) -f docker-compose-tests.yml run traceBasedTests

.PHONY: run-tracetesting
run-tracetesting:
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) -f docker-compose-tests.yml run traceBasedTests ${SERVICES_TO_TEST}

.PHONY: generate-protobuf
generate-protobuf:
	./ide-gen-proto.sh

.PHONY: generate-kubernetes-manifests
generate-kubernetes-manifests:
	helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
	helm repo update
	echo "# Copyright The OpenTelemetry Authors" > kubernetes/opentelemetry-demo.yaml
	echo "# SPDX-License-Identifier: Apache-2.0" >> kubernetes/opentelemetry-demo.yaml
	echo "# This file is generated by 'make generate-kubernetes-manifests'" >> kubernetes/opentelemetry-demo.yaml
	echo "---" >> kubernetes/opentelemetry-demo.yaml
	echo "apiVersion: v1" >> kubernetes/opentelemetry-demo.yaml
	echo "kind: Namespace" >> kubernetes/opentelemetry-demo.yaml
	echo "metadata:" >> kubernetes/opentelemetry-demo.yaml
	echo "  name: otel-demo" >> kubernetes/opentelemetry-demo.yaml
	helm template opentelemetry-demo open-telemetry/opentelemetry-demo --namespace otel-demo | sed '/helm.sh\/chart\:/d' | sed '/helm.sh\/hook/d' | sed '/managed-by\: Helm/d' >> kubernetes/opentelemetry-demo.yaml

.PHONY: docker-generate-protobuf
docker-generate-protobuf:
	./docker-gen-proto.sh

.PHONY: clean
clean:
	rm -rf ./src/{checkout,product-catalog}/genproto/oteldemo/
	rm -rf ./src/recommendation/{demo_pb2,demo_pb2_grpc}.py
	rm -rf ./src/frontend/protos/demo.ts

.PHONY: check-clean-work-tree
check-clean-work-tree:
	@if ! git diff --quiet; then \
	  echo; \
	  echo 'Working tree is not clean, did you forget to run "make docker-generate-protobuf"?'; \
	  echo; \
	  git status; \
	  exit 1; \
	fi

.PHONY: start
start:
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) up --force-recreate --remove-orphans --detach
	@echo ""
	@echo "OpenTelemetry Demo is running."
	@echo "Go to http://localhost:8080 for the demo UI."
	@echo "Go to http://localhost:8080/jaeger/ui for the Jaeger UI."
	@echo "Go to http://localhost:8080/grafana/ for the Grafana UI."
	@echo "Go to http://localhost:8080/loadgen/ for the Load Generator UI."
	@echo "Go to http://localhost:8080/feature/ to change feature flags."

.PHONY: start-minimal
start-minimal:
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) -f docker-compose.minimal.yml up --force-recreate --remove-orphans --detach
	@echo ""
	@echo "OpenTelemetry Demo in minimal mode is running."
	@echo "Go to http://localhost:8080 for the demo UI."
	@echo "Go to http://localhost:8080/jaeger/ui for the Jaeger UI."
	@echo "Go to http://localhost:8080/grafana/ for the Grafana UI."
	@echo "Go to http://localhost:8080/loadgen/ for the Load Generator UI."
	@echo "Go to https://opentelemetry.io/docs/demo/feature-flags/ to learn how to change feature flags."

.PHONY: stop
stop:
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) down --remove-orphans --volumes
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) -f docker-compose-tests.yml down --remove-orphans --volumes
	@echo ""
	@echo "OpenTelemetry Demo is stopped."

# Use to restart a single service component
# Example: make restart service=frontend
.PHONY: restart
restart:
# work with `service` or `SERVICE` as input
ifdef SERVICE
	service := $(SERVICE)
endif

ifdef service
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) stop $(service)
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) rm --force $(service)
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) create $(service)
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) start $(service)
else
	@echo "Please provide a service name using `service=[service name]` or `SERVICE=[service name]`"
endif

# Use to rebuild and restart (redeploy) a single service component
# Example: make redeploy service=frontend
.PHONY: redeploy
redeploy:
# work with `service` or `SERVICE` as input
ifdef SERVICE
	service := $(SERVICE)
endif

ifdef service
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) build $(DOCKER_COMPOSE_BUILD_ARGS) $(service)
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) stop $(service)
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) rm --force $(service)
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) create $(service)
	$(DOCKER_COMPOSE_CMD) $(DOCKER_COMPOSE_ENV) start $(service)
else
	@echo "Please provide a service name using `service=[service name]` or `SERVICE=[service name]`"
endif

.PHONY: build-react-native-android
build-react-native-android:
	docker build -f src/react-native-app/android.Dockerfile --platform=linux/amd64 --output=. src/react-native-app
