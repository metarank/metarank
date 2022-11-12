#!/usr/bin/env bash

set -euxo pipefail

DIR=$1

echo "generating certs"
openssl req -nodes -newkey rsa:2048 -keyout $DIR/redistls.key -x509 -days 365 -out $DIR/redistls.crt \
  -subj "/C=DE/ST=Berlin/L=Berlin/O=My Inc/OU=DevOps/CN=localhost/emailAddress=dev@www.example.com"

echo "setting permissions"
chmod +r $DIR/redistls.key