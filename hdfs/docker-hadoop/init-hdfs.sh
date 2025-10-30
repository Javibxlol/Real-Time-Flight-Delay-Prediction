#!/bin/bash
# Esperar a que HDFS esté listo
sleep 10

# Crear la carpeta y cambiar permisos
hdfs dfs -mkdir -p /user/flight_predictions
hdfs dfs -chmod -R 777 /user/flight_predictions

# Ejecutar el servicio principal
hdfs --daemon start namenode
tail -f /dev/null  # Para que el contenedor no se pare
