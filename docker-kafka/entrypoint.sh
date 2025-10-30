#!/bin/bash
set -e

# Inicia el servidor de Kafka en segundo plano
bin/kafka-server-start.sh config/kraft/server.properties &
KAFKA_PID=$!

# Espera a que el servidor de Kafka esté listo (un método simple es esperar un poco)
sleep 10

# Intenta crear el topic donde se reciben las peticiones para estimar el retraso
bin/kafka-topics.sh \
  --create \
  --bootstrap-server localhost:9092 \
  --replication-factor 1 \
  --partitions 1 \
  --topic flight-delay-ml-request

# Intenta crear el topic donde se reciben las estimaciones de retraso
bin/kafka-topics.sh \
  --create \
  --bootstrap-server localhost:9092 \
  --replication-factor 1 \
  --partitions 1 \
  --topic flight-delay-ml-response

# Espera a que el servidor de Kafka termine (esto en realidad no sucederá en este setup)
wait $KAFKA_PID