FROM python:3.10
ENV MONGO_HOST=mongo


RUN apt update && apt -y install wget git  
WORKDIR /
RUN git clone https://github.com/Big-Data-ETSIT/practica_creativa

WORKDIR /practica_creativa
ENV SKLEARN_ALLOW_DEPRECATED_SKLEARN_PACKAGE_INSTALL=True 
RUN pip install -r requirements.txt
RUN pip install Flask kafka-python

COPY predict_flask.py /practica_creativa/resources/web
COPY flight_delay_predict_polling.js /practica_creativa/resources/web/static
COPY flight_delays_predict_kafka.html /practica_creativa/resources/web/templates

EXPOSE 5001
ENTRYPOINT export PROJECT_HOME=/practica_creativa; python3 /practica_creativa/resources/web/predict_flask.py