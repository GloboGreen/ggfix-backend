package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.config.CloudinaryConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generic image upload for master-data screens (Categories, Brands, Models, …).
 * Posts the file to Cloudinary's signed upload API directly via RestTemplate —
 * no SDK dependency. Falls back to inline base64 data URIs when Cloudinary
 * credentials aren't configured so the admin still works end-to-end in dev.
 *
 * Response shape:
 * <pre>
 * { url: "https://res.cloudinary.com/.../abc.png",
 *   publicId: "ggfix/master/abc",   // null when source == base64
 *   source: "cloudinary" | "base64",
 *   bytes: 12345 }
 * </pre>
 */
@RestController
@RequestMapping("/media")
public class MediaController {

    private final CloudinaryConfig cfg;
    private final RestTemplate restTemplate;

    public MediaController(CloudinaryConfig cfg, RestTemplate restTemplate) {
        this.cfg = cfg;
        this.restTemplate = restTemplate;
    }

    /**
     * Health-check / build verification. Hitting GET /media/ping in a browser
     * confirms the service has been rebuilt with the latest MediaController.
     * Response includes whether Cloudinary creds are configured so you can tell
     * upload mode at a glance.
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("controller", "MediaController");
        out.put("cloudinary", cfg.isConfigured() ? "configured" : "fallback-base64");
        out.put("folder", cfg.getFolder());
        return ResponseEntity.ok(out);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "folder", required = false) String folder) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
        }

        try {
            if (cfg.isConfigured()) {
                return ResponseEntity.ok(uploadToCloudinary(file, folder));
            }
            return ResponseEntity.ok(toBase64DataUri(file));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    // ---- Cloudinary signed upload via HTTP (no SDK) ----
    private Map<String, Object> uploadToCloudinary(MultipartFile file, String folderOverride) throws Exception {
        String targetFolder = resolveFolder(folderOverride);
        long timestamp = System.currentTimeMillis() / 1000L;

        // Cloudinary segments uploads by resource type. `/image/upload` rejects
        // audio/video files; `/video/upload` accepts both video AND audio (per
        // Cloudinary docs); `/raw/upload` is for anything else (PDFs, etc.).
        // We pick based on the incoming Content-Type so the customer's voice
        // note (audio/m4a) lands at /video/upload instead of failing.
        String resourceType = resourceTypeFor(file.getContentType());

        // Build the parameter set to sign (lexicographically sorted by key).
        TreeMap<String, String> toSign = new TreeMap<>();
        toSign.put("folder", targetFolder);
        toSign.put("timestamp", String.valueOf(timestamp));

        String signature = sha1Hex(joinForSignature(toSign) + cfg.getApiSecret());

        // Build the multipart request to Cloudinary.
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() {
                return file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
            }
        });
        body.add("api_key", cfg.getApiKey());
        body.add("timestamp", String.valueOf(timestamp));
        body.add("folder", targetFolder);
        body.add("signature", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        String url = "https://api.cloudinary.com/v1_1/" + cfg.getCloudName() + "/" + resourceType + "/upload";
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();

        Map<String, Object> out = new HashMap<>();
        out.put("url", result.get("secure_url"));
        out.put("publicId", result.get("public_id"));
        out.put("source", "cloudinary");
        out.put("resourceType", resourceType);
        out.put("width", result.get("width"));
        out.put("height", result.get("height"));
        out.put("duration", result.get("duration"));
        out.put("bytes", result.get("bytes"));
        return out;
    }

    /**
     * Map an incoming Content-Type to the matching Cloudinary upload segment.
     * Audio files must go to /video/upload — Cloudinary handles audio there.
     * Anything we don't recognise as image/video/audio falls back to /raw/upload
     * so the request still succeeds.
     */
    private static String resourceTypeFor(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase();
        if (ct.startsWith("image/")) return "image";
        if (ct.startsWith("video/")) return "video";
        if (ct.startsWith("audio/")) return "video"; // Cloudinary accepts audio at /video/upload
        return "raw";
    }

    // ---- Fallback: base64 data URI ----
    private Map<String, Object> toBase64DataUri(MultipartFile file) throws Exception {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) contentType = "image/png";
        String base64 = Base64.getEncoder().encodeToString(file.getBytes());
        String dataUri = "data:" + contentType + ";base64," + base64;
        Map<String, Object> out = new HashMap<>();
        out.put("url", dataUri);
        out.put("publicId", null);
        out.put("source", "base64");
        out.put("bytes", file.getSize());
        return out;
    }

    /**
     * Resolve the Cloudinary destination folder. The caller (admin form) sends
     * a SHORT subfolder name like "categories" / "brands" / "models" and we
     * prepend the configured base ("ggifx"), giving "ggifx/categories" etc.
     * If the caller passes nothing, we use the base alone. If the caller
     * passes a value that already starts with the base, we don't double it.
     */
    private String resolveFolder(String sub) {
        String base = cfg.getFolder() == null ? "" : cfg.getFolder().trim().replaceAll("[\\\\/]+$", "");
        String s = sub == null ? "" : sub.trim().replaceAll("^[\\\\/]+|[\\\\/]+$", "");
        if (s.isEmpty()) return base.isEmpty() ? null : base;
        if (base.isEmpty()) return s;
        if (s.equals(base) || s.startsWith(base + "/")) return s;
        return base + "/" + s;
    }

    private static String joinForSignature(TreeMap<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static String sha1Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
