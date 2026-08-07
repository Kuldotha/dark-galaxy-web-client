package chain

import com.interstellargames.darkgalaxy.core.chain.Address
import com.interstellargames.darkgalaxy.core.chain.ChainConfig
import com.interstellargames.darkgalaxy.core.chain.Ix
import com.interstellargames.darkgalaxy.core.game.ChainGateway

/** The browser's side of the seam: fetch RPC + localStorage identity + tweetnacl signing,
 *  all via dg-bridge.js. The shared systems in :core only ever see this interface. */
object WebChainGateway : ChainGateway {
    override val sessionAddress: String? get() = if (ErSession.isReady) ErSession.address else null

    override fun l1Url(): String = ChainConfig.L1_RPC
    override fun erUrl(): String = ErSession.erUrl()

    override suspend fun accountData(url: String, account: Address): ByteArray? =
        Rpc.getAccountData(url, account)

    override suspend fun multiAccountData(url: String, accounts: List<Address>): List<ByteArray?> =
        Rpc.getMultipleAccountsData(url, accounts)

    override suspend fun sendSessionTx(instructions: List<Ix>): String =
        ErSession.sendTransaction(instructions)

    override fun nowSec(): Long = Bridge.nowSec()
}
