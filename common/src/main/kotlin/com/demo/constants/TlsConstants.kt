package com.demo.constants

import java.io.File
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

val SERVER_KEYSTORE = File("etc/openssl/server-keystore.p12").let {
    if (it.exists()) it else File("../etc/openssl/server-keystore.p12")
}
const val SERVER_ALIAS = "server"
val CLIENT_KEYSTORE = File("etc/openssl/client-keystore.p12").let {
    if (it.exists()) it else File("../etc/openssl/client-keystore.p12")
}
const val CLIENT_ALIAS = "client"
val TRUSTSTORE = File("etc/openssl/truststore.p12").let {
    if (it.exists()) it else File("../etc/openssl/truststore.p12")
}
val PASSWORD = "123456"

val SERVER_KEY_STORE = KeyStore.getInstance(SERVER_KEYSTORE, PASSWORD.toCharArray())
val SERVER_KEY_MANAGER_FACTORY = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
    init(SERVER_KEY_STORE, PASSWORD.toCharArray())
}

val CLIENT_KEY_STORE = KeyStore.getInstance(CLIENT_KEYSTORE, PASSWORD.toCharArray())
val CLIENT_KEY_MANAGER_FACTORY = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
    init(CLIENT_KEY_STORE, PASSWORD.toCharArray())
}

val TRUST_KEY_STORE = KeyStore.getInstance(TRUSTSTORE, PASSWORD.toCharArray())
val TRUST_MANAGER_FACTORY = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
    init(TRUST_KEY_STORE)
}
