package com.particlesdevs.photoncamera.manual;

import android.hardware.camera2.CaptureRequest;

import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.control.TouchFocus;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ParamFocusTest {
    @Test public void returningToAutoHandsOffTapOwnershipAndAcquiresAfterRepeating() {
        CaptureController controller = mock(CaptureController.class);
        CaptureRequest.Builder builder = mock(CaptureRequest.Builder.class);
        TouchFocus touch = mock(TouchFocus.class);
        controller.mPreviewRequestBuilder = builder;
        controller.mTouchFocus = touch;
        when(builder.get(any())).thenReturn(CaptureRequest.CONTROL_AF_MODE_OFF);
        try (MockedStatic<PreferenceKeys> prefs = mockStatic(PreferenceKeys.class)) {
            prefs.when(PreferenceKeys::getAfMode).thenReturn(CaptureRequest.CONTROL_AF_MODE_AUTO);
            ParamController params = new ParamController(controller);
            params.setFocus((float) ManualParamModel.FOCUS_AUTO);
            InOrder order = inOrder(touch, builder, controller);
            order.verify(touch).clearFocusOwnership();
            order.verify(builder).set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            order.verify(controller).rebuildPreviewBuilder();
            order.verify(controller).startPreviewAutoFocus();
            clearInvocations(controller);
            when(builder.get(any())).thenReturn(CaptureRequest.CONTROL_AF_MODE_AUTO);
            params.setFocus((float) ManualParamModel.FOCUS_AUTO);
            verify(controller, never()).startPreviewAutoFocus();
        }
    }
}
