package org.neurotecfinger.controller;

import org.neurotecfinger.service.ScannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@CrossOrigin(origins = "*") // Allow all origins like the old driver
public class LegacyDriverController {

    @Autowired
    private ScannerService scannerService;

    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }

    @PostMapping("/stopscan")
    public String stopScan() {
        if (scannerService.isScanning()) {
            scannerService.cancelScanner();
        }
        return "ok";
    }

    @PostMapping("/fingerprints")
    public ResponseEntity<Map<String, Object>> scanFingerprints(@RequestParam(value = "Timeout", defaultValue = "10000") String timeoutStr, @RequestParam(value = "fingerType", required = false) String fingerType) {
        Map<String, Object> response = new HashMap<>();
        int timeout = Integer.parseInt(timeoutStr);
        System.out.println("Timeout param:" + timeoutStr);

        try {
            // Call async scan and wait for result with timeout
            Map<String, Object> fingerDetails = scannerService.scanFinger(fingerType).get(timeout, TimeUnit.MILLISECONDS);
            if (!"unknown".equalsIgnoreCase(fingerType)) {
                scannerService.saveFingerprintData(
                        fingerType,
                        (String) fingerDetails.get("WSQImage"),
                        (String) fingerDetails.get("BMPBase64"),
                        (String) fingerDetails.get("NativeTemplate"),
                        (Integer) fingerDetails.get("NFIQ")
                );
                System.out.println("✅ ENROLL: Saved " + fingerType + " to DB.");
            } else {
                System.out.println("ℹ️ IDENTIFY: Scanned without saving.");
            }
            // Success Response Structure
            response.put("data", fingerDetails);
            return ResponseEntity.ok(response);

        } catch (TimeoutException e) {
            scannerService.cancelScanner();
            response.put("error", "Timeout waiting for scan");
            return ResponseEntity.status(413).body(response); // 413 Payload Too Large (Used as timeout code in old driver)

        } catch (Exception e) {
            // Unwrap ExecutionException
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String errorMsg = cause.getMessage();

            // Handle "Busy" logic (Old BScannerException code 409)
            if (errorMsg != null && errorMsg.contains("already trying")) {
                response.put("error", errorMsg);
                return ResponseEntity.status(409).body(response);
            }

            // Default Error
            e.printStackTrace();
            response.put("error", errorMsg != null ? errorMsg.split("\\r")[0] : "Unknown Error");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/getWsqFromBmp")
    public ResponseEntity<Map<String, String>> getWsqFromBmp(@RequestParam("uploaded_file") MultipartFile file) {
        Map<String, String> response = new HashMap<>();
        try {
            String wsqBase64 = scannerService.convertBmpToWsq(file);
            response.put("WSQImage", wsqBase64);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", e.getMessage());
            // Old driver returned 200 OK with error field sometimes, but 500 is safer if it failed hard.
            // Following old pattern roughly:
            return ResponseEntity.ok(response);
        }
    }


    // 2. Handle the Identification Request
    @GetMapping("/identify-page")
    public ModelAndView showIdentifyPage() {
        return new ModelAndView("identify");
    }

    // --- IDENTIFY API ---
    @PostMapping("/api/identify")
    public ResponseEntity<Map<String, Object>> identify(@RequestBody Map<String, String> payload) {
        String template = payload.get("nativeTemplate");
        System.out.println("template from controller: " + template);
        return ResponseEntity.ok(scannerService.identify(template));
    }
}
