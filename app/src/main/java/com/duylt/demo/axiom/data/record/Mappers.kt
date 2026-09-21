package com.duylt.demo.axiom.data.record

import com.duylt.demo.axiom.data.remote.model.CategoryResponse
import com.duylt.demo.axiom.data.remote.model.ProductResponse
import com.duylt.demo.axiom.data.remote.model.UserResponse

/** Null when the row cannot be shown at all (no title or no picture); the task drops and counts those. */
fun ProductResponse.toRecordOrNull(): ProductRecord? {
    val title = title?.takeIf { it.isNotBlank() } ?: return null
    val thumb = thumbnail?.takeIf { it.isNotBlank() } ?: return null
    return ProductRecord(
        id = id,
        title = title,
        price = price ?: 0.0,
        category = category.orEmpty(),
        brand = brand?.takeIf { it.isNotBlank() },
        rating = rating ?: 0.0,
        thumbUrl = thumb,
    )
}

fun UserResponse.toRecord(): UserRecord =
    UserRecord(
        id = id,
        fullName = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { "User #$id" },
        email = email.orEmpty(),
        age = age ?: 0,
        imageUrl = image?.takeIf { it.isNotBlank() },
        company = company?.name.orEmpty(),
        jobTitle = company?.title.orEmpty(),
    )

fun CategoryResponse.toRecord(): CategoryRecord = CategoryRecord(slug = slug, name = name ?: slug)
