
Dockerizar cada uno de los servicios que componen la arquitectura completa y desplegar el escenario completo usando docker-compose

<img width="1323" height="793" alt="image" src="https://github.com/user-attachments/assets/da6a77af-c521-4ab8-899b-b971c63523d2" />

Cada componente de la arquitectura ha sido dockerizado individualmente, con sus propios Dockerfiles ubicados en directorios específicos (./docker-kafka, ./docker-mongo, ./docker-spark, ./docker-flask). Esto permite una gestión independiente de cada servicio, facilitando su mantenimiento y actualización. El despliegue completo se realiza mediante Docker Compose (“docker compose up --build” dentro de /practica_creativa), que orquesta todos los servicios y establece las dependencias entre ellos.







La red “app-network” definida en el Docker Compose permite la comunicación entre todos los servicios, utilizando los nombres de los contenedores como nombres de host. Esto simplifica la configuración de conexiones entre servicios, ya que no es necesario conocer las direcciones IP específicas.
Los volúmenes compartidos (shared-volume e ivy-cache) permiten que los nodos del clúster Spark compartan datos y dependencias, lo que es esencial para el correcto funcionamiento del procesamiento distribuido.

<img width="862" height="620" alt="image" src="https://github.com/user-attachments/assets/4fb74c82-309b-4c3b-94fb-7ba6aed71ab9" />

Como se observa en la interfaz del Spark Master, hemos desplegado correctamente nuestro cluster Spark con dos workers en funcionamiento. Esto demuestra que nuestra configuración de Docker Compose ha creado una correcta infraestructura distribuida. Además, se puede apreciar que la aplicación ('FlightPredictionStreaming') ya se está ejecutando, con su driver asignado a uno de los workers, confirmando el proceso de envío y ejecución de trabajos.

<img width="1222" height="615" alt="image" src="https://github.com/user-attachments/assets/128cea06-91eb-486a-ac75-fd5acfff3e42" />


Aquí vemos nuestra base de datos agile_data_science, específicamente la colección flight_delay_ml_response, donde llegan y se almacenan las predicciones generadas.

<img width="1217" height="577" alt="image" src="https://github.com/user-attachments/assets/e20036de-4dfa-4e54-84c2-b5d18ff155ae" />

Aquí vemos cómo el consumidor está consumiendo los mensajes de nuestro tópico de Kafka “flight-delay-ml-request”.

<img width="895" height="337" alt="image" src="https://github.com/user-attachments/assets/7f139186-55b2-4de0-a93c-ed155381a6f8" />

Y finalmente, el navegador en el que realizamos las predicciones:

<img width="910" height="263" alt="image" src="https://github.com/user-attachments/assets/a155f0d9-0ba8-472e-8fe1-f6bbe5400fe1" />

Modificar el código para que el resultado de la predicción se escriba en Kafka y se presente en la aplicación (debe mantenerse también que se guarde en Mongo)
Vamos a crear un nuevo topic "flight-delay-ml-response" en Kafka donde se publiquen las predicciones. La aplicación web se suscribirá a dicho topic y la muestra por pantalla. Mejor que haber definido mensajes específicos para la petición: el mensaje, aunque fuera diferente, se leería de la cola para comprobarlo y ya no estaría disponible.

Primero debemos modificar los ficheros necesarios para su implementación:

Como parte fundamental de la configuración de nuestro entorno Kafka, en el entrypoint del servidor definimos la creación de los dos tópicos (el original y el nuevo).

<img width="892" height="427" alt="image" src="https://github.com/user-attachments/assets/32a7f15e-f530-4e60-a27e-065807166fbe" />

En predict_flask.py, añadimos la variable FLIGHT_DELAY_ML_RESPONSE_TOPIC. Esta apunta al tópico de Kafka “flight-delay-ml-response”, donde publicamos las predicciones del modelo.

<img width="895" height="152" alt="image" src="https://github.com/user-attachments/assets/ab53b092-b104-4e5d-8a8e-8cbc6be6abdd" />

Para la implementación de la funcionalidad de respuesta en tiempo real, se desarrolló la ruta “/@app.route('/flights/delays/predict/kafka_event_stream/<unique_id>')”. Esta ruta es fundamental, ya que establece una conexión de Server-Sent Events (SSE) con el cliente, permitiendo el flujo de datos unidireccional desde el servidor hacia el navegador.

Su propósito principal es consumir las predicciones generadas por el modelo para que se publiquen en el tópico “flight-delay-ml-response”

