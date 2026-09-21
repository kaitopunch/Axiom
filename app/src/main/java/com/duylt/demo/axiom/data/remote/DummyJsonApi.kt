package com.duylt.demo.axiom.data.remote

import com.duylt.demo.axiom.data.remote.model.CategoryResponse
import com.duylt.demo.axiom.data.remote.model.ProductsPage
import com.duylt.demo.axiom.data.remote.model.UsersPage
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Retrofit interface Axiom builds for the `dummyjson` client. Ordinary Retrofit, `suspend` functions
 * (axiom/README.md §2 step 1). dummyjson.com wraps list payloads as `{ products: [...], total, skip, limit }`
 * rather than `{ status, message, data }`, so the SDK's optional [com.axiom.envelope.ApiEnvelope] is not
 * used here — `fetch` returns whatever the interface returns.
 */
interface DummyJsonApi {
    /** `limit = 0` returns every product; `select` trims the payload to the fields the record keeps. */
    @GET("products")
    suspend fun getProducts(
        @Query("limit") limit: Int = 0,
        @Query("select") select: String = "id,title,price,category,brand,rating,thumbnail",
    ): ProductsPage

    @GET("users")
    suspend fun getUsers(
        @Query("limit") limit: Int = 0,
        @Query("select") select: String = "id,firstName,lastName,email,age,image,company",
    ): UsersPage

    @GET("products/categories")
    suspend fun getCategories(): List<CategoryResponse>

    /**
     * dummyjson's error simulator: answers with the given HTTP status. The Lab screen's failing tasks
     * point here so a viewer can watch Axiom classify and retry (or not) a real non-2xx response.
     */
    @GET("http/{code}/{message}")
    suspend fun fail(
        @Path("code") code: Int,
        @Path("message") message: String,
    ): Map<String, Any?>
}
