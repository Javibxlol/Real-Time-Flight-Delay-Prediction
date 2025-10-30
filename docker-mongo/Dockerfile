FROM mongo:4.0

WORKDIR /data

# Copiar datos y script de carga
COPY ./origin_dest_distances.jsonl /data
COPY ./origin_dest_distances.py /data

# Copiar script para iniciar la base de datos
COPY load_data.sh /data


# Exponer el puerto por defecto de MongoDB (ya está expuesto en la imagen base, pero es buena práctica documentarlo)
EXPOSE 27017

# Copiar script para iniciar la base de datos
COPY load_data.sh /docker-entrypoint-initdb.d/

# Hacer el script de entrada ejecutable
RUN chmod +x /docker-entrypoint-initdb.d/load_data.sh

# Hacer el script de entrada ejecutable
#RUN chmod +x /data/load_data.sh

# Ejecutar el script al iniciar el contenedor
#CMD ["/data/load_data.sh"]