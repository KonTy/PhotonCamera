package com.particlesdevs.photoncamera.capture;

import android.app.Activity;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;

import com.particlesdevs.photoncamera.api.CameraEventsListener;
import com.particlesdevs.photoncamera.api.CameraManager2;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.Settings;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.TouchFocus;
import com.particlesdevs.photoncamera.manual.ParamController;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static android.hardware.camera2.CaptureRequest.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PreviewAutoFocusTest {
    private MockedStatic<PhotonCamera> app;
    private MockedStatic<PreferenceKeys> preferences;
    private MockedConstruction<CameraManager2> managers;
    private CaptureController controller;
    private CaptureRequest.Builder builder;
    private CameraCaptureSession session;
    private Settings settings;
    private final List<Integer> singles = new ArrayList<>();
    private final List<Integer> repeats = new ArrayList<>();
    private final Map<CaptureRequest, Integer> snapshots = new IdentityHashMap<>();
    private int lastWrite;

    @Before public void setUp() throws Exception {
        settings = mock(Settings.class);
        settings.selectedMode = CameraMode.PHOTO;
        app = mockStatic(PhotonCamera.class);
        app.when(PhotonCamera::getSettings).thenReturn(settings);
        PhotonCamera instance = mock(PhotonCamera.class);
        app.when(() -> PhotonCamera.getInstance(any())).thenReturn(instance);
        preferences = mockStatic(PreferenceKeys.class);
        preferences.when(PreferenceKeys::getAfMode).thenReturn(CONTROL_AF_MODE_AUTO);
        preferences.when(PreferenceKeys::getAeMode).thenReturn(CONTROL_AE_MODE_ON);
        managers = mockConstruction(CameraManager2.class, (manager, context) ->
                when(manager.getCameraIdList()).thenReturn(new String[0]));
        controller = new CaptureController(mock(Activity.class), mock(ExecutorService.class),
                mock(CameraEventsListener.class));
        builder = mock(CaptureRequest.Builder.class);
        session = mock(CameraCaptureSession.class);
        controller.mPreviewRequestBuilder = builder;
        setField(controller, "mCaptureSession", session);
        setField(controller, "mCameraAfModes", new int[]{CONTROL_AF_MODE_OFF,
                CONTROL_AF_MODE_AUTO, CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                CONTROL_AF_MODE_CONTINUOUS_VIDEO});
        CaptureController.mCameraCharacteristics = null;
        CaptureController.burst = false;
        when(builder.get(any())).thenReturn(CONTROL_AF_MODE_AUTO);
        // Android's local-test jar has null metadata keys. Observe the last setter
        // at build time (AF trigger at these submission boundaries), not fake key identity.
        doAnswer(call -> {
            Object value = call.getArgument(1);
            if (value instanceof Integer) lastWrite = (Integer) value;
            return null;
        }).when(builder).set(any(), any());
        when(builder.build()).thenAnswer(call -> {
            CaptureRequest request = mock(CaptureRequest.class);
            snapshots.put(request, lastWrite);
            return request;
        });
        doAnswer(call -> {
            singles.add(snapshots.get(call.getArgument(0)));
            return 1;
        }).when(session).capture(any(), any(), any());
        doAnswer(call -> {
            repeats.add(snapshots.get(call.getArgument(0)));
            return 2;
        }).when(session).setRepeatingRequest(any(), any(), any());
    }

    @After public void tearDown() {
        CaptureController.burst = false;
        CaptureController.mCameraCharacteristics = null;
        managers.close();
        preferences.close();
        app.close();
    }

    private static void setField(Object owner, String name, Object value) throws Exception {
        Field field = CaptureController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private CameraCaptureSession.CaptureCallback callback() throws Exception {
        Field field = CaptureController.class.getDeclaredField("mCaptureCallback");
        field.setAccessible(true);
        return (CameraCaptureSession.CaptureCallback) field.get(controller);
    }

    @Test public void initialAutoAcquiresOnceAndCompletionsNeverResubmit() throws Exception {
        when(builder.get(any())).thenReturn(CONTROL_AF_MODE_AUTO, CONTROL_AF_MODE_AUTO,
                null, null, CONTROL_AF_MODE_AUTO);
        controller.unlockFocus();
        assertEquals(List.of(CONTROL_AF_TRIGGER_CANCEL, CONTROL_AF_TRIGGER_START), singles);
        assertEquals(List.of(CONTROL_AF_TRIGGER_IDLE), repeats);
        assertEquals(CONTROL_AF_TRIGGER_IDLE, lastWrite);
        TotalCaptureResult result = mock(TotalCaptureResult.class);
        AtomicInteger reads = new AtomicInteger();
        // Only FLASH_STATE must be nonnull; the callback reads eleven metadata
        // values per completion, whose keys are all null in the local-test jar.
        when(result.get(any())).thenAnswer(call -> reads.getAndIncrement() % 11 == 5 ? 0 : null);
        CameraCaptureSession.CaptureCallback callback = callback();
        for (int i = 0; i < 120; i++)
            callback.onCaptureCompleted(session, controller.mPreviewInputRequest, result);
        assertEquals(2, singles.size());
        assertEquals(1, repeats.size());
    }

    @Test public void stillLockIsOneShotAndManualFocusDoesNotTrigger() {
        controller.takePicture();
        assertEquals(List.of(CONTROL_AF_TRIGGER_START), singles);
        assertEquals(List.of(CONTROL_AF_TRIGGER_IDLE), repeats);
        assertEquals(CONTROL_AF_TRIGGER_IDLE, lastWrite);
        when(builder.get(any())).thenReturn(CONTROL_AF_MODE_OFF);
        controller.takePicture();
        assertEquals(1, singles.size());
        assertEquals(List.of(CONTROL_AF_TRIGGER_IDLE, CONTROL_AF_TRIGGER_IDLE), repeats);
        assertEquals(3, controller.mState); // WAITING_NON_PRECAPTURE, not a nonexistent AF lock.
    }

    @Test public void manualRestorationPrecedesAutoAcquisition() throws Exception {
        ParamController params = mock(ParamController.class);
        setField(controller, "paramController", params);
        when(builder.get(any())).thenReturn(CONTROL_AF_MODE_OFF, null, null, CONTROL_AF_MODE_OFF);
        doAnswer(call -> {
            builder.set(CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);
            when(builder.get(any())).thenReturn(CONTROL_AF_MODE_OFF);
            return null;
        }).when(params).setupPreview();
        controller.unlockFocus();
        assertTrue(singles.isEmpty());
        assertEquals(List.of(CONTROL_AF_TRIGGER_IDLE), repeats);
        verify(params).setupPreview();
    }

    @Test public void unsupportedFixedAndContinuousModesDoNotGetPreviewAutoStart() throws Exception {
        when(builder.get(any())).thenReturn(CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        controller.startPreviewAutoFocus();
        when(builder.get(any())).thenReturn(CONTROL_AF_MODE_AUTO);
        setField(controller, "mCameraAfModes", new int[]{CONTROL_AF_MODE_OFF});
        controller.startPreviewAutoFocus();
        setField(controller, "mCameraAfModes", new int[]{CONTROL_AF_MODE_AUTO});
        CameraCharacteristics fixed = mock(CameraCharacteristics.class);
        when(fixed.get(any())).thenReturn(0f);
        CaptureController.mCameraCharacteristics = fixed;
        controller.startPreviewAutoFocus();
        assertTrue(singles.isEmpty());
    }

    @Test public void tapOwnershipSurvivesStillUnlockAndBlocksPreviewAcquisition() {
        TouchFocus touch = mock(TouchFocus.class);
        touch.isTouchFocus = true;
        controller.mTouchFocus = touch;
        controller.startPreviewAutoFocus();
        controller.takePicture();
        controller.unlockFocus();
        assertTrue(singles.isEmpty());
        assertEquals(List.of(CONTROL_AF_TRIGGER_IDLE, CONTROL_AF_TRIGGER_IDLE), repeats);
        verifyNoInteractions(touch);
    }

    @Test public void failedSkippedAndClosedSessionOneShotsAlwaysClearTrigger() throws Exception {
        doThrow(new IllegalStateException("closed")).when(session).capture(any(), any(), any());
        controller.startPreviewAutoFocus();
        assertEquals(CONTROL_AF_TRIGGER_IDLE, lastWrite);
        CaptureController.burst = true;
        builder.set(CONTROL_AF_TRIGGER, CONTROL_AF_TRIGGER_START);
        controller.rebuildPreviewBuilderOneShot();
        assertEquals(CONTROL_AF_TRIGGER_IDLE, lastWrite);
        CaptureController.burst = false;
        setField(controller, "mCaptureSession", null);
        builder.set(CONTROL_AF_TRIGGER, CONTROL_AF_TRIGGER_CANCEL);
        controller.rebuildPreviewBuilderOneShot();
        assertEquals(CONTROL_AF_TRIGGER_IDLE, lastWrite);
        verify(session, times(1)).capture(any(), any(), any());
    }

    @Test public void videoAndHighSpeedDoNotGetPhotoAutoStart() throws Exception {
        settings.selectedMode = CameraMode.VIDEO;
        controller.startPreviewAutoFocus();
        settings.selectedMode = CameraMode.PHOTO;
        controller.mIsRecordingVideo = true;
        setField(controller, "mHighSpeedRecording", true);
        controller.startPreviewAutoFocus();
        builder.set(CONTROL_AF_TRIGGER, CONTROL_AF_TRIGGER_START);
        controller.rebuildPreviewBuilderOneShot();
        assertTrue(singles.isEmpty());
        assertEquals(CONTROL_AF_TRIGGER_IDLE, lastWrite);
    }

    @Test public void explicitResetAcquiresOnceWithIdleRepeating() {
        when(builder.get(any())).thenReturn(null, null, CONTROL_AF_MODE_AUTO);
        controller.reset3Aparams();
        assertEquals(List.of(CONTROL_AF_TRIGGER_START), singles);
        assertEquals(List.of(CONTROL_AF_TRIGGER_IDLE), repeats);
        assertEquals(CONTROL_AF_TRIGGER_IDLE, lastWrite);
    }
}
