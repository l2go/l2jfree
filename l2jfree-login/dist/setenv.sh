CLASSPATH=${CLASSPATH}:./libs/*

# for configuration
CLASSPATH=${CLASSPATH}:./config/
CLASSPATH=${CLASSPATH}:./*
CLASSPATH=${CLASSPATH}:.

export CLASSPATH

# Operators edit config/logback.xml. The copy inside the jars is not the runtime configuration.
export JAVA_LOGGING_OPTS="-Dlogback.configurationFile=config/logback.xml"

JAVA_AGENT_OPTS=""
if [[ "${L2JFREE_OTEL_ENABLED:-false}" == "true" ]]; then
	if [[ -z "${OTEL_EXPORTER_OTLP_ENDPOINT:-}" ]]; then
		echo "OpenTelemetry is enabled but OTEL_EXPORTER_OTLP_ENDPOINT is not set." >&2
		exit 1
	fi
	if [[ ! -f ./agents/opentelemetry-javaagent.jar ]]; then
		echo "OpenTelemetry agent is missing from the LoginServer distribution." >&2
		exit 1
	fi
	export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-l2jfree-loginserver}"
	export OTEL_METRICS_EXPORTER=otlp
	export OTEL_TRACES_EXPORTER=otlp
	export OTEL_LOGS_EXPORTER=none
	JAVA_AGENT_OPTS="-javaagent:./agents/opentelemetry-javaagent.jar"
fi
export JAVA_AGENT_OPTS
