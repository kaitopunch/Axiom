package com.duylt.demo.axiom.data.record

/**
 * What the store holds — one JSON value per task key (axiom/README.md §2 step 2). Gson reads these back
 * reflectively, hence the `-keep` in proguard-rules.pro.
 */
data class ProductRecord(
    val id: Int,
    val title: String,
    val price: Double,
    val category: String,
    val brand: String?,
    val rating: Double,
    /** The remote thumbnail URL, as the API sent it. Always present. */
    val thumbUrl: String,
    /** Absolute path of the cached copy, or null until the transform has downloaded it (or if that failed). */
    val thumbLocal: String? = null,
)

/**
 * One value for the whole products payload, so the list and its metadata land in the store together.
 * Nothing time-stamped lives in here on purpose: Axiom fingerprints the stored value (SHA-256), and a
 * `fetchedAt` field would make every sync a "new" payload — the `Unchanged` result would never happen.
 */
data class ProductStore(
    val total: Int = 0,
    val products: List<ProductRecord> = emptyList(),
)

data class UserRecord(
    val id: Int,
    val fullName: String,
    val email: String,
    val age: Int,
    val imageUrl: String?,
    val company: String,
    val jobTitle: String,
)

data class CategoryRecord(
    val slug: String,
    val name: String,
)

/** What a probe task *would* store if its fetch ever got past the failing call. It never does. */
data class ProbeRecord(
    val reachedAt: Long,
)
