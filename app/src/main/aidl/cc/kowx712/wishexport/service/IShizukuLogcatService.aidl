package cc.kowx712.wishexport.service;

import android.os.ParcelFileDescriptor;

interface IShizukuLogcatService {
    /**
     * Execute logcat command and return output.
     */
    String executeLogcat(in String[] command);

    /**
     * Clear logcat buffer.
     */
    void clearLogcat();

    ParcelFileDescriptor startLogcat(in String[] command);

    void stopLogcat();
}
