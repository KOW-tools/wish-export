package cc.kowx712.wishexport.service;

oneway interface IShizukuLogcatCallback {
    void onUrlFound(String url);
    void onCaptureError(String message);
    void onCaptureFinished();
}
