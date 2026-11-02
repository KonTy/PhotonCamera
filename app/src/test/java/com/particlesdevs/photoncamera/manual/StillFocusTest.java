package com.particlesdevs.photoncamera.manual;

import android.hardware.camera2.CaptureRequest;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StillFocusTest {
    @Test public void burstBuilderKeepsZeroAndManualDistanceButDoesNotOverrideAutoOrTap() {
        CaptureRequest.Builder builder = mock(CaptureRequest.Builder.class);
        ArrayList<Object> values = new ArrayList<>();
        doAnswer(call -> {
            values.add(call.getArgument(1));
            return null;
        }).when(builder).set(any(), any());
        for (float distance : new float[]{0, 2, 8}) {
            values.clear();
            clearInvocations(builder);
            FocusDistance.applyToStill(builder, distance, 8f, true);
            assertEquals(List.of(CaptureRequest.CONTROL_AF_MODE_OFF,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE, distance), values);
            verify(builder, times(3)).set(any(), any());
        }
        clearInvocations(builder);
        FocusDistance.applyToStill(builder, -1, null, false);
        FocusDistance.applyToStill(builder, 2, 8f, false);
        verifyNoInteractions(builder);
        for (float distance : new float[]{-2, 9, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> FocusDistance.applyToStill(builder, distance, 8f, true));
        }
        assertThrows(IllegalArgumentException.class, () -> FocusDistance.applyToStill(builder, 0, null, true));
        verifyNoInteractions(builder);
    }
}
