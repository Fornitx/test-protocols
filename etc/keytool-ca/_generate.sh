#!/bin/bash

rm -vf *.csr *.cer *.crt *.pem *.p12

# Create Certificate Authority (CA) EC private key
keytool -v -genkeypair -alias root-ca -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -validity 3650 \
        -keystore root-ca.p12 -storeType PKCS12 -keypass 123456 -storepass 123456 -dname "CN=Root CA" \
        -ext "KeyUsage=digitalSignature,keyCertSign" -ext "BasicConstraints=ca:true,PathLen:3"

# Create server and client EC private keys
keytool -v -genkeypair -alias server -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -validity 3650 \
        -keystore server-keystore.p12 -storeType PKCS12 -keypass 123456 -storepass 123456 -dname "CN=Server" \
        -ext "KeyUsage=digitalSignature,dataEncipherment,keyEncipherment,keyAgreement" \
        -ext "ExtendedKeyUsage=serverAuth,clientAuth" \
        -ext "SubjectAlternativeName:c=DNS:localhost,IP:127.0.0.1"
keytool -v -genkeypair -alias client -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -validity 3650 \
        -keystore client-keystore.p12 -storeType PKCS12 -keypass 123456 -storepass 123456 -dname "CN=Client" \
        -ext "KeyUsage=digitalSignature,dataEncipherment,keyEncipherment,keyAgreement" \
        -ext "ExtendedKeyUsage=serverAuth,clientAuth" \

# Create certificate signing request (CSR) for server and client
keytool -v -certreq -file server.csr -keystore server-keystore.p12 -alias server -keypass 123456 -storepass 123456 -keyalg ec
keytool -v -certreq -file client.csr -keystore client-keystore.p12 -alias client -keypass 123456 -storepass 123456 -keyalg ec

# Signing server and client certificates with CA
keytool -v -gencert -infile server.csr -outfile server.cer -keystore root-ca.p12 -storepass 123456 -alias root-ca -validity 3650 \
        -ext "KeyUsage=digitalSignature,dataEncipherment,keyEncipherment,keyAgreement" \
        -ext "ExtendedKeyUsage=serverAuth,clientAuth" \
        -ext "SubjectAlternativeName:c=DNS:localhost,IP:127.0.0.1"
keytool -v -gencert -infile client.csr -outfile client.cer -keystore root-ca.p12 -storepass 123456 -alias root-ca -validity 3650 \
        -ext "KeyUsage=digitalSignature,dataEncipherment,keyEncipherment,keyAgreement" \
        -ext "ExtendedKeyUsage=serverAuth,clientAuth"

# Export CA Certificate
keytool -v -exportcert -file root-ca.pem -alias root-ca -keystore root-ca.p12 -storepass 123456 -noprompt -rfc

# Import CA certificate into server and client keystores
keytool -v -importcert -alias root-ca -keystore server-keystore.p12 -file root-ca.pem -storepass 123456 -noprompt
keytool -v -importcert -alias root-ca -keystore client-keystore.p12 -file root-ca.pem -storepass 123456 -noprompt

# Import signed and client certificates
keytool -v -importcert -alias server -keystore server-keystore.p12 -file server.cer -storepass 123456 -noprompt
keytool -v -importcert -alias client -keystore client-keystore.p12 -file client.cer -storepass 123456 -noprompt

# Delete imported CA certificate from server and client keystores
keytool -v -delete -alias root-ca -keystore server-keystore.p12 -storepass 123456
keytool -v -delete -alias root-ca -keystore client-keystore.p12 -storepass 123456

# Create CA truststore
keytool -v -importcert -file root-ca.pem -alias root-ca -keystore truststore.p12 -storepass 123456 -noprompt

rm -vf *.csr *.cer *.crt *.pem
