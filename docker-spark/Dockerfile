FROM apache/spark:3.4.0-scala2.12-java11-python3-ubuntu


# Instalar dependencias necesarias
RUN apt update && apt -y install wget git  

# Clonar el repositorio
WORKDIR /
RUN git clone https://github.com/Big-Data-ETSIT/practica_creativa

# Descargar y descomprimir sbt
RUN wget -q -O - "https://github.com/sbt/sbt/releases/download/v1.8.3/sbt-1.8.3.tgz" | tar xz 

# Variables de entorno
ENV SPARK_HOME=/opt/spark/
ENV SKLEARN_ALLOW_DEPRECATED_SKLEARN_PACKAGE_INSTALL=True 

# Entrar al directorio del proyecto
WORKDIR /practica_creativa

# Instalar dependencias de Python y preparar datos
RUN pip install -r requirements.txt
RUN resources/download_data.sh
RUN python3 resources/train_spark_mllib_model.py . 

# Añadir archivos personalizados
COPY MakePrediction.scala /practica_creativa/flight_prediction/src/main/scala/es/upm/dit/ging/predictor/MakePrediction.scala 
COPY build.sbt /practica_creativa/flight_prediction/build.sbt 

# Compilar el proyecto Scala con sbt
WORKDIR /practica_creativa/flight_prediction
RUN /sbt/bin/sbt clean
RUN /sbt/bin/sbt package

RUN mkdir -p /home/spark/.ivy2/cache

# Añadir el script de entrada
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# CMD ["tail", "-f", "/dev/null"]

# Ejecutar el script al iniciar el contenedor
ENTRYPOINT ["/entrypoint.sh"]
