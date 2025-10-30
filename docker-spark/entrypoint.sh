#!/bin/bash

# Define the full path to your application JAR within the container
APP_JAR_PATH="/opt/shared/flight_prediction.jar"
cp /practica_creativa/flight_prediction/target/scala-2.12/flight_prediction_2.12-0.1.jar "$APP_JAR_PATH"

# Copy your trained models
cp -r /practica_creativa/models /opt/shared/models

# Define package versions
MONGO_SPARK_CONNECTOR_VERSION="10.4.1"
KAFKA_SPARK_CONNECTOR_VERSION="3.4.0"

# --- Debugging: Print the exact command being executed ---
set -x

# Hacemos disponibles los jars que hay en el volumen compartido a todos los nodos del cluster
exec /opt/spark/bin/spark-submit \
  --class es.upm.dit.ging.predictor.MakePrediction \
  --master "$SPARK_MASTER_URL" \
  --deploy-mode cluster \
  --jars $(echo /home/spark/.ivy2/jars/*.jar | tr ' ' ',') \
  --packages org.mongodb.spark:mongo-spark-connector_2.12:${MONGO_SPARK_CONNECTOR_VERSION},org.apache.spark:spark-sql-kafka-0-10_2.12:${KAFKA_SPARK_CONNECTOR_VERSION} \
  --conf spark.jars.ivy=/home/spark/.ivy2 \
  "$APP_JAR_PATH"

# --- Execute the Spark application with --packages ---
#exec /opt/spark/bin/spark-submit \
#  --class es.upm.dit.ging.predictor.MakePrediction \
#  --master "$SPARK_MASTER_URL" \
#  --deploy-mode cluster \
#  --jars /home/spark/.ivy2/jars/org.mongodb_mongodb-spark-connector_2.12-10.4.1.jar,/home/spark/.ivy2/jars/org.apache.spark_spark-sql-kafka-0-10_2.12-3.4.0.jar \
#  --packages org.mongodb.spark:mongo-spark-connector_2.12:${MONGO_SPARK_CONNECTOR_VERSION},org.apache.spark:spark-sql-kafka-0-10_2.12:${KAFKA_SPARK_CONNECTOR_VERSION} \
#  --conf spark.jars.ivy=/home/spark/.ivy2 \
#  "$APP_JAR_PATH"
