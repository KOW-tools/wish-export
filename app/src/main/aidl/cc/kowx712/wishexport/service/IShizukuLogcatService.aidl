package cc.kowx712.wishexport.service;

import cc.kowx712.wishexport.service.IShizukuLogcatCallback;

interface IShizukuLogcatService {
    void startCapture(IShizukuLogcatCallback callback);
    void stopCapture();
}
