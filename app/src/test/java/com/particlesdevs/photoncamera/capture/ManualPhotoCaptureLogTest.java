package com.particlesdevs.photoncamera.capture;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ManualPhotoCaptureLogTest {
    private static final ManualPhotoCaptureLog.Values MANUAL =
            new ManualPhotoCaptureLog.Values(0, 10_000_000L, 400, 0, 0f);

    @Test public void onlyManualPhotoIsEnabledIncludingInfinity() {
        assertTrue(ManualPhotoCaptureLog.enabled(true, -1, -1, 0));
        assertTrue(ManualPhotoCaptureLog.enabled(true, 10_000_000, -1, -1));
        assertTrue(ManualPhotoCaptureLog.enabled(true, -1, 400, -1));
        assertFalse(ManualPhotoCaptureLog.enabled(false, 10_000_000, 400, 0));
        assertFalse(ManualPhotoCaptureLog.enabled(true, -1, -1, -1));
        assertFalse(ManualPhotoCaptureLog.enabled(true, 0, 0, -1));
        assertFalse(ManualPhotoCaptureLog.enabled(true, 0, 0, Float.NaN));
        assertFalse(ManualPhotoCaptureLog.enabled(true, 0, 0, Float.POSITIVE_INFINITY));
    }

    @Test public void requestedAndActualStayDistinctWithExplicitZeroFocusAndModes() {
        List<String> lines = new ArrayList<>();
        var log = new ManualPhotoCaptureLog(7, lines::add);
        log.completed(123, MANUAL, new ManualPhotoCaptureLog.Values(1, 20_000_000L, 800, 4, 2f));
        assertEquals(List.of("capture=7 event=RESULT frame=123"
                + " requestedAE=0 requestedExposureNs=10000000 requestedISO=400 requestedAF=0 requestedFocusDiopters=0.0"
                + " actualAE=1 actualExposureNs=20000000 actualISO=800 actualAF=4 actualFocusDiopters=2.0"), lines);
    }

    @Test public void missingInvalidAndNonfiniteMetadataIsUnknownNotAFallback() {
        for (Float focus : new Float[]{null, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            List<String> lines = new ArrayList<>();
            var log = new ManualPhotoCaptureLog(1, lines::add);
            log.completed(0, new ManualPhotoCaptureLog.Values(null, null, null, null, focus),
                    new ManualPhotoCaptureLog.Values(-1, 0L, -1, -1, focus));
            assertEquals("capture=1 event=RESULT frame=0"
                    + " requestedAE=UNKNOWN requestedExposureNs=UNKNOWN requestedISO=UNKNOWN requestedAF=UNKNOWN requestedFocusDiopters=UNKNOWN"
                    + " actualAE=UNKNOWN actualExposureNs=UNKNOWN actualISO=UNKNOWN actualAF=UNKNOWN actualFocusDiopters=UNKNOWN", lines.get(0));
        }
    }

    @Test public void firstResultAndOneTerminalRecordAreTheEntirePerShotBudget() {
        List<String> lines = new ArrayList<>();
        var log = new ManualPhotoCaptureLog(2, lines::add);
        log.completed(10, MANUAL, MANUAL);
        log.completed(11, MANUAL, MANUAL);
        log.sequenceCompleted(8, 11);
        log.sequenceCompleted(8, 11);
        log.failed(1);
        log.aborted(8);
        log.completed(12, MANUAL, MANUAL);
        assertEquals(2, lines.size());
        assertEquals("capture=2 event=SEQUENCE_COMPLETE sequence=8 lastFrame=11", lines.get(1));
        assertFalse(String.join("", lines).contains("SAVED"));
    }

    @Test public void failureAndAbortNeverProduceSuccessOrLateResult() {
        List<String> lines = new ArrayList<>();
        var failed = new ManualPhotoCaptureLog(3, lines::add);
        failed.failed(0);
        failed.completed(1, MANUAL, MANUAL);
        failed.sequenceCompleted(1, 1);
        var aborted = new ManualPhotoCaptureLog(4, lines::add);
        aborted.aborted(9);
        aborted.completed(2, MANUAL, MANUAL);
        assertEquals(List.of("capture=3 event=CAPTURE_ERROR reason=0",
                "capture=4 event=SEQUENCE_ABORTED sequence=9"), lines);
    }

    @Test public void maximumScalarLengthsStayBoundedAscii() {
        List<String> lines = new ArrayList<>();
        var log = new ManualPhotoCaptureLog(Long.MAX_VALUE, lines::add);
        var maximum = new ManualPhotoCaptureLog.Values(Integer.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Float.MAX_VALUE);
        log.completed(Long.MAX_VALUE, maximum, maximum);
        log.sequenceCompleted(Integer.MAX_VALUE, Long.MAX_VALUE);
        for (String line : lines) {
            assertTrue(line.length() <= 512);
            assertEquals(line.length(), line.getBytes(StandardCharsets.US_ASCII).length);
            assertTrue(line.chars().allMatch(character -> character >= 32 && character <= 126));
        }
    }

    @Test public void submissionAndConfigurationFailuresKeepUnknownReasonsExplicit() {
        List<String> lines = new ArrayList<>();
        var submission = new ManualPhotoCaptureLog(5, lines::add);
        submission.submissionFailed(null, false);
        submission.sequenceCompleted(1, 1);
        var configuration = new ManualPhotoCaptureLog(6, lines::add);
        configuration.submissionFailed(null, true);
        var access = new ManualPhotoCaptureLog(7, lines::add);
        access.submissionFailed(2, false);
        assertEquals(List.of("capture=5 event=CAPTURE_ERROR stage=SUBMISSION reason=UNKNOWN",
                "capture=6 event=CAPTURE_ERROR stage=CONFIGURATION reason=UNKNOWN",
                "capture=7 event=CAPTURE_ERROR stage=SUBMISSION reason=2"), lines);
    }

    @Test public void unknownPublicModesAreNotReportedAsValidActualModes() {
        List<String> lines = new ArrayList<>();
        new ManualPhotoCaptureLog(8, lines::add).completed(1, MANUAL,
                new ManualPhotoCaptureLog.Values(Integer.MAX_VALUE, 1L, 1, Integer.MAX_VALUE, 0f));
        assertTrue(lines.get(0).contains(" actualAE=UNKNOWN"));
        assertTrue(lines.get(0).contains(" actualAF=UNKNOWN"));
    }

    @Test public void cameraMetadataReadsActualValuesWithoutRequestFallbacks() {
        CaptureRequest request = mock(CaptureRequest.class);
        TotalCaptureResult result = mock(TotalCaptureResult.class);
        // Android's local-test jar has null Camera2 keys; supply the typed getter sequence.
        doReturn(0, 10_000_000L, 400, 0, 0f).when(request).get(any());
        when(result.getFrameNumber()).thenReturn(12L);
        List<String> lines = new ArrayList<>();
        new ManualPhotoCaptureLog(9, lines::add).completed(request, result);
        assertEquals("capture=9 event=RESULT frame=12" + MANUAL.fields("requested")
                + new ManualPhotoCaptureLog.Values(null, null, null, null, null).fields("actual"), lines.get(0));
        verify(result, times(5)).get(any());
    }

    @Test public void productionWiringUsesActualStillResultAndBaselineLifecycleGuards() throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of(".") : Path.of("..");
        String controller = new String(Files.readAllBytes(root.resolve(
                "app/src/main/java/com/particlesdevs/photoncamera/capture/CaptureController.java")), StandardCharsets.UTF_8);
        assertTrue(controller.contains("PhotonCamera.getSettings().selectedMode == CameraMode.PHOTO,\n"
                + "                    paramController.SHUTTER, paramController.ISO, paramController.FOCUS"));
        assertTrue(controller.contains("if (ownsManualCapture(session) && shotRequests.contains(request)) {\n"
                + "                        manualLog.completed(request, result);"));
        assertTrue(controller.contains("if (ownsManualCapture(session)) {\n"
                + "                        manualLog.sequenceCompleted(sequenceId, lastFrameNumber);"));
        assertTrue(controller.contains("CaptureCallback == this"));
        assertTrue(controller.contains("mCameraDevice == shotDevice && openToken.get() == shotOpenToken"));
        assertTrue(controller.contains("session == mCaptureSession"));
        assertTrue(controller.contains("session.getDevice() == shotDevice"));
        assertTrue(controller.contains("final List<CaptureRequest> shotRequests = new ArrayList<>(captures);"));
        assertTrue(controller.contains("manualLog.failed(failure.getReason());"));
        assertTrue(controller.contains("manualLog.aborted(sequenceId);"));
        assertTrue(controller.contains("if (submissionLog != null) submissionLog.failed(e.getReason());"));
        assertTrue(controller.contains("if (burstCallback != null) burstCallback.submissionFailed(e, false);"));
        assertTrue(controller.contains("if (burstCallback != null) burstCallback.submissionFailed(null, true);"));
        assertFalse(controller.contains("StillCaptureGate"));
        String helper = new String(Files.readAllBytes(root.resolve(
                "app/src/main/java/com/particlesdevs/photoncamera/capture/ManualPhotoCaptureLog.java")), StandardCharsets.UTF_8);
        for (String key : new String[]{"CONTROL_AE_MODE", "SENSOR_EXPOSURE_TIME",
                "SENSOR_SENSITIVITY", "CONTROL_AF_MODE", "LENS_FOCUS_DISTANCE"}) {
            assertTrue(helper.contains("request.get(CaptureRequest." + key + ")"));
            assertTrue(helper.contains("result.get(TotalCaptureResult." + key + ")"));
        }
        assertFalse(helper.contains(".set("));
        assertFalse(helper.contains("getCameraCharacteristics"));
        assertFalse(helper.contains("PhotonCamera.get"));
    }
}
