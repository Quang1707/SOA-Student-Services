package com.example.document.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentFileStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    private DocumentFileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new DocumentFileStorageService();
        ReflectionTestUtils.setField(storage, "storageDirectory", temporaryDirectory.toString());
        storage.initialize();
    }

    @Test
    void storesPdfWithGeneratedNameAndReturnsReadableResource() throws Exception {
        byte[] pdfHeader = "%PDF-1.7\nunit-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "minh-chung.pdf", "application/pdf", pdfHeader);

        DocumentFileStorageService.StoredFile stored = storage.store(file);

        assertTrue(stored.storageKey().endsWith(".pdf"));
        assertNotEquals("minh-chung.pdf", stored.storageKey());
        assertEquals("application/pdf", stored.contentType());
        assertTrue(storage.loadAsResource(stored.storageKey()).exists());
        assertTrue(Files.exists(temporaryDirectory.resolve(stored.storageKey())));
    }

    @Test
    void rejectsExecutableOrMismatchedContentEvenWhenExtensionLooksSafe() {
        MockMultipartFile file = new MockMultipartFile("file", "malicious.pdf", "application/pdf", "<script>alert(1)</script>".getBytes());
        assertThrows(IllegalArgumentException.class, () -> storage.store(file));
    }

    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "payload.html", "text/html", "<html/>".getBytes());
        assertThrows(IllegalArgumentException.class, () -> storage.store(file));
    }

    @Test
    void rejectsFilesLargerThanTenMegabytes() {
        byte[] bytes = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "large.pdf", "application/pdf", bytes);
        assertThrows(IllegalArgumentException.class, () -> storage.store(file));
    }

    @Test
    void rejectsPathTraversalStorageKeys() {
        assertThrows(IllegalArgumentException.class, () -> storage.loadAsResource("../../secret.pdf"));
    }
}
