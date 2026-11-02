package com.particlesdevs.photoncamera.ui.camera;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class CameraStartupPermissionsTest {
    @Test public void manualPhotoDoesNotRequireLibraryOrSafOnModernAndroid() {
        for (int sdk : new int[]{30, 31, 33, 35, 36}) {
            assertFalse(CameraActivity.requiresLibraryAccessForStartup("manualPhoto", false, sdk));
        }
    }

    @Test public void ordinaryBuildsKeepExistingLibraryRequirements() {
        for (String buildType : new String[]{"debug", "release", "manualPhotoOther", "", null}) {
            for (int sdk : new int[]{26, 29, 30, 33, 36}) {
                assertTrue(CameraActivity.requiresLibraryAccessForStartup(buildType, false, sdk));
            }
        }
    }

    @Test public void legacyManualCandidateKeepsStorageChecksAndSecurePolicyIsUnchanged() {
        for (int sdk : new int[]{26, 28, 29}) {
            assertTrue(CameraActivity.requiresLibraryAccessForStartup("manualPhoto", false, sdk));
        }
        for (String buildType : new String[]{"debug", "release", "manualPhoto"}) {
            for (int sdk : new int[]{26, 29, 30, 36}) {
                assertFalse(CameraActivity.requiresLibraryAccessForStartup(buildType, true, sdk));
            }
        }
    }

    @Test public void bothStartupPathsUseBuildTypePolicyAfterExistingBasicChecks() throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of(".") : Path.of("..");
        String source = new String(Files.readAllBytes(root.resolve(
                "app/src/main/java/com/particlesdevs/photoncamera/ui/camera/CameraActivity.java")),
                StandardCharsets.UTF_8);
        String request = source.substring(source.indexOf("private void requestPermission()"),
                source.indexOf("private void doRequestMediaPermission()"));
        String check = source.substring(source.indexOf("private boolean hasAllPermissions()"),
                source.indexOf("private void tryLoad()"));
        String policy = "requiresLibraryAccessForStartup(BuildConfig.BUILD_TYPE, secureSession, SDK_INT)";
        assertTrue(request.indexOf("Arrays.stream(PERMISSIONS).anyMatch") < request.indexOf(policy));
        assertTrue(request.contains("requestPermissions(PERMISSIONS, CODE_REQUEST_PERMISSIONS)"));
        assertTrue(request.contains("if (!" + policy + ")"));
        assertTrue(check.contains("Arrays.stream(PERMISSIONS).allMatch"));
        assertTrue(check.contains("if (!" + policy + ") {\n            return basicPermissions;"));
        assertTrue(check.contains("&& hasMediaReadPermission()"));
        assertTrue(check.contains("&& SimpleStorageHelper.hasStorageAccess(this)"));
        assertTrue(source.contains("Manifest.permission.CAMERA,\n"
                + "            Manifest.permission.RECORD_AUDIO,\n"
                + "            Manifest.permission.INTERNET"));
    }
}
