#!/bin/bash -e

./prepare.sh

./wait-for-it.sh ${SPRING_DATASOURCE_HOST}:${SPRING_DATASOURCE_PORT} -s -t 120

java -jar ace.jar --spring.config.location=run.yml
