package com.duylt.demo.axiom.data.remote.model

import com.google.gson.annotations.SerializedName

/** Wire shapes, exactly as dummyjson.com sends them. Kept apart from the records the store holds (§2 step 2). */
data class ProductsPage(
    @SerializedName("products") val products: List<ProductResponse> = emptyList(),
    @SerializedName("total") val total: Int = 0,
)

data class ProductResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("price") val price: Double?,
    @SerializedName("category") val category: String?,
    @SerializedName("brand") val brand: String?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("thumbnail") val thumbnail: String?,
)

data class UsersPage(
    @SerializedName("users") val users: List<UserResponse> = emptyList(),
    @SerializedName("total") val total: Int = 0,
)

data class UserResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("firstName") val firstName: String?,
    @SerializedName("lastName") val lastName: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("age") val age: Int?,
    @SerializedName("image") val image: String?,
    @SerializedName("company") val company: CompanyResponse?,
)

data class CompanyResponse(
    @SerializedName("name") val name: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("department") val department: String?,
)

data class CategoryResponse(
    @SerializedName("slug") val slug: String,
    @SerializedName("name") val name: String?,
    @SerializedName("url") val url: String?,
)
