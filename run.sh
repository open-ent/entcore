#!/bin/bash
docker-compose up -d neo4j > /dev/null &
sleep 10
vertx runMod org.entcore~infra~2.0.0 > /dev/null &
