#!/bin/bash

set -euxo pipefail

JAR=$1

java -jar $JAR standalone\
  --config src/test/resources/ranklens/config.yml\
  --data src/test/resources/ranklens/events/ & echo $! > /tmp/inference.pid

PID=$(cat /tmp/inference.pid)

echo "Waiting for http server with pid=$PID to come online..."

while ! nc -z localhost 8080; do
  sleep 5
  echo "Trying to connect to :8080"
done

curl -X POST http://localhost:8080/rank/xgboost -d '{
    "event": "ranking",
    "id": "id1",
    "items": [
        {"id":"72998"}, {"id":"67197"}, {"id":"77561"}, {"id":"68358"}, {"id":"79132"}, {"id":"103228"},
        {"id":"72378"}, {"id":"85131"}, {"id":"94864"}, {"id":"68791"}, {"id":"93363"}, {"id":"112623"},
        {"id":"109487"}, {"id":"59315"}, {"id":"120466"}, {"id":"90405"}, {"id":"122918"}, {"id":"70286"},
        {"id":"117529"}, {"id":"130490"}, {"id":"92420"}, {"id":"122882"}, {"id":"87306"}, {"id":"82461"},
        {"id":"113345"}, {"id":"2571"}, {"id":"122900"}, {"id":"88744"}, {"id":"111360"}, {"id":"134130"},
        {"id":"95875"}, {"id":"60069"}, {"id":"2021"}, {"id":"135567"}, {"id":"103253"}, {"id":"111759"},
        {"id":"122902"}, {"id":"104243"}, {"id":"112852"}, {"id":"102880"}, {"id":"56174"}, {"id":"107406"},
        {"id":"96610"}, {"id":"741"}, {"id":"166528"}, {"id":"164179"}, {"id":"187595"}, {"id":"589"},
        {"id":"71057"}, {"id":"3527"}, {"id":"6365"}, {"id":"6934"}, {"id":"1270"}, {"id":"6502"},
        {"id":"114935"}, {"id":"8810"}, {"id":"173291"}, {"id":"1580"}, {"id":"182715"}, {"id":"166635"},
        {"id":"1917"}, {"id":"135569"}, {"id":"106920"}, {"id":"1240"}, {"id":"5502"}, {"id":"316"},
        {"id":"85056"}, {"id":"780"}, {"id":"1527"}, {"id":"5459"}, {"id":"94018"}, {"id":"33493"},
        {"id":"8644"}, {"id":"60684"}, {"id":"7254"}, {"id":"44191"}, {"id":"101864"}, {"id":"132046"},
        {"id":"97752"}, {"id":"2628"}, {"id":"541"}, {"id":"106002"}, {"id":"1200"}, {"id":"5378"},
        {"id":"2012"}, {"id":"79357"}, {"id":"6283"}, {"id":"113741"}, {"id":"90345"}, {"id":"2011"},
        {"id":"27660"}, {"id":"34048"}, {"id":"1882"}, {"id":"1748"}, {"id":"2985"}, {"id":"104841"},
        {"id":"34319"}, {"id":"1097"}, {"id":"115713"}, {"id":"2916"}
    ],
    "user": "alice",
    "session": "alice1",
    "timestamp": 1661345221008
}'

curl -X POST http://localhost:8080/feedback -d '{
    "event": "ranking",
    "id": "id1",
    "items": [
        {"id":"72998"}, {"id":"589"}, {"id":"134130"}, {"id":"5459"},
        {"id":"1917"}, {"id":"2571"}, {"id":"1527"}, {"id":"97752"},
        {"id":"1270"}, {"id":"1580"}, {"id":"109487"}, {"id":"79132"}
    ],
    "user": "alice",
    "session": "alice1",
    "timestamp": 1661345221008
}'

curl -X POST -v http://localhost:8080/feedback -d '{
    "event": "interaction",
    "type": "click",
    "id": "id2",
    "ranking": "id1",
    "item": "1580",
    "user": "alice",
    "session": "alice1",
    "timestamp": "1661345221008"
}'

kill -TERM $PID
