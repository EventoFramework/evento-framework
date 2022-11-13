#!/bin/sh
curl $APP_JAR_URL -o app.jar
java $VM_ARGS -jar app.jar