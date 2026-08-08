package chain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.interstellargames.darkgalaxy.core.chain.Address
import com.interstellargames.darkgalaxy.core.chain.ChainConfig
import com.interstellargames.darkgalaxy.core.chain.Ix
import com.interstellargames.darkgalaxy.core.chain.describeStatusError
import kotlinx.coroutines.await
import kotlinx.coroutines.delay

/**
 * The web player's always-on ER connection — the walletless-identity analogue of the Android
 * ErSession + LocalWallet pair.
 *
 * Identity is a random 32-byte seed persisted in localStorage. Like Android's walletless path it
 * holds no funds (joining and playing are gasless on the ER), so localStorage's security posture
 * is acceptable — it must never seed a key that guards value. The seed doubles as the ER session
 * key: it signs the auth challenge for a token, then every in-game action, no prompts.
 */
object ErSession {

    private const val SEED_KEY = "dg_seed_hex"           // walletless identity — never destroyed
    private const val WALLET_SEED_KEY = "dg_wallet_seed" // wallet-derived — re-derivable by signing
    private const val WALLET_ADDR_KEY = "dg_wallet_addr"
    private const val MODE_KEY = "dg_mode"               // "" = no choice yet → landing
    private const val CONFIRM_TIMEOUT_MS = 8_000L

    private var seedHex: String? = null

    var address: String? by mutableStateOf(null)
        private set
    var walletAddress: String? by mutableStateOf(Bridge.localGet(WALLET_ADDR_KEY).ifEmpty { null })
        private set
    var authToken: String? by mutableStateOf(null)
        private set
    var authError: String? by mutableStateOf(null)
        private set

    val publicKey: Address? get() = address?.let { Address(it) }
    val isReady: Boolean get() = address != null && authToken != null
    /** True once the player has chosen how to play — false shows the landing. */
    val hasIdentity: Boolean get() = Bridge.localGet(MODE_KEY).isNotEmpty()
    val walletSupported: Boolean get() = Bridge.walletAvailable()

    fun erUrl(): String = authToken?.let { "${ChainConfig.ER_RPC}?token=$it" } ?: ChainConfig.ER_RPC
    fun erWs(): String = authToken?.let { "${ChainConfig.ER_WS}?token=$it" } ?: ChainConfig.ER_WS

    /** Resume the previously chosen identity. No-op when none exists (landing shows). */
    suspend fun ensure() {
        if (isReady || !hasIdentity) return
        when (Bridge.localGet(MODE_KEY)) {
            "wallet" -> {
                val stored = Bridge.localGet(WALLET_SEED_KEY)
                if (stored.isNotEmpty()) authWith(stored) else connectWallet()
            }
            else -> enterWalletless()
        }
    }

    /** The walletless path: a local random seed IS the account (kept across logouts). */
    suspend fun enterWalletless() {
        val seed = Bridge.localGet(SEED_KEY).ifEmpty {
            Bridge.randomSeedHex().also { Bridge.localSet(SEED_KEY, it) }
        }
        Bridge.localSet(MODE_KEY, "walletless")
        walletAddress = null
        authWith(seed)
    }

    /** The wallet path: sign the session message once; SHA-256 of the signature is the seed —
     *  the same derivation as Android, so one wallet is one session identity everywhere. */
    suspend fun connectWallet() {
        val combined = Bridge.walletSession(ChainConfig.SESSION_KEY_MESSAGE).await<JsString>().toString()
        val addr = combined.substringBefore('|')
        val seed = combined.substringAfter('|')
        Bridge.localSet(MODE_KEY, "wallet")
        Bridge.localSet(WALLET_SEED_KEY, seed)
        Bridge.localSet(WALLET_ADDR_KEY, addr)
        walletAddress = addr
        authWith(seed)
    }

    /** Drop the identity CHOICE — the walletless seed survives (it IS that account); the
     *  wallet-derived seed is discarded (re-derivable by signing again). */
    fun logout() {
        Bridge.localSet(MODE_KEY, "")
        Bridge.localSet(WALLET_SEED_KEY, "")
        Bridge.localSet(WALLET_ADDR_KEY, "")
        seedHex = null
        address = null
        walletAddress = null
        authToken = null
        authError = null
    }

    private suspend fun authWith(seed: String) {
        seedHex = seed
        address = Bridge.pubkeyFromSeed(seed)
        try {
            authToken = Bridge.erAuth(ChainConfig.ER_RPC, seed).await<JsString>().toString()
            authError = null
        } catch (e: Throwable) {
            authError = e.message ?: "ER auth failed"
            throw e
        }
    }

    /** Sign with the session key and submit to the ER, then wait for confirmation. The session
     *  key is the fee payer (gasless). Throws with a described program error if the tx failed. */
    suspend fun sendTransaction(instructions: List<Ix>): String {
        val seed = seedHex ?: error("ER session not initialized")
        if (authToken == null) error("ER session not initialized")
        val url = erUrl()
        val blockhash = Rpc.getLatestBlockhash(url)
        val txB64 = Bridge.signTx(seed, blockhash, instructions.toJson())
        val sig = Rpc.sendRaw(url, txB64)
        confirm(url, sig)
        return sig
    }

    suspend fun sendTransaction(instruction: Ix): String = sendTransaction(listOf(instruction))

    /** With preflight skipped, on-chain failures only surface in the signature status. */
    private suspend fun confirm(rpcUrl: String, signature: String) {
        val deadline = Bridge.nowSec() * 1000 + CONFIRM_TIMEOUT_MS
        while (Bridge.nowSec() * 1000 < deadline) {
            val status = try { Rpc.getSignatureStatus(rpcUrl, signature) } catch (_: Throwable) { "" }
            if (status.startsWith("err:")) error(describeStatusError(status.removePrefix("err:")))
            if (status == "confirmed" || status == "finalized") return
            delay(150)
        }
        error("Transaction was not confirmed in time — check your connection and retry")
    }
}

/** Serialize instructions for the bridge: [{p, d(hex), k:[{a, s, w}]}]. All values are base58 /
 *  hex, so plain string concatenation is safe — no escaping can ever be needed. */
private fun List<Ix>.toJson(): String = joinToString(",", prefix = "[", postfix = "]") { ix ->
    val keys = ix.accounts.joinToString(",") {
        """{"a":"${it.address.base58}","s":${if (it.signer) 1 else 0},"w":${if (it.writable) 1 else 0}}"""
    }
    """{"p":"${ix.programId.base58}","d":"${ix.data.toHex()}","k":[$keys]}"""
}

private fun ByteArray.toHex(): String {
    val hex = "0123456789abcdef"
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append(hex[v shr 4]).append(hex[v and 0x0F])
    }
    return sb.toString()
}
