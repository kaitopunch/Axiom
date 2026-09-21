package com.duylt.demo.axiom.di

import androidx.work.WorkManager
import com.axiom.koin.axiomApi
import com.axiom.koin.axiomModule
import com.duylt.demo.axiom.data.DemoClients
import com.duylt.demo.axiom.data.remote.DummyJsonApi
import com.duylt.demo.axiom.data.repository.ProductRepository
import com.duylt.demo.axiom.data.repository.UserRepository
import com.duylt.demo.axiom.log.AxiomLogRecorder
import com.duylt.demo.axiom.ui.lab.LabViewModel
import com.duylt.demo.axiom.ui.products.ProductsViewModel
import com.duylt.demo.axiom.ui.users.UsersViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The Koin graph (axiom/README.md §3.3). `axiomModule` binds the `Axiom` singleton lazily — it calls
 * `Axiom.get()` when first asked, so this module can be built (and verified in a test) before
 * `Application.onCreate` has run `Axiom.init`. `axiomApi<DummyJsonApi>` registers the Retrofit service
 * of the named client as a single, for the one place outside a task that calls the API directly (the
 * Lab screen's "call API through Koin" card).
 */
val appModule =
    module {
        includes(axiomModule)
        axiomApi<DummyJsonApi>(client = DemoClients.API)

        single { WorkManager.getInstance(androidContext()) }
        single { AxiomLogRecorder }

        singleOf(::ProductRepository)
        singleOf(::UserRepository)

        viewModelOf(::ProductsViewModel)
        viewModelOf(::UsersViewModel)
        viewModelOf(::LabViewModel)
    }
