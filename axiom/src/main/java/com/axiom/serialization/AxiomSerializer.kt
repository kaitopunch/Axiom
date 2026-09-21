package com.axiom.serialization

import com.google.gson.Gson
import java.lang.reflect.Type

/**
 * How a task's payload is turned into the JSON the store holds, and back.
 *
 * The [Type] is the full generic type the task was declared with (`List<Skin>`, not `List`), which is
 * what lets a generic payload round-trip. Gson is the default; a consumer on another library implements
 * these two functions and sets it on [com.axiom.AxiomConfig.serializer].
 */
interface AxiomSerializer {
    fun toJson(
        value: Any,
        type: Type,
    ): String

    fun <T> fromJson(
        json: String,
        type: Type,
    ): T
}

class GsonSerializer(
    private val gson: Gson,
) : AxiomSerializer {
    override fun toJson(
        value: Any,
        type: Type,
    ): String = gson.toJson(value, type)

    override fun <T> fromJson(
        json: String,
        type: Type,
    ): T = gson.fromJson(json, type)
}
