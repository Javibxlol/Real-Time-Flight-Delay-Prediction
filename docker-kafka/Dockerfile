# Utiliza la imagen base de Ubuntu más reciente
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV KAFKA_CLUSTER_ID="lKuonNnrQvuQVwQ7PD2Krg"

# Actualiza los repositorios e instala Java
RUN apt-get update -y
RUN apt-get install -y openjdk-17-jre-headless
RUN apt-get -y install wget


# Descarga Kafka
RUN wget https://dlcdn.apache.org/kafka/3.9.0/kafka_2.12-3.9.0.tgz
RUN tar -xzf kafka_2.12-3.9.0.tgz
RUN mv kafka_2.12-3.9.0 /opt/kafka
COPY server.properties /opt/kafka/config/kraft

# Crea el directorio para los logs de Kafka
RUN mkdir -p /opt/kafka/logs

# Expone el puerto por defecto de Kafka
EXPOSE 9092

# Define el directorio de trabajo
WORKDIR /opt/kafka

# Copia el script de entrada al contenedor
COPY entrypoint.sh /usr/local/bin/

# Haz el script ejecutable
RUN chmod +x /usr/local/bin/entrypoint.sh

RUN bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c config/kraft/server.properties  
#RUN bin/kafka-server-start.sh config/kraft/server.properties

# Define el script de entrada
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

# Define el comando para iniciar el servidor de Kafka cuando el contenedor se ejecute
#CMD ["bin/kafka-server-start.sh", "config/kraft/server.properties"]