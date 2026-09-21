package com.axiom.koin

import com.axiom.Axiom
import com.axiom.api
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.module

/**
 * Binds the [Axiom] singleton into a Koin graph, so repositories take it through their constructor
 * like any other dependency and a test can hand them a standalone [Axiom.create] instance instead.
 *
 * Resolved lazily — the definition calls [Axiom.get] when first asked, not when the module is built —
 * because a module is usually a top-level `val` that a `Module.verify()` test constructs long before any
 * `Application.onCreate` runs.
 *
 * ```
 * startKoin { modules(axiomModule, dataModule) }
 * ```
 */
val axiomModule: Module =
    module {
        single<Axiom> { Axiom.get() }
    }

/**
 * Registers a Retrofit service from a named Axiom client as a Koin single.
 *
 * ```
 * val dataModule = module {
 *     axiomApi<CatalogueApi>(client = "catalogue")
 *     singleOf(::SkinRepositoryImpl) bind SkinRepository::class   // takes CatalogueApi
 * }
 * ```
 */
inline fun <reified S : Any> Module.axiomApi(
    client: String,
    qualifier: Qualifier? = null,
) {
    single(qualifier) { get<Axiom>().api<S>(client) }
}
