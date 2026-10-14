package com.vuravision.classroom.room

import android.util.Base64
import com.vuravision.classroom.ui.label
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption for shared rooms (item 84). Each guest agrees a fresh key with the host when joining (ECDH on P-256),
 * then every request and answer is sealed with AES-GCM, so the lesson, pictures and credentials never cross the
 * classroom network in the clear. Both screens show the same 4-digit code derived from the key: the teacher compares
 * it before approving, which rules out a device in the middle. Requests carry a timestamp, so a recorded request
 * cannot be replayed later.
 */
object RoomCrypto {
    private val random = SecureRandom()
    fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()
    fun publicKey(pair: KeyPair): String = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)

    /** The session key: SHA-256 of the shared secret, bound to this room's invitation. */
    fun agree(own: KeyPair, other: String, invite: String): ByteArray {
        val bytes = Base64.decode(other, Base64.NO_WRAP)
        require(bytes.size in 60..200) { "invalid_key" }
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
        val secret = KeyAgreement.getInstance("ECDH").apply {
            init(own.private)
            doPhase(peer, true)
        }.generateSecret()
        return MessageDigest.getInstance("SHA-256").run {
            update(secret)
            update(invite.toByteArray())
            update("vura-room-1".toByteArray())
            digest()
        }
    }

    /** Four digits both devices show; equal codes mean both hold the same key. */
    fun code(key: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").run {
            update("vura-room-code".toByteArray())
            update(key)
            digest()
        }
        val n = ((d[0].toInt() and 0xff) shl 24 or ((d[1].toInt() and 0xff) shl 16) or ((d[2].toInt() and 0xff) shl 8) or (d[3].toInt() and 0xff)).toLong() and 0xffffffffL
        return "%04d".format(n % 10000)
    }

    /** IV (12 bytes) + ciphertext + tag. [label] ties the message to its endpoint, so it cannot be reused elsewhere. */
    fun seal(key: ByteArray, plain: ByteArray, label: String): ByteArray {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(label.toByteArray())
        }
        return iv + c.doFinal(plain)
    }
    fun open(key: ByteArray, sealed: ByteArray, label: String): ByteArray {
        require(sealed.size >= 28) { "invalid_message" }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed, 0, 12))
            updateAAD(label.toByteArray())
        }
        return c.doFinal(sealed, 12, sealed.size - 12)
    }
    fun text(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun bytes(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
    fun hex(key: ByteArray) = key.joinToString("") { "%02x".format(it) }
    fun unhex(text: String): ByteArray {
        require(text.matches(Regex("[a-f0-9]{64}")))
        return ByteArray(32) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

/** What a sealed request carries besides its payload: who, proven by the credential, and when. */
data class RoomEnvelope(val t: Long = 0, val c: String = "", val p: String = "")
data class RoomSealedWelcome(val key: String = "", val data: String = "")
