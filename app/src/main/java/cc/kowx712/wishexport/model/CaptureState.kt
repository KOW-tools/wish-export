package cc.kowx712.wishexport.model

/**
 * Represents the state of the capture operation.
 */
sealed class CaptureState {
    data object Idle : CaptureState()
    data object Capturing : CaptureState()
    data class Success(val url: String) : CaptureState()
    data class Error(val message: String) : CaptureState()
}
