package cc.kowx712.wishexport.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder

/**
 * Shizuku user service connection for logcat operations.
 * Using AIDL for proper Shizuku integration.
 */
class ShizukuServiceConnection : ServiceConnection {

    @Volatile
    private var binder: IShizukuLogcatService? = null

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        binder = if (service != null && service.pingBinder()) {
            IShizukuLogcatService.Stub.asInterface(service)
        } else {
            null
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        binder = null
    }

    fun getBinder(): IShizukuLogcatService? = binder

    fun isConnected(): Boolean = binder != null && binder?.asBinder()?.pingBinder() == true
}
