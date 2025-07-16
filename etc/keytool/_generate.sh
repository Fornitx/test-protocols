#!/bin/bash

rm -vf *.p12

# Generate server and client EC keys
keytool -v -genkeypair -alias server -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -validity 3650 \
        -keystore server-keystore.p12 -storeType PKCS12 -keypass 123456 -storepass 123456 -dname "CN=Server" \
        -ext "KeyUsage=digitalSignature,dataEncipherment,keyEncipherment,keyAgreement" \
        -ext "ExtendedKeyUsage=serverAuth,clientAuth" \
        -ext "SubjectAlternativeName:c=DNS:localhost,IP:127.0.0.1"
keytool -v -genkeypair -alias client -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -validity 3650 \
        -keystore client-keystore.p12 -storeType PKCS12 -keypass 123456 -storepass 123456 -dname "CN=Client" \
        -ext "KeyUsage=digitalSignature,dataEncipherment,keyEncipherment,keyAgreement" \
        -ext "ExtendedKeyUsage=serverAuth,clientAuth" \
        -ext "SubjectAlternativeName:c=DNS:localhost,IP:127.0.0.1"

# Export public certificates for both the client and server
keytool -v -exportcert -alias client -file client.cer -keystore client-keystore.p12 -storepass 123456
keytool -v -exportcert -alias server -file server.cer -keystore server-keystore.p12 -storepass 123456

# Import the client and server public certificates into each others keystore
keytool -v -importcert -keystore server-truststore.p12 -alias client -file client.cer -storepass 123456 -noprompt
keytool -v -importcert -keystore client-truststore.p12 -alias server -file server.cer -storepass 123456 -noprompt

rm -vf *.cer
