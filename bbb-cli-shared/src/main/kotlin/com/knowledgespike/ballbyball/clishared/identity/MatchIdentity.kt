@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.knowledgespike.ballbyball.clishared.identity

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

sealed class IdentityError(val message: String) {
    class InvalidProviderId(value: String) : IdentityError("Provider ID must not be blank: '$value'")

    class InvalidUuid(value: String) : IdentityError("Value must be a valid UUID: '$value'")

    class InvalidSha256(value: String) : IdentityError("Value must be a lowercase SHA-256 digest: '$value'")
}

@Serializable
@JvmInline
value class ProviderId private constructor(val value: String) {
    companion object {
        context(raise: Raise<IdentityError.InvalidProviderId>)
        operator fun invoke(value: String): ProviderId {
            val normalized = value.trim().lowercase()
            if (normalized.isEmpty()) raise.raise(IdentityError.InvalidProviderId(value))
            return ProviderId(normalized)
        }

        fun of(value: String): Either<IdentityError.InvalidProviderId, ProviderId> = either { invoke(value) }

        fun from(value: String): ProviderId = requireNotNull(of(value).getOrNull()) {
            "Provider ID must not be blank"
        }
    }
}

@Serializable(with = SourceRecordIdSerializer::class)
@JvmInline
value class SourceRecordId private constructor(val value: UUID) {
    companion object {
        fun of(value: String): Either<IdentityError.InvalidUuid, SourceRecordId> = parseUuid(value, ::SourceRecordId)

        fun from(value: UUID): SourceRecordId = SourceRecordId(value)
    }
}

@Serializable(with = CanonicalMatchIdSerializer::class)
@JvmInline
value class CanonicalMatchId private constructor(val value: UUID) {
    companion object {
        fun of(value: String): Either<IdentityError.InvalidUuid, CanonicalMatchId> = parseUuid(value, ::CanonicalMatchId)

        fun from(value: UUID): CanonicalMatchId = CanonicalMatchId(value)
    }
}

@Serializable
@JvmInline
value class Sha256Digest private constructor(val value: String) {
    companion object {
        private val sha256Pattern = Regex("^[0-9a-f]{64}$")

        context(raise: Raise<IdentityError.InvalidSha256>)
        operator fun invoke(value: String): Sha256Digest {
            if (!sha256Pattern.matches(value)) raise.raise(IdentityError.InvalidSha256(value))
            return Sha256Digest(value)
        }

        fun of(value: String): Either<IdentityError.InvalidSha256, Sha256Digest> = either { invoke(value) }

        fun from(value: String): Sha256Digest = requireNotNull(of(value).getOrNull()) {
            "Value must be a lowercase SHA-256 digest"
        }
    }
}

object ProviderNamespaces {
    val CRICSHEET: UUID = UUID.fromString("f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b")
    internal val TEST_PROVIDER: UUID = UUID.fromString("ba6f14c2-6d85-5dde-bf22-f40ff595e62f")
}

object CanonicalNamespaces {
    val SINGLE_SOURCE: UUID = UUID.fromString("6b456afa-09d1-5cc1-a820-77617ad17ca2")
}

object PublicNamespaces {
    val MATCH_ID: UUID = UUID.fromString("0e4f5f84-3b4c-5f0b-a5c6-7d8e9f0a1b2c")
}

object DeterministicIdentity {
    fun sourceRecordId(namespace: UUID, providerRecordKey: String): SourceRecordId {
        require(providerRecordKey.isNotEmpty()) { "Provider record key must not be empty" }
        return SourceRecordId.from(uuidV5(namespace, providerRecordKey))
    }

    fun canonicalMatchId(namespace: UUID, stableKey: String): CanonicalMatchId {
        require(stableKey.isNotEmpty()) { "Canonical stable key must not be empty" }
        return CanonicalMatchId.from(uuidV5(namespace, stableKey))
    }

    /**
     * Derives the persisted ten-digit URL identifier from the canonical match identity.
     * Collisions are intentionally rejected by the warehouse unique constraint rather than
     * resolved by probing, so the result remains independent of insertion order.
     */
    fun publicMatchId(canonicalMatchId: CanonicalMatchId): PublicMatchId {
        val publicUuid = uuidV5(PublicNamespaces.MATCH_ID, canonicalMatchId.value.toString())
        val unsignedValue = java.lang.Long.remainderUnsigned(
            publicUuid.mostSignificantBits,
            PUBLIC_ID_RANGE
        )
        return PublicMatchId.from(PublicMatchId.MIN_PUBLIC_MATCH_ID + unsignedValue)
    }

