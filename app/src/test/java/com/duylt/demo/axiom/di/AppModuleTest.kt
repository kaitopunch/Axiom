package com.duylt.demo.axiom.di

import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

/**
 * Every definition in [appModule] can be resolved from the graph. Runs before any `Axiom.init`,
 * which is exactly why `axiomModule` binds `Axiom.get()` lazily (axiom/README.md §3.3).
 */
@OptIn(KoinExperimentalAPI::class)
class AppModuleTest {
    @Test
    fun `app module resolves`() {
        appModule.verify()
    }
}
