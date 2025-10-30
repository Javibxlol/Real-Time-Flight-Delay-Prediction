#!/bin/bash
set -e

echo "MongoDB ya está iniciado y listo, comenzando importación..."

# Import our enriched airline data as the 'airlines' collection
echo "Importando datos..."
mongoimport -d agile_data_science -c origin_dest_distances --file origin_dest_distances.jsonl

# Crear índice
echo "Creando índice..."
#mongo agile_data_science --eval 'db.origin_dest_distances.createIndex({Origin: 1, Dest: 1})'

# No necesitamos mantener el proceso en ejecución, ya que la imagen base 
# de MongoDB se encargará de mantener el contenedor activo
echo "Importación completada. MongoDB está ejecutándose."