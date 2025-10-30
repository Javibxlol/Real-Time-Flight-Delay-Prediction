// Attach a submit handler to the form
$( "#flight_delay_classification" ).submit(function( event ) {

  // Stop form from submitting normally
  event.preventDefault();

  // Get some values from elements on the page:
  var $form = $( this ),
    url = $form.attr( "action" );

  // Send the data using post
  var posting = $.post(
    url,
    $( "#flight_delay_classification" ).serialize()
  );

  // Submit the form and parse the response
  posting.done(function( data ) {
    var response = JSON.parse(data);

    // If the response is ok, print a message to wait and start polling/SSE
    if(response.status == "OK") {
      // For MongoDB polling
      $( "#mongo_result" ).empty().append( "Processing via MongoDB..." );
      pollMongo(response.id);

      // For Kafka SSE
      $( "#kafka_result" ).empty().append( "Waiting for Kafka event..." );
      subscribeToKafkaEvents(response.id);
    } else {
      $( "#mongo_result" ).empty().append( "Error submitting request." );
      $( "#kafka_result" ).empty().append( "Error submitting request." );
    }
  });

  posting.fail(function() {
    $( "#mongo_result" ).empty().append( "Error submitting request (POST failed)." );
    $( "#kafka_result" ).empty().append( "Error submitting request (POST failed)." );
  });
});

// Poll the prediction URL for MongoDB results
function pollMongo(id) {
  var responseUrlBase = "/flights/delays/predict/classify_realtime/response/";
  console.log("Polling MongoDB for request id " + id + "...");

  var predictionUrl = responseUrlBase + id;

  $.ajax(
  {
    url: predictionUrl,
    type: "GET",
    complete: conditionalPollMongo
  });
}

// Decide whether to poll MongoDB based on the response status
function conditionalPollMongo(data) {
  if (!data || !data.responseText) {
    console.error("MongoDB Polling: Invalid data received.");
    $("#mongo_result").empty().append("Error polling MongoDB: Invalid data.");
    return;
  }
  try {
    var response = JSON.parse(data.responseText);

    if(response.status == "OK") {
      renderMongoPage(response.prediction);
    }
    else if(response.status == "WAIT") {
      setTimeout(function() {pollMongo(response.id)}, 1000);
    } else {
      $("#mongo_result").empty().append("Error polling MongoDB: Status " + response.status);
    }
  } catch (e) {
    console.error("MongoDB Polling: Error parsing JSON: ", e, data.responseText);
    $("#mongo_result").empty().append("Error polling MongoDB: Invalid JSON response.");
  }
}

// Render the response on the page for MongoDB results
function renderMongoPage(predictionData) {
  console.log("MongoDB Prediction Data:", predictionData);
  var displayMessage = getDisplayMessage(predictionData.Prediction);
  $( "#mongo_result" ).empty().append( displayMessage );
}

// Subscribe to Kafka SSE
function subscribeToKafkaEvents(id) {
  var eventSourceUrl = "/flights/delays/predict/kafka_event_stream/" + id;
  console.log("Subscribing to Kafka SSE for request id " + id + " at " + eventSourceUrl);
  var eventSource = new EventSource(eventSourceUrl);

  eventSource.onmessage = function(event) {
    console.log("Kafka SSE: Message received", event.data);
    try {
      var predictionData = JSON.parse(event.data);
      if (predictionData.error) {
        console.error("Kafka SSE: Error from server:", predictionData.error);
        $("#kafka_result").empty().append("Error from Kafka stream: " + predictionData.error);
      } else if (predictionData.status === 'TIMEOUT') {
        $("#kafka_result").empty().append("No message received from Kafka within timeout.");
      }else {
        renderKafkaPage(predictionData);
      }
    } catch (e) {
      console.error("Kafka SSE: Error parsing JSON from event data: ", e, event.data);
      $("#kafka_result").empty().append("Error processing Kafka event: Invalid JSON.");
    }
    eventSource.close();
    console.log("Kafka SSE: Connection closed for id " + id);
  };

  eventSource.onerror = function(err) {
    console.error("Kafka SSE: EventSource failed:", err);
    $( "#kafka_result" ).empty().append( "Error connecting to Kafka event stream or stream ended." );
    eventSource.close();
  };
}

// Render the response on the page for Kafka results
function renderKafkaPage(predictionData) {
  console.log("Kafka Prediction Data:", predictionData);
  var displayMessage = getDisplayMessage(predictionData.prediction);
  $( "#kafka_result" ).empty().append( displayMessage );
}

// Helper function to get display message from prediction value
function getDisplayMessage(predictionValue) {
  var displayMessage;
  if(predictionValue == 0 || predictionValue == '0') {
    displayMessage = "Early (15+ Minutes Early)";
  }
  else if(predictionValue == 1 || predictionValue == '1') {
    displayMessage = "Slightly Early (0-15 Minute Early)";
  }
  else if(predictionValue == 2 || predictionValue == '2') {
    displayMessage = "Slightly Late (0-30 Minute Delay)";
  }
  else if(predictionValue == 3 || predictionValue == '3') {
    displayMessage = "Very Late (30+ Minutes Late)";
  }
  else if (predictionValue === undefined) {
    displayMessage = "Prediction data not available or in unexpected format.";
  } else {
    displayMessage = "Unknown prediction value: " + predictionValue;
  }
  return displayMessage;
}
