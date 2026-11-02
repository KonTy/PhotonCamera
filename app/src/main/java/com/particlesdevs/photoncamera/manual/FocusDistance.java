package com.particlesdevs.photoncamera.manual;

import android.hardware.camera2.CaptureRequest;

public final class FocusDistance {
    private FocusDistance() {}

    public static boolean isValid(float distance, Float sensorMaximum) {
        return Float.isFinite(distance) && distance >= 0.0f
                && sensorMaximum != null && Float.isFinite(sensorMaximum)
                && sensorMaximum >= 0.0f && distance <= sensorMaximum;
    }

    public static void applyToStill(CaptureRequest.Builder builder, float distance, Float sensorMaximum,
                                    boolean previewAfOff) {
        if (distance == -1.0f) return;
        if (!isValid(distance, sensorMaximum)) {
            throw new IllegalArgumentException("Invalid manual still focus distance");
        }
        if (!previewAfOff) return;
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, distance);
    }
}
