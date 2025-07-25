#!/bin/bash -e

./prepare.sh

./wait-for-it.sh ${DATABASE_TRUSTDECK_HOST}:${DATABASE_TRUSTDECK_PORT} -s -t 120

java -jar trustdeck.jar --spring.config.location=run.yml
