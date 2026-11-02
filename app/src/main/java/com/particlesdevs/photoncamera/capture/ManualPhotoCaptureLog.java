package com.particlesdevs.photoncamera.capture;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Scalar still-result evidence, not proof of saved output or optical focus. */
final class ManualPhotoCaptureLog {
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private final long captureId;
    private final Consumer<String> output;
    private boolean resultRecorded;
    private boolean terminalRecorded;

    record Values(Integer ae, Long exposureNs, Integer iso, Integer af, Float focusDiopters) {
        String fields(String prefix) {
            return " " + prefix + "AE=" + mode(ae,
                    CaptureRequest.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY)
                    + " " + prefix + "ExposureNs=" + positive(exposureNs)
                    + " " + prefix + "ISO=" + positive(iso)
                    + " " + prefix + "AF=" + mode(af, CaptureRequest.CONTROL_AF_MODE_EDOF)
                    + " " + prefix + "FocusDiopters="
                    + (focusDiopters != null && Float.isFinite(focusDiopters) && focusDiopters >= 0
                    ? Float.toString(focusDiopters) : "UNKNOWN");
        }

        private static String mode(Integer value, int maximum) {
            return value != null && value >= 0 && value <= maximum ? value.toString() : "UNKNOWN";
        }

        private static String positive(Number value) {
            return value != null && value.longValue() > 0 ? value.toString() : "UNKNOWN";
        }
    }

    static ManualPhotoCaptureLog create(boolean photo, long shutter, int iso, float focus) {
        return enabled(photo, shutter, iso, focus)
                ? new ManualPhotoCaptureLog(NEXT_ID.incrementAndGet(),
                line -> android.util.Log.i("PhotonManualCapture", line)) : null;
    }

    static boolean enabled(boolean photo, long shutter, int iso, float focus) {
        return photo && (shutter > 0 || iso > 0 || (Float.isFinite(focus) && focus >= 0));
    }

    ManualPhotoCaptureLog(long captureId, Consumer<String> output) {
        this.captureId = captureId;
        this.output = output;
    }

    void completed(CaptureRequest request, TotalCaptureResult result) {
        completed(result.getFrameNumber(),
                new Values(request.get(CaptureRequest.CONTROL_AE_MODE),
                        request.get(CaptureRequest.SENSOR_EXPOSURE_TIME),
                        request.get(CaptureRequest.SENSOR_SENSITIVITY),
                        request.get(CaptureRequest.CONTROL_AF_MODE),
                        request.get(CaptureRequest.LENS_FOCUS_DISTANCE)),
                new Values(result.get(TotalCaptureResult.CONTROL_AE_MODE),
                        result.get(TotalCaptureResult.SENSOR_EXPOSURE_TIME),
                        result.get(TotalCaptureResult.SENSOR_SENSITIVITY),
                        result.get(TotalCaptureResult.CONTROL_AF_MODE),
                        result.get(TotalCaptureResult.LENS_FOCUS_DISTANCE)));
    }

    synchronized void completed(long frame, Values requested, Values actual) {
        if (resultRecorded || terminalRecorded) return;
        resultRecorded = true;
        output.accept("capture=" + captureId + " event=RESULT frame=" + frame
                + requested.fields("requested") + actual.fields("actual"));
    }

    synchronized void sequenceCompleted(int sequenceId, long lastFrame) {
        terminal("SEQUENCE_COMPLETE sequence=" + sequenceId + " lastFrame=" + lastFrame);
    }

    synchronized void failed(int reason) {
        terminal("CAPTURE_ERROR reason=" + reason);
    }

    synchronized void submissionFailed(Integer reason, boolean configuration) {
        terminal("CAPTURE_ERROR stage=" + (configuration ? "CONFIGURATION" : "SUBMISSION")
                + " reason=" + (reason == null ? "UNKNOWN" : reason));
    }

    synchronized void aborted(int sequenceId) {
        terminal("SEQUENCE_ABORTED sequence=" + sequenceId);
    }

    private void terminal(String detail) {
        if (terminalRecorded) return;
        terminalRecorded = true;
        output.accept("capture=" + captureId + " event=" + detail);
    }
}
