package com.demo.constants

import java.io.File
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

object NET {
    const val HOST = "localhost"
    const val PORT = 8443
}

object QUIC {
    const val PROTOCOL = "echo"
}

object TLS {
    const val SERVER_ALIAS = "server"
    const val CLIENT_ALIAS = "client"
    const val PASSWORD = "123456"
    val PASSWORD_CHARS = PASSWORD.toCharArray()

    val SERVER_KEYSTORE_FILE = safeFile("etc/openssl/server-keystore.p12")
    val CLIENT_KEYSTORE_FILE = safeFile("etc/openssl/client-keystore.p12")
    val SERVER_TRUSTSTORE_FILE = safeFile("etc/openssl/truststore.p12")
    val CLIENT_TRUSTSTORE_FILE = safeFile("etc/openssl/truststore.p12")

    val SERVER_KEYSTORE = KeyStore.getInstance(SERVER_KEYSTORE_FILE, PASSWORD_CHARS)
    val SERVER_KEYMANAGERFACTORY = SERVER_KEYSTORE.asKeyManagerFactory()
    val SERVER_TRUSTSTORE = KeyStore.getInstance(SERVER_TRUSTSTORE_FILE, PASSWORD_CHARS)
    val SERVER_TRUSTMANAGERFACTORY = SERVER_TRUSTSTORE.asTrustManagerFactory()

    val CLIENT_KEYSTORE = KeyStore.getInstance(CLIENT_KEYSTORE_FILE, PASSWORD_CHARS)
    val CLIENT_KEYMANAGERFACTORY = CLIENT_KEYSTORE.asKeyManagerFactory()
    val CLIENT_TRUSTSTORE = KeyStore.getInstance(CLIENT_TRUSTSTORE_FILE, PASSWORD_CHARS)
    val CLIENT_TRUSTMANAGERFACTORY = CLIENT_TRUSTSTORE.asTrustManagerFactory()

    private fun safeFile(path: String) = File(path).let {
        val res = if (it.exists()) it else File("../$path")
        check(res.exists()) { "$path does not exist!" }
        res
    }

    private fun KeyStore.asKeyManagerFactory(): KeyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(this@asKeyManagerFactory, PASSWORD_CHARS)
        }

    private fun KeyStore.asTrustManagerFactory(): TrustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(this@asTrustManagerFactory)
        }
}

object TLS2 {
    val SERVER_KEYSTORE_FILE = safeFile("etc/keytool/server-keystore.p12")
    val CLIENT_KEYSTORE_FILE = safeFile("etc/keytool/client-keystore.p12")
    val SERVER_TRUSTSTORE_FILE = safeFile("etc/keytool/server-truststore.p12")
    val CLIENT_TRUSTSTORE_FILE = safeFile("etc/keytool/client-truststore.p12")

    val SERVER_KEYSTORE = KeyStore.getInstance(SERVER_KEYSTORE_FILE, TLS.PASSWORD_CHARS)
    val SERVER_KEYMANAGERFACTORY = SERVER_KEYSTORE.asKeyManagerFactory()
    val SERVER_TRUSTSTORE = KeyStore.getInstance(SERVER_TRUSTSTORE_FILE, TLS.PASSWORD_CHARS)
    val SERVER_TRUSTMANAGERFACTORY = SERVER_TRUSTSTORE.asTrustManagerFactory()

    val CLIENT_KEYSTORE = KeyStore.getInstance(CLIENT_KEYSTORE_FILE, TLS.PASSWORD_CHARS)
    val CLIENT_KEYMANAGERFACTORY = CLIENT_KEYSTORE.asKeyManagerFactory()
    val CLIENT_TRUSTSTORE = KeyStore.getInstance(CLIENT_TRUSTSTORE_FILE, TLS.PASSWORD_CHARS)
    val CLIENT_TRUSTMANAGERFACTORY = CLIENT_TRUSTSTORE.asTrustManagerFactory()

    private fun safeFile(path: String) = File(path).let {
        val res = if (it.exists()) it else File("../$path")
        check(res.exists()) { "$path does not exist!" }
        res
    }

    private fun KeyStore.asKeyManagerFactory(): KeyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(this@asKeyManagerFactory, TLS.PASSWORD_CHARS)
        }

    private fun KeyStore.asTrustManagerFactory(): TrustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(this@asTrustManagerFactory)
        }
}