    /**
     * Generates a UUID based on the version 5 (SHA-1 hash and namespace) standard.
     *
     * @param namespace The namespace UUID used as the base for generating the version 5 UUID.
     * @param stableKey The stable key (name) used as input to hash with the namespace.
     * @return A new UUID generated using the version 5 algorithm.
     */
    private fun uuidV5(namespace: UUID, stableKey: String): UUID {
        val namespaceBytes = ByteBuffer.allocate(UUID_BYTES)
            .putLong(namespace.mostSignificantBits)
            .putLong(namespace.leastSignificantBits)
            .array()
        val hash = MessageDigest.getInstance("SHA-1").digest(
            namespaceBytes + stableKey.toByteArray(StandardCharsets.UTF_8)
        )
        hash[6] = ((hash[6].toInt() and VERSION_MASK) or VERSION_FIVE).toByte()
        hash[8] = ((hash[8].toInt() and VARIANT_MASK) or IETF_VARIANT).toByte()
        val buffer = ByteBuffer.wrap(hash)
        return UUID(buffer.long, buffer.long)
    }

    fun rawContentDigest(rawBytes: ByteArray): Sha256Digest = Sha256Digest.from(
        MessageDigest.getInstance("SHA-256").digest(rawBytes).toHexString()
    )

    private const val UUID_BYTES = 16
    private const val VERSION_MASK = 0x0f
    private const val VERSION_FIVE = 0x50
    private const val VARIANT_MASK = 0x3f
    private const val IETF_VARIANT = 0x80
    private const val PUBLIC_ID_RANGE =
        PublicMatchId.MAX_PUBLIC_MATCH_ID - PublicMatchId.MIN_PUBLIC_MATCH_ID + 1
}

@Serializable
data class SourceReference(
    val provider: ProviderId,
    val providerRecordKey: String,
    val sourceRecordId: SourceRecordId,
    val rawContentDigest: Sha256Digest
)

@Serializable
data class SourceMatchEnvelope(
    @EncodeDefault val version: Int = CURRENT_ENVELOPE_VERSION,
    val source: SourceReference,
    val match: BbbMatchData
)

@Serializable
sealed interface MergeEvidence {
    @Serializable
    @SerialName("singleSource")
    data class SingleSource(val sourceRecordId: SourceRecordId) : MergeEvidence
}

@Serializable
data class CanonicalMatchEnvelope(
    @EncodeDefault val version: Int = CURRENT_ENVELOPE_VERSION,
    val canonicalMatchId: CanonicalMatchId,
    val sources: List<SourceReference>,
    val mergeEvidence: MergeEvidence,
    val match: BbbMatchData
)

object SingleSourceCanonicalizer {
    fun canonicalize(sourceEnvelope: SourceMatchEnvelope): CanonicalMatchEnvelope = CanonicalMatchEnvelope(
        canonicalMatchId = DeterministicIdentity.canonicalMatchId(
            namespace = CanonicalNamespaces.SINGLE_SOURCE,
            stableKey = sourceEnvelope.source.sourceRecordId.value.toString()
        ),
        sources = listOf(sourceEnvelope.source),
        mergeEvidence = MergeEvidence.SingleSource(sourceEnvelope.source.sourceRecordId),
        match = sourceEnvelope.match
    )
}

private const val CURRENT_ENVELOPE_VERSION = 1

private fun <T> parseUuid(value: String, factory: (UUID) -> T): Either<IdentityError.InvalidUuid, T> = either {
    val uuid = runCatching { UUID.fromString(value) }
        .getOrElse { raise(IdentityError.InvalidUuid(value)) }
    factory(uuid)
}

object SourceRecordIdSerializer : KSerializer<SourceRecordId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SourceRecordId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SourceRecordId) = encoder.encodeString(value.value.toString())

    override fun deserialize(decoder: Decoder): SourceRecordId = SourceRecordId.of(decoder.decodeString()).fold(
        ifLeft = { error -> throw IllegalArgumentException(error.message) },
        ifRight = { it }
    )
}

object CanonicalMatchIdSerializer : KSerializer<CanonicalMatchId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CanonicalMatchId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CanonicalMatchId) = encoder.encodeString(value.value.toString())

    override fun deserialize(decoder: Decoder): CanonicalMatchId = CanonicalMatchId.of(decoder.decodeString()).fold(
        ifLeft = { error -> throw IllegalArgumentException(error.message) },
        ifRight = { it }
    )
}