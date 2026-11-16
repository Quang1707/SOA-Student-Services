package com.example.document.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lưu file ngoài web root. Tên file lưu trên đĩa được sinh ngẫu nhiên,
 * không sử dụng trực tiếp tên file do người dùng gửi lên.
 */
@Service
public class DocumentFileStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;
    private static final Pattern STORAGE_KEY_PATTERN =
            Pattern.compile("^[0-9a-fA-F-]{36}\\.(pdf|png|jpg|jpeg)$");

    @Value("${app.document.storage-dir:./uploads}")
    private String storageDirectory;

    private Path root;

    @PostConstruct
    public void initialize() {
        try {
            root = Paths.get(storageDirectory).toAbsolutePath().normalize();
            Files.createDirectories(root);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Không thể khởi tạo thư mục lưu tài liệu.", ex);
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file tài liệu.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Dung lượng file tối đa là 10 MB.");
        }

        String originalFilename = sanitizeOriginalFilename(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        String contentType = detectAndValidateContent(file, extension);
        String storageKey = UUID.randomUUID() + "." + extension;
        Path target = resolveSafe(storageKey);

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target);
            return new StoredFile(storageKey, originalFilename, contentType, file.getSize());
        } catch (IOException ex) {
            deleteQuietly(storageKey);
            throw new IllegalStateException("Không thể lưu file tài liệu.", ex);
        }
    }

    public Resource loadAsResource(String storageKey) {
        Path file = resolveSafe(storageKey);
        try {
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
                throw new IllegalArgumentException("Không tìm thấy file tài liệu trên máy chủ.");
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("File tài liệu không thể đọc được.");
            }
            return resource;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Không thể đọc file tài liệu.", ex);
        }
    }

    public void deleteQuietly(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveSafe(storageKey));
        } catch (IOException | RuntimeException ignored) {
            // Không làm hỏng giao dịch nghiệp vụ chỉ vì lỗi dọn file.
        }
    }

    private Path resolveSafe(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Mã lưu trữ file không hợp lệ.");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Đường dẫn file không hợp lệ.");
        }
        return resolved;
    }

    private String sanitizeOriginalFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("Tên file không hợp lệ.");
        }
        String normalized = filename.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        normalized = normalized.replaceAll("[\\p{Cntrl}]", "").trim();
        if (normalized.isBlank() || normalized.length() > 255 || normalized.contains("..")) {
            throw new IllegalArgumentException("Tên file không hợp lệ hoặc vượt quá 255 ký tự.");
        }
        return normalized;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 1 || dot == filename.length() - 1) {
            throw new IllegalArgumentException("Chỉ chấp nhận file PDF, PNG, JPG hoặc JPEG.");
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extension.matches("pdf|png|jpg|jpeg")) {
            throw new IllegalArgumentException("Định dạng không được hỗ trợ. Chỉ chấp nhận PDF, PNG, JPG hoặc JPEG.");
        }
        return extension;
    }

    private String detectAndValidateContent(MultipartFile file, String extension) {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(16);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Không thể đọc nội dung file.", ex);
        }

        boolean pdf = startsWith(header, new byte[]{'%', 'P', 'D', 'F', '-'});
        boolean png = startsWith(header, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        boolean jpeg = header.length >= 3 && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;

        if ("pdf".equals(extension) && pdf) return "application/pdf";
        if ("png".equals(extension) && png) return "image/png";
        if (("jpg".equals(extension) || "jpeg".equals(extension)) && jpeg) return "image/jpeg";

        throw new IllegalArgumentException("Nội dung file không khớp định dạng PDF, PNG hoặc JPEG được khai báo.");
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) return false;
        }
        return true;
    }

    public record StoredFile(String storageKey, String originalFilename, String contentType, long size) {}
}
