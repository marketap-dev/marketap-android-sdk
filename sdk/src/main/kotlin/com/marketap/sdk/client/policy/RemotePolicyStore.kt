package com.marketap.sdk.client.policy

import com.marketap.sdk.SdkMetadataProvider
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.utils.moshi
import com.marketap.sdk.utils.pairAdapter
import com.marketap.sdk.utils.stringAdapter
import com.squareup.moshi.JsonReader
import okio.Buffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object IgnoredPolicy

internal class RemotePolicyStore(
    private val storage: InternalStorage,
    private val now: () -> Long = System::currentTimeMillis,
    sdkType: String? = null,
    sdkVersion: String? = null,
    private val readOnly: Boolean = false,
) {
    companion object {
        val lock = Any()
        private val modes = mutableMapOf<String, Boolean>()
        private val generations = mutableMapOf<String, Long>()
        private val blocks = listOf("collection", "inApp", "pushTracking")
        private val isoTimestamp = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")
    }

    private class Loaded(val policy: Map<*, *>, val expiries: Map<String, Long>)

    private val jsonAdapter = moshi.adapter(Any::class.java)
    private val cache = mutableMapOf<String, Loaded>()
    private val nextRefresh = mutableMapOf<String, Long>()
    private val identity = SdkMetadataProvider.nativeLibraryVersion + ":" + sdkType.orEmpty() + ":" + sdkVersion.orEmpty()

    private fun key(projectId: String) = "marketap_remote_policy.$projectId"

    private fun load(projectId: String, discard: Boolean): Loaded {
        if (!readOnly) cache[projectId]?.let { return it }
        val loaded = read(projectId, discard)
        if (!readOnly) cache[projectId] = loaded
        return loaded
    }

    private fun read(projectId: String, discard: Boolean): Loaded {
        val policy = runCatching {
            storage.getItem(key(projectId), stringAdapter)?.let { jsonAdapter.fromJson(it) as? Map<*, *> }
        }.getOrNull() ?: return Loaded(emptyMap<Any, Any>(), emptyMap())
        if (policy["identity"] == identity) return Loaded(policy, expiries(policy))
        if (discard) storage.removeItem(key(projectId))
        return Loaded(emptyMap<Any, Any>(), emptyMap())
    }

    private fun saved(projectId: String, discard: Boolean = !readOnly): Map<*, *> = load(projectId, discard).policy

    private fun expiries(policy: Map<*, *>): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        blocks.forEach { block ->
            val millis = parseTimestamp((policy[block] as? Map<*, *>)?.get("expiresAt"))
            if (millis != null) result[block] = millis
        }
        return result
    }

    fun refreshDue(projectId: String): Boolean = synchronized(lock) {
        now() >= (nextRefresh[projectId] ?: 0L)
    }

    fun retryLater(projectId: String) = synchronized(lock) {
        nextRefresh[projectId] = now() + 60_000L
    }

    fun accept(projectId: String, payload: Any?) = synchronized(lock) {
        val old = saved(projectId)
        val interval = ((old["refreshAfterSec"] as? Number)?.toLong() ?: 300L).coerceIn(60, 3600)
        nextRefresh[projectId] = now() + interval * 1000
        if (payload == null) {
            blocks.forEach { allowed(projectId, it) }
            return@synchronized
        }
        if (payload === IgnoredPolicy) return@synchronized
        val raw = jsonAdapter.toJson(payload)
        if (raw.toByteArray(Charsets.UTF_8).size > 16 * 1024) return@synchronized
        val policy = payload as? Map<*, *> ?: return@synchronized
        if ((policy["version"] as? Number)?.toDouble() != 1.0) return@synchronized
        val revision = integer(policy["revision"]) ?: return@synchronized
        if (revision <= (integer(old["revision"]) ?: -1L)) return@synchronized
        val updated = mutableMapOf<String, Any>("version" to 1, "revision" to revision, "identity" to identity)
        val refresh = (integer(policy["refreshAfterSec"]) ?: 300L).coerceIn(60, 3600)
        updated["refreshAfterSec"] = refresh
        blocks.forEach { block ->
            val value = policy[block] as? Map<*, *>
            if (value != null && value["mode"] in listOf("allow", "stop")) {
                val normalized = mutableMapOf<String, Any>("mode" to value["mode"]!!)
                (value["expiresAt"] as? String)?.let { normalized["expiresAt"] = it }
                updated[block] = normalized
            } else if (policy.containsKey(block)) {
                old[block]?.let { updated[block] = it }
            }
        }
        storage.setItem(key(projectId), jsonAdapter.toJson(updated), stringAdapter)
        if (!readOnly) cache[projectId] = Loaded(updated, expiries(updated))
        nextRefresh[projectId] = now() + refresh * 1000
        blocks.forEach { allowed(projectId, it) }
    }

    private fun integer(value: Any?): Long? {
        val number = value as? Number ?: return null
        val double = number.toDouble()
        return number.toLong().takeIf { double.isFinite() && it.toDouble() == double }
    }

    private fun parseTimestamp(value: Any?): Long? {
        val original = value as? String ?: return null
        val text = if (original.endsWith("+00:00")) original.removeSuffix("+00:00") + "Z" else original
        if (!text.matches(isoTimestamp)) return null
        val format = if ('.' in text) "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" else "yyyy-MM-dd'T'HH:mm:ss'Z'"
        val normalized = if ('.' in text) text.substringBefore('.') + "." + text.substringAfter('.').removeSuffix("Z").take(3).padEnd(3, '0') + "Z" else text
        return runCatching {
            SimpleDateFormat(format, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(normalized)!!.time
        }.getOrNull()
    }

    private fun allows(loaded: Loaded, block: String): Boolean {
        val value = loaded.policy[block] as? Map<*, *>
        if (value?.get("mode") != "stop") return true
        val expiry = loaded.expiries[block] ?: return false
        return expiry <= now()
    }

    fun currentlyAllowed(projectId: String, block: String): Boolean = allows(load(projectId, false), block)

    fun allowed(projectId: String, block: String): Boolean = synchronized(lock) {
        val allow = allows(load(projectId, !readOnly), block)
        val stateKey = "$projectId:$block"
        val previous = modes.put(stateKey, allow) ?: true
        if (previous && !allow) {
            generations[stateKey] = (generations[stateKey] ?: 0L) + 1
            if (block == "collection") clearCollection(projectId)
            if (block == "inApp") (storage as? SharedPreferenceInternalStorage)?.clearCampaignCache()
        }
        allow
    }

    fun generation(projectId: String, block: String): Long = synchronized(lock) {
        allowed(projectId, block)
        generations["$projectId:$block"] ?: 0L
    }

    fun clearIfChanged(projectId: String, block: String, generation: Long) = synchronized(lock) {
        if (block == "collection" && generation(projectId, block) != generation) clearCollection(projectId)
    }

    private fun clearCollection(projectId: String) {
        val adapter = pairAdapter<String, IngestEventRequest>()
        if (storage is SharedPreferenceInternalStorage) {
            storage.clearCollectionEvents(projectId)
        } else {
            val events = storage.popItems("events", adapter, Int.MAX_VALUE)
            events.filter { it.first != projectId }.forEach { storage.queueItem("events", it, adapter) }
        }
        val profile = runCatching { storage.getItem("pending_user_profile", pairAdapter<String, UpdateProfileRequest>()) }
        if (profile.isFailure || profile.getOrNull()?.first == projectId) storage.removeItem("pending_user_profile")
    }
}

internal fun readSdkPolicy(body: String): Any? = try {
    val reader = JsonReader.of(Buffer().writeUtf8(body))
    var policy: Any? = null
    reader.beginObject()
    while (reader.hasNext()) {
        if (reader.nextName() != "data" || reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            continue
        }
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "sdkPolicy") {
                reader.skipValue()
                continue
            }
            val raw = reader.nextSource().use { it.readUtf8() }
            policy = if (raw.toByteArray(Charsets.UTF_8).size > 16 * 1024) IgnoredPolicy else moshi.adapter(Any::class.java).fromJson(raw)
            break
        }
        break
    }
    policy
} catch (e: Exception) {
    null
}
