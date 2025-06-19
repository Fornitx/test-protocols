#!/bin/bash

rm -vf *.key *.pem *.crt *.csr *.srl *.p12

openssl req -new -x509 -sha256 -days 3650 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -noenc -keyout ca.key -out ca.crt \
        -subj "/CN=Root CA" -addext "subjectAltName = DNS:localhost, IP:127.0.0.1"

openssl req -new -sha256 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -noenc -keyout server.key -out server.csr -subj "/CN=Server"
openssl req -new -sha256 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -noenc -keyout client.key -out client.csr -subj "/CN=Client"

openssl x509 -req -days 3650 -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -extfile <(printf "subjectAltName=@alternate_names\n[ alternate_names ]\nDNS.1=localhost\nIP.1=127.0.0.1")
openssl x509 -req -days 3650 -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out client.crt -extfile <(printf "subjectAltName=@alternate_names\n[ alternate_names ]\nDNS.1=localhost\nIP.1=127.0.0.1")

openssl pkcs12 -export -in server.crt -inkey server.key -name server -passout pass:123456 -out server-keystore.p12 -CAfile ca.crt -caname ca -chain
openssl pkcs12 -export -in client.crt -inkey client.key -name client -passout pass:123456 -out client-keystore.p12 -CAfile ca.crt -caname ca -chain

keytool -import -file ca.crt -alias "ca" -keystore truststore.p12 -storetype PKCS12 -storepass 123456 -noprompt

rm -vf *.key *.pem *.crt *.csr *.srl
