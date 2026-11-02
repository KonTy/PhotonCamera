package com.particlesdevs.photoncamera.processing.opengl;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OptionalTuningFileTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void policyIsOnlyModernManualPhoto() {
        for (int sdk : new int[]{30, 33, 36}) {
            assertTrue(GLBasePipeline.usesOptionalTuningFile("manualPhoto", sdk));
        }
        for (int sdk : new int[]{26, 28, 29}) {
            assertFalse(GLBasePipeline.usesOptionalTuningFile("manualPhoto", sdk));
        }
        for (String type : new String[]{"debug", "release", "manualPhotoOther", "", null}) {
            assertFalse(GLBasePipeline.usesOptionalTuningFile(type, 36));
        }
    }

    @Test public void absentFileIsReportedWithoutCreatingOpeningOrLoadingIt() throws Exception {
        File file = new File(temporary.getRoot(), "PhotonCameraTuning.ini");
        Properties properties = mock(Properties.class);
        ArrayList<String> reports = new ArrayList<>();
        GLBasePipeline.loadOptionalTuning(file, properties, reports::add);
        assertFalse(file.exists());
        verifyNoInteractions(properties);
        assertEquals(1, reports.size());
        assertEquals("Optional PhotonCameraTuning.ini absent; using built-in tuning defaults", reports.get(0));
    }

    @Test public void existingFileLoadsOverridesAndPreservesBytes() throws Exception {
        File file = temporary.newFile("PhotonCameraTuning.ini");
        byte[] content = "Node_Value=42\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(file.toPath(), content);
        Properties properties = new Properties();
        ArrayList<String> reports = new ArrayList<>();
        GLBasePipeline.loadOptionalTuning(file, properties, reports::add);
        assertEquals("42", properties.getProperty("Node_Value", "7"));
        assertEquals("7", properties.getProperty("Node_Missing", "7"));
        assertTrue(reports.isEmpty());
        assertArrayEquals(content, Files.readAllBytes(file.toPath()));
    }

    @Test public void existingUnreadableInputDoesNotBecomeAbsentSuccess() throws Exception {
        File directory = temporary.newFolder("PhotonCameraTuning.ini");
        Properties properties = new Properties();
        ArrayList<String> reports = new ArrayList<>();
        assertThrows(IOException.class,
                () -> GLBasePipeline.loadOptionalTuning(directory, properties, reports::add));
        assertTrue(reports.isEmpty());
        assertTrue(directory.isDirectory());
    }

    @Test public void parseFailureIsNotReportedAsAbsentOrSuccessfulDefaults() throws Exception {
        File file = temporary.newFile("PhotonCameraTuning.ini");
        byte[] content = "Node_Value=\\uINVALID\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(file.toPath(), content);
        ArrayList<String> reports = new ArrayList<>();
        assertThrows(IllegalArgumentException.class,
                () -> GLBasePipeline.loadOptionalTuning(file, new Properties(), reports::add));
        assertTrue(reports.isEmpty());
        assertArrayEquals(content, Files.readAllBytes(file.toPath()));
    }
}
