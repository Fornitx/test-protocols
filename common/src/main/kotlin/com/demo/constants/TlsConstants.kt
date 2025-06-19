package com.demo.constants

import java.io.File
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

val SERVER_KEYSTORE = File("etc/openssl/server-keystore.p12")
const val SERVER_ALIAS = "server"
val TRUSTSTORE = File("etc/openssl/truststore.p12")
val PASSWORD = "123456".toCharArray()

val SERVER_KEY_STORE = KeyStore.getInstance(SERVER_KEYSTORE, PASSWORD)
val SERVER_KEY_MANAGER_FACTORY = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
    init(SERVER_KEY_STORE, PASSWORD)
}
val TRUST_KEY_STORE = KeyStore.getInstance(TRUSTSTORE, PASSWORD)
val TRUST_MANAGER_FACTORY = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
    init(TRUST_KEY_STORE)
}
