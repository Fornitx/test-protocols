#!/bin/bash

rm -vf *.key *.pem *.crt *.csr *.srl *.p12

openssl ecparam -name prime256v1 -genkey -noout -out root-ca.key
openssl ecparam -name prime256v1 -genkey -noout -out server.key
openssl ecparam -name prime256v1 -genkey -noout -out client.key

openssl req -new -x509 -sha256 -days 3650 -key root-ca.key -out root-ca.crt -nodes -subj "/CN=Root CA" \
        -addext "subjectKeyIdentifier = hash" \
        -addext "authorityKeyIdentifier = keyid,issuer" \
        -addext "basicConstraints = critical, CA:true" \
        -addext "subjectAltName = DNS:localhost, IP:127.0.0.1" \
        -addext "keyUsage = critical, digitalSignature, cRLSign, keyCertSign"

openssl req -new -sha256 -key server.key -out server.csr -subj "/CN=Server" -nodes
openssl req -new -sha256 -key client.key -out client.csr -subj "/CN=Client" -nodes

openssl x509 -req -sha256 -days 3650 -in server.csr -CA root-ca.crt -CAkey root-ca.key -CAcreateserial -out server.crt \
        -extfile <(printf "subjectAltName=@alternate_names\n[ alternate_names ]\nDNS.1=localhost\nIP.1=127.0.0.1")
openssl x509 -req -sha256 -days 3650 -in client.csr -CA root-ca.crt -CAkey root-ca.key -CAcreateserial -out client.crt \
        -extfile <(printf "subjectAltName=@alternate_names\n[ alternate_names ]\nDNS.1=localhost\nIP.1=127.0.0.1")

openssl pkcs12 -export -in server.crt -inkey server.key -name server -passout pass:123456 -out server-keystore.p12 -CAfile root-ca.crt -caname root-ca -chain
openssl pkcs12 -export -in client.crt -inkey client.key -name client -passout pass:123456 -out client-keystore.p12 -CAfile root-ca.crt -caname root-ca -chain

keytool -import -file root-ca.crt -alias "root-ca" -keystore truststore.p12 -storetype PKCS12 -storepass 123456 -noprompt

rm -vf *.key *.pem *.crt *.csr *.srl
