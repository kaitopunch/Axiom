package com.axiom.client

import com.axiom.log.AxiomLogger
import com.axiom.log.debug
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Builds and caches one [OkHttpClient] and one [Retrofit] per named client, lazily.
 *
 * Every client is a `newBuilder()` copy of one shared base, so they share the connection pool and the
 * dispatcher — the two expensive parts of an OkHttp client — and differ only in what a [ClientConfig]
 * can say: timeouts, headers, interceptors, logging.
 */
internal class ClientRegistry(
    private val gson: Gson,
    private val logger: AxiomLogger,
) {
    private val configs = ConcurrentHashMap<String, ClientConfig>()
    private val okHttpClients = ConcurrentHashMap<String, OkHttpClient>()
    private val retrofits = ConcurrentHashMap<String, Retrofit>()

    private val base: OkHttpClient by lazy { OkHttpClient() }

    fun register(config: ClientConfig) {
        configs[config.name] = config
        // Rebuilt lazily with the new config; a Retrofit service already created keeps the old client,
        // which is the documented cost of replacing a client at runtime.
        okHttpClients.remove(config.name)
        retrofits.remove(config.name)
    }

    fun config(name: String): ClientConfig =
        configs[name] ?: throw IllegalArgumentException(
            "No Axiom client named '$name'. Declare it with client(\"$name\") { … } in Axiom.init.",
        )

    fun callFactory(name: String): Call.Factory = okHttp(name)

    fun retrofit(name: String): Retrofit =
        retrofits.computeIfAbsent(name) {
            val config = config(name)
            val baseUrl =
                config.baseUrl?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(
                        "Axiom client '$name' has no baseUrl, so it cannot back a Retrofit service. " +
                            "Set baseUrl in client(\"$name\") { … } or use callFactory(\"$name\") for raw calls.",
                    )
            Retrofit
                .Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(okHttp(name))
                .addConverterFactory(config.converterFactory ?: GsonConverterFactory.create(gson))
                .build()
        }

    fun <S : Any> api(
        name: String,
        service: Class<S>,
    ): S = retrofit(name).create(service)

    private fun okHttp(name: String): OkHttpClient =
        okHttpClients.computeIfAbsent(name) {
            val config = config(name)
            base
                .newBuilder()
                .connectTimeout(config.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .writeTimeout(config.writeTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .apply {
                    // Headers first, so what the consumer's interceptors and the logger see is the
                    // request as it goes out.
                    if (config.staticHeaders.isNotEmpty() || config.headerProvider != null) {
                        addInterceptor(HeaderInterceptor(config.staticHeaders.toMap(), config.headerProvider))
                    }
                    config.interceptors.forEach { addInterceptor(it) }
                    if (config.logging != HttpLogging.NONE) {
                        addInterceptor(
                            HttpLoggingInterceptor { message -> logger.debug { "[$name] $message" } }
                                .apply {
                                    level =
                                        when (config.logging) {
                                            HttpLogging.NONE -> HttpLoggingInterceptor.Level.NONE
                                            HttpLogging.BASIC -> HttpLoggingInterceptor.Level.BASIC
                                            HttpLogging.HEADERS -> HttpLoggingInterceptor.Level.HEADERS
                                            HttpLogging.BODY -> HttpLoggingInterceptor.Level.BODY
                                        }
                                    config.redactedHeaders.forEach { redactHeader(it) }
                                },
                        )
                    }
                    config.networkInterceptors.forEach { addNetworkInterceptor(it) }
                }.build()
        }
}

/**
 * Attaches a client's headers to every request. Static ones first, then whatever the provider returns
 * for this request — so a rotating token overrides a fixed default of the same name.
 */
internal class HeaderInterceptor(
    private val static: Map<String, String>,
    private val provider: (() -> Map<String, String>)?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        static.forEach { (name, value) -> builder.header(name, value) }
        provider?.invoke()?.forEach { (name, value) -> builder.header(name, value) }
        return chain.proceed(builder.build())
    }
}
