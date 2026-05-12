package org.openshelf.reader.credentials

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openshelf.reader.source.api.SourceId

internal class AndroidKeystoreApiKeyStore(
    context: Context,
) : SecureApiKeyStore {
    private val rootDirectory = File(context.filesDir, SecureDirectoryName)

    override suspend fun load(sourceId: SourceId): String? = withContext(Dispatchers.IO) {
        val file = fileFor(sourceId)
        if (!file.isFile) return@withContext null

        runCatching {
            val encrypted = EncryptedApiKeyBlob.parse(file.readText())
            val cipher = Cipher.getInstance(CipherTransformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GcmTagBits, encrypted.iv),
            )
            cipher.doFinal(encrypted.ciphertext).toString(Charsets.UTF_8)
        }.getOrElse { error ->
            throw SecureApiKeyStoreException("Could not read saved API key.", error)
        }
    }

    override suspend fun save(
        sourceId: SourceId,
        apiKey: String,
    ) {
        require(apiKey.isNotBlank()) { "API key must not be blank." }

        withContext(Dispatchers.IO) {
            if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
                throw SecureApiKeyStoreException("Could not create secure key storage.")
            }

            runCatching {
                val cipher = Cipher.getInstance(CipherTransformation)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
                val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
                val blob = EncryptedApiKeyBlob(
                    iv = cipher.iv,
                    ciphertext = ciphertext,
                )
                val target = fileFor(sourceId)
                val temporary = File(target.parentFile, "${target.name}.tmp")
                temporary.writeText(blob.serialize())
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse { error ->
                throw SecureApiKeyStoreException("Could not save API key.", error)
            }
        }
    }

    override suspend fun delete(sourceId: SourceId) {
        withContext(Dispatchers.IO) {
            fileFor(sourceId).delete()
        }
    }

    private fun fileFor(sourceId: SourceId): File {
        val safeName = sourceId.value.map { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_') {
                character
            } else {
                '_'
            }
        }.joinToString("").ifBlank { "source" }

        return File(rootDirectory, "$safeName.key")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        val existing = keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            AndroidKeyStore,
        )
        val spec = KeyGenParameterSpec.Builder(
            KeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AesKeySizeBits)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private data class EncryptedApiKeyBlob(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) {
        fun serialize(): String =
            listOf(
                BlobVersion,
                Encoder.encodeToString(iv),
                Encoder.encodeToString(ciphertext),
            ).joinToString("\n")

        companion object {
            fun parse(value: String): EncryptedApiKeyBlob {
                val lines = value.lines().filter { it.isNotBlank() }
                require(lines.size == 3 && lines[0] == BlobVersion) { "Unsupported encrypted key blob." }
                return EncryptedApiKeyBlob(
                    iv = Decoder.decode(lines[1]),
                    ciphertext = Decoder.decode(lines[2]),
                )
            }
        }
    }

    private companion object {
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "openshelf-reader-kavita-api-key"
        const val CipherTransformation = "AES/GCM/NoPadding"
        const val GcmTagBits = 128
        const val AesKeySizeBits = 256
        const val SecureDirectoryName = "secure-api-keys"
        const val BlobVersion = "v1"
        val Encoder: Base64.Encoder = Base64.getEncoder()
        val Decoder: Base64.Decoder = Base64.getDecoder()
    }
}
