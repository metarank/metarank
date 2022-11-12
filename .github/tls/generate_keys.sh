#!/usr/bin/env sh

set -euxo pipefail

echo "generating certs"
openssl req -nodes -newkey rsa:2048 -keyout redistls.key -x509 -days 365 -out redistls.crt \
  -subj "/C=DE/ST=Berlin/L=Berlin/O=My Inc/OU=DevOps/CN=localhost/emailAddress=dev@www.example.com"

echo "setting permissions"
chmod +r redistls.key