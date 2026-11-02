package com.particlesdevs.photoncamera.manual;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;

import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FocusDistanceTest {
    @Test public void includesInfinityAndSensorEndpoints() {
        assertTrue(FocusDistance.isValid(0f, 10f));
        assertTrue(FocusDistance.isValid(10f, 10f));
        assertTrue(FocusDistance.isValid(0f, 0f));
    }

    @Test public void rejectsAutoInvalidAndUnknownRange() {
        for (float value : new float[]{-1f, -0.01f, 10.01f, Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertFalse(FocusDistance.isValid(value, 10f));
        }
        assertFalse(FocusDistance.isValid(0f, null));
        assertFalse(FocusDistance.isValid(0f, Float.NaN));
        assertFalse(FocusDistance.isValid(0f, Float.POSITIVE_INFINITY));
        assertFalse(FocusDistance.isValid(0f, -1f));
    }

    @Test public void rejectedObserverInputDoesNotReplaceAcceptedFocusOrRebuildPreview() {
        CameraCharacteristics previous = CaptureController.mCameraCharacteristics;
        try {
            CameraCharacteristics sensor = mock(CameraCharacteristics.class);
            when(sensor.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)).thenReturn(8f);
            CaptureController.mCameraCharacteristics = sensor;
            CaptureController capture = mock(CaptureController.class);
            CaptureRequest.Builder builder = mock(CaptureRequest.Builder.class);
            capture.mPreviewRequestBuilder = builder;
            ParamController controller = new ParamController(capture);
            controller.setFocus(0f);
            assertEquals(0f, controller.FOCUS, 0f);
            verify(builder).set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            verify(builder).set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f);
            verify(capture).rebuildPreviewBuilder();
            clearInvocations(builder, capture);

            ManualParamModel model = mock(ManualParamModel.class);
            when(model.getCurrentFocusValue()).thenReturn(Double.NaN);
            controller.update(model, ManualParamModel.ID_FOCUS);
            assertEquals(0f, controller.FOCUS, 0f);
            verifyNoInteractions(builder, capture);

            capture.mPreviewRequestBuilder = null;
            controller.setFocus(2f);
            assertEquals(0f, controller.FOCUS, 0f);
        } finally {
            CaptureController.mCameraCharacteristics = previous;
        }
    }
}