<img width="895" height="296" alt="image" src="https://github.com/user-attachments/assets/5b0943fe-3c67-4dbf-8d9c-f8d155cad4e7" />

También modificamos el archivo flight_delays_predict_kafka.html para asegurarnos de que el navegador pudiera mostrar claramente las predicciones. Por ello, añadimos dos secciones específicas en el HTML para visualizar cómo se ven los resultados de las predicciones en la aplicación, separando lo que viene de Kafka (nuestro nuevo flujo de datos) y lo que se obtiene de Mongo (la base de datos).

<img width="891" height="256" alt="image" src="https://github.com/user-attachments/assets/8942ee6b-2dcd-46b3-8f35-c60325550902" />

Finalmente, para establecer la comunicación en tiempo real en el lado del cliente, se modificó el archivo flight_delay_predict_polling.js. Esta sección del código es crucial porque implementa la suscripción a los Server-Sent Events (SSE) que provienen del flujo de predicciones de Kafka.

<img width="892" height="546" alt="image" src="https://github.com/user-attachments/assets/51bb7c37-40b5-4a2d-b99f-51a29713c415" />

Finalmente, mostramos cómo se ve la predicción en la aplicación, con los resultados tanto en la sección de Kafka como en la de Mongo.

<img width="897" height="352" alt="image" src="https://github.com/user-attachments/assets/b9d6b713-db16-483c-9597-222634142264" />

Asi mismo, verificamos que las predicciones han llegado correctamente al nuevo tópico de Kafka flight-delay-ml-response.

<img width="892" height="215" alt="image" src="https://github.com/user-attachments/assets/5b725588-d053-4ff0-a243-f72dc8ed4f79" />

Escribir las predicciones en HDFS en lugar de MongoDB
Para empezar, clonamos el siguiente repositorio de GitHub (https://github.com/big-data-europe/docker-hadoop.git) que contiene los ficheros de configuración de Docker para Hadoop.

<img width="937" height="136" alt="image" src="https://github.com/user-attachments/assets/439ce4ef-e310-493c-9e82-1f567442fb60" />

Nuestro cluster de HDFS y Spark viven en dos redes de Docker completamente aisladas. La solución es crear una red externa y conectar los servicios de ambos ficheros “docker-compose.yml” a esa misma red.

Por tanto, realizaremos los dos siguientes pasos:

Creamos una red manualmente: Usaremos el comando 

docker network create hadoop-spark-net

Modificaremos ambos docker-compose.yml: Les diremos que, en lugar de crear su propia red por defecto, deben conectarse a la red que hemos creado.

docker-compose.yml (HDFS): Definimos la red personalizada, hadoop-spark-net, y la referenciamos en todos los servicios de hdfs.

<img width="935" height="482" alt="image" src="https://github.com/user-attachments/assets/3d5d80cb-7101-4127-b047-65af3139ba49" />

docker-compose.yml (Original): Definimos la red personalizada, hadoop-spark-net, y la referenciamos en todos los servicios.

<img width="525" height="296" alt="image" src="https://github.com/user-attachments/assets/94613ead-bc8e-4db3-8dc0-01973fa45bb9" />

Así mismo, añadimos en el “MakePrediction.scala” la funcionalidad que permite la escritura directa de los resultados de las predicciones en HDFS.

<img width="962" height="282" alt="image" src="https://github.com/user-attachments/assets/3f83ec32-c3b6-4bec-89ed-3935fe7fb8cf" />

Ahora creamos entrypoint que crea un directorio en HDFS “/user/flight_predictions” y le da permisos completos. En ese directorio es donde se encontrarán nuestras predicciones en formato “json”.

<img width="612" height="217" alt="image" src="https://github.com/user-attachments/assets/53f2a8f9-bafe-4c2f-b342-f9ba1d86d801" />

Finalmente, accedemos a “http://localhost:9870” y navegamos hasta llegar al directorio “/user/flight_predictions” en el que efectivamente vemos las predicciones almacenadas en archivos JSON.

<img width="622" height="307" alt="image" src="https://github.com/user-attachments/assets/1a8efcf6-1617-4808-990b-57f14b27a638" />

Si queremos ver el contenido de cada archivo JSON, nos metemos dentro del contenedor “namenode” y ejecutamos los comandos mostrados en la imagen para acabar observando las predicciones (1 por cada json).

<img width="1117" height="367" alt="image" src="https://github.com/user-attachments/assets/c1739d3f-9a3f-40e5-96cf-3f122f5a17cf" />



















