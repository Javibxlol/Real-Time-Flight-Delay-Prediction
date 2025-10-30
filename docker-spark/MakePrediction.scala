package es.upm.dit.ging.predictor

import com.mongodb.spark._
import org.apache.spark.ml.classification.RandomForestClassificationModel
import org.apache.spark.ml.feature.{VectorAssembler}
import org.apache.spark.sql.functions.{concat, from_json, lit, struct => spark_struct, to_json} // Añadido to_json y renombrado struct
import org.apache.spark.sql.types.{DataTypes, StructType}
import org.apache.spark.sql.{DataFrame, SparkSession}

object MakePrediction {

  def main(args: Array[String]): Unit = {
    println("Flight predictor starting...")

    val spark = SparkSession
      .builder
      .appName("FlightPredictionStreaming") // Nombre de app más descriptivo
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.kryo.registrationRequired", "false")
      //.master("local[*]")
      // Configuración de conexión a Mongo a nivel de SparkSession (opcional si se especifica en writeStream)
      .config("spark.mongodb.connection.uri", "mongodb://mongo:27017")
      .config("spark.mongodb.database", "agile_data_science")
      .config("spark.mongodb.collection", "flight_delay_ml_response")
      .getOrCreate()
    import spark.implicits._

    val base_path = "/opt/shared"

    // --- Carga de Modelos y Transformadores ---

    // Eliminada la carga del Bucketizer (no usado en el pipeline mostrado)
    // val arrivalBucketizerPath = "%s/models/arrival_bucketizer_2.0.bin".format(base_path)
    // println(arrivalBucketizerPath.toString()) // Usado println para evitar el print raro
    // val arrivalBucketizer = Bucketizer.load(arrivalBucketizerPath)

    // Eliminada la carga de StringIndexerModels (asumiendo que los campos _index vienen en el JSON)
    // val columns= Seq("Carrier","Origin","Dest","Route")
    // val stringIndexerModelPath =  columns.map(n=> ("%s/models/string_indexer_model_"
    //    .format(base_path)+"%s.bin".format(n)).toSeq)
    // val stringIndexerModel = stringIndexerModelPath.map{n => StringIndexerModel.load(n.toString)}
    // val stringIndexerModels  = (columns zip stringIndexerModel).toMap

    // Carga el numeric vector assembler
    val vectorAssemblerPath = "%s/models/numeric_vector_assembler.bin".format(base_path)
    val vectorAssembler = VectorAssembler.load(vectorAssemblerPath)

    // Carga el modelo clasificador
    val randomForestModelPath = "%s/models/spark_random_forest_classifier.flight_delays.5.0.bin".format(
      base_path)
    val rfc = RandomForestClassificationModel.load(randomForestModelPath)

    // --- Proceso de Peticiones de Predicción en Streaming ---

    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka:9092") // <-- Servidor(es) de Kafka
      .option("subscribe", "flight-delay-ml-request") // <-- Topic de Kafka a leer
      .load()
    println("Kafka stream started. Raw schema:") // Mensaje indicando inicio de lectura Kafka
    df.printSchema()


    val flightJsonDf = df.selectExpr("CAST(value AS STRING)")

    // Define el esquema del JSON de entrada (incluye los campos _index y Prediction)
    val struct = new StructType()
      .add("Origin", DataTypes.StringType)
      .add("FlightNum", DataTypes.StringType)
      .add("DayOfWeek", DataTypes.IntegerType)
      .add("DayOfYear", DataTypes.IntegerType)
      .add("DayOfMonth", DataTypes.IntegerType)
      .add("Dest", DataTypes.StringType)
      .add("DepDelay", DataTypes.DoubleType)
      .add("Prediction", DataTypes.StringType) // Este campo viene en el JSON de entrada, aunque el modelo generará uno nuevo
      .add("Timestamp", DataTypes.TimestampType)
      .add("FlightDate", DataTypes.DateType)
      .add("Carrier", DataTypes.StringType)
      .add("UUID", DataTypes.StringType)
      .add("Distance", DataTypes.DoubleType)
      .add("Carrier_index", DataTypes.DoubleType) // Asumiendo que estos campos vienen ya indexados en el JSON
      .add("Origin_index", DataTypes.DoubleType)
      .add("Dest_index", DataTypes.DoubleType)
      .add("Route_index", DataTypes.DoubleType)


    val flightNestedDf = flightJsonDf.select(from_json($"value", struct).as("flight"))
    println("Parsed JSON schema (nested):")
    flightNestedDf.printSchema()

    // --- Pipeline de ML simplificado ---

    // Extrae todos los campos relevantes (incluyendo los *_index del JSON) y añade la columna Route
    // Esto crea UN DataFrame aplanado con todas las features de entrada necesarias
    val flightFlattenedDf = flightNestedDf.selectExpr(
        "flight.Origin",
        "flight.DayOfWeek",
        "flight.DayOfYear",
        "flight.DayOfMonth",
        "flight.Dest",
        "flight.DepDelay",
        "flight.Timestamp",
        "flight.FlightDate",
        "flight.Carrier",
        "flight.UUID",
        "flight.Distance",
        "flight.Carrier_index", // <-- Incluir los campos _index del JSON para el assembler
        "flight.Origin_index",
        "flight.Dest_index",
        "flight.Route_index"
    ).withColumn( // Añadir la columna 'Route' derivada
      "Route",
      concat($"Origin", lit('-'), $"Dest")
    )

    println("Flattened DataFrame schema:")
    flightFlattenedDf.printSchema()

    // Aplica el VectorAssembler al DataFrame aplanado.
    // Asumimos que el 'vectorAssembler' cargado ya tiene sus inputCols y outputCol definidos correctamente
    // para las columnas numéricas y las columnas *_index.
    val vectorizedFeatures = vectorAssembler
        .setHandleInvalid("keep") // Mantener el comportamiento original para valores inválidos
        .transform(flightFlattenedDf) // Aplica el transformador

    println("Vectorized features schema:")
    vectorizedFeatures.printSchema()

    // Drop las columnas de índice individuales que ahora están incluidas en el vector de features.
    val finalVectorizedFeatures = vectorizedFeatures
        .drop("Carrier_index")
        .drop("Origin_index")
        .drop("Dest_index")
        .drop("Route_index")
        // Opcional: Dropear también las columnas string originales si no las necesitas en la salida final
        // .drop("Origin").drop("Dest").drop("Carrier").drop("Route")


    println("Final features schema before prediction:")
    finalVectorizedFeatures.printSchema()

    // Realiza la predicción usando el modelo RandomForest.
    // El modelo leerá la columna de features (por defecto 'features') y añadirá columnas como 'rawPrediction', 'probability', 'prediction'.
    val predictions = rfc.transform(finalVectorizedFeatures)

    println("Predictions schema:")
    predictions.printSchema()

    // Limpia el DataFrame resultante para obtener las columnas originales más la columna 'prediction'.
    // Dropea las columnas intermedias de predicción y la columna de features que se usó como entrada al modelo.
    val finalPredictions = predictions
      .drop("rawPrediction")
      .drop("probability")
      .drop(vectorAssembler.getOutputCol) // Dropea la columna de features usando el nombre de salida del assembler
      // Las siguientes líneas de drop ("indices", "values") del código original son inusuales
      // en este contexto y se asume que no son necesarias o que su origen era un error.
      // .drop("indices")
      // .drop("values")


    println("Final output schema (original fields + prediction):")
    finalPredictions.printSchema() // Deberías ver tus campos originales + la columna 'prediction'

    // --- Escritura de Resultados en Streaming ---

    // Define una consulta de streaming para escribir en MongoDB
    // ESTA SECCIÓN NO SE MODIFICA, ES LA ESCRITURA ORIGINAL A MONGO
    val mongoDataStreamWriter = finalPredictions // El DataFrame con las predicciones finales y campos originales
      .writeStream
      .format("mongodb") // Usando el conector de MongoDB Spark
      .option("spark.mongodb.connection.uri", "mongodb://mongo:27017") // URI de conexión a Mongo
      .option("spark.mongodb.database", "agile_data_science") // Especifica la base de datos
      .option("checkpointLocation", "/tmp/checkpoint_flight_predictions") // Ubicación para checkpoints (usar ruta persistente en prod)
      .option("spark.mongodb.collection", "flight_delay_ml_response") // Especifica la colección
      .outputMode("append") // Añade nuevos resultados

    // Inicia la escritura en streaming a Mongo
    val mongoQuery = mongoDataStreamWriter.start()
    println("MongoDB streaming query started.")

    // --- AÑADIMOS LA ESCRITURA AL NUEVO TOPIC DE KAFKA ---

    // Prepara el DataFrame para escribir en Kafka:
    // Necesitamos una columna 'value' que sea un string JSON y una columna 'key' (opcional, pero buena práctica).
    val kafkaOutputDf = finalPredictions
      .select(
        $"UUID".as("key"), // Usamos el UUID del vuelo como clave para el mensaje Kafka
        // Convertimos las columnas deseadas en una estructura y luego a un string JSON
        to_json(
          spark_struct(
            $"Origin",
            //$"FlightNum",
            $"DayOfWeek",
            $"DayOfYear",
            $"DayOfMonth",
            $"Dest",
            $"DepDelay",
            $"prediction", // Se asume que este campo es la predicción final
            $"Timestamp",
            $"FlightDate",
            $"Carrier",
            $"UUID",
            $"Distance"
            // Si renombraste "prediction" a "predicted_delay", deberías usar $"predicted_delay" aquí
            // Por ejemplo: $"predicted_delay"
          )
        ).as("value") // El resultado de to_json será la columna 'value' del mensaje Kafka
      )

    println("Kafka output DataFrame schema:")
    kafkaOutputDf.printSchema()

    // Define e inicia la consulta de streaming para Kafka
    val kafkaQuery = kafkaOutputDf
      .writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka:9092") // Servidores de Kafka
      .option("topic", "flight-delay-ml-response") // El nuevo topic de destino
      .option("checkpointLocation", "/tmp/checkpoint_flight_predictions_kafka") // Ubicación de checkpoint única para Kafka
      .outputMode("append") // Modo de salida: añadir nuevos registros
      .start()
    println("Kafka streaming query to 'flight-delay-ml-response' started.")


    // --- AÑADIMOS ESCRITURA A HDFS ---
    val hdfsQuery = finalPredictions
      .writeStream
      .format("json") 
      .option("path", "hdfs://namenode:9000/user/flight_predictions") // Ruta HDFS donde se guardarán los archivos
      .option("checkpointLocation", "/tmp/checkpoint_flight_predictions_hdfs") // Ruta de checkpoint distinta
      .outputMode("append") // Añade nuevos datos conforme llegan
      .start()
    println("HDFS streaming query started.")

    // Salida a Consola para depuración (opcional, pero útil)
    val consoleOutput = finalPredictions.writeStream
      .outputMode("append")
      .format("console") // Escribe también a la consola
      //.option("truncate", "false") // Opcional: evita que los campos largos se trunquen en la consola
      .start()
    println("Console streaming query started.")

    // Espera a que la consulta de consola termine (mantiene la aplicación corriendo)
    // Esto bloquea el hilo principal. Si usaras otra lógica de terminación, podrías esperar por 'mongoQuery' o 'kafkaQuery' en su lugar.
    consoleOutput.awaitTermination()

    // Si quieres que espere por el query de Mongo en lugar del de consola:
    // mongoQuery.awaitTermination()
    // O si quieres que espere por el query de Kafka:
    // kafkaQuery.awaitTermination()
  }
}