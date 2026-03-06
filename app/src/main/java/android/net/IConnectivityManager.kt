package android.net

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.RemoteException

interface IConnectivityManager : IInterface {

    @Throws(RemoteException::class)
    fun setFirewallChainEnabled(chain: Int, enable: Boolean)

    @Throws(RemoteException::class)
    fun setUidFirewallRule(chain: Int, uid: Int, rule: Int)

    @Throws(RemoteException::class)
    fun getUidFirewallRule(chain: Int, uid: Int): Int

    abstract class Stub : Binder(), IConnectivityManager {
        companion object {
            fun asInterface(obj: IBinder?): IConnectivityManager {
                throw UnsupportedOperationException()
            }
        }
    }
}
