package org.neurotecfinger.service;

import com.neurotec.biometrics.*;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.devices.NDeviceManager;
import com.neurotec.devices.NDeviceType;
import com.neurotec.devices.NFScanner;
import com.neurotec.images.NImage;
import com.neurotec.images.NImageFormat;
import com.neurotec.images.WSQInfo;
import com.neurotec.io.NBuffer;
import com.neurotec.util.NVersion;
import org.neurotecfinger.model.FingerprintEntity;
import org.neurotecfinger.repository.FingerprintRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ScannerService {

    @Autowired
    private NBiometricClient client;

    @Autowired
    private FingerprintRepository fingerprintRepository;

    private final AtomicBoolean currentWorking = new AtomicBoolean(false);

    @Async
    public CompletableFuture<Map<String, Object>> scanFinger(String fingerType) throws Exception {
        // 1. Check Lock (Matches AtomicBoolean logic)
        if (!currentWorking.compareAndSet(false, true)) {
            throw new RuntimeException("You are already trying to read a fingerprint, try after completing that one");
        }

        NSubject subject = new NSubject();
        NFinger finger = new NFinger();
        subject.getFingers().add(finger);

        try {
            // 2. Select Scanner
            selectScanner();

            // 3. Capture
            System.out.println("Capturing....");
            NBiometricStatus status = client.capture(subject);
            if (status != NBiometricStatus.OK) {
                throw new RuntimeException("Failed to capture: " + status);
            }

            // 4. Process Image (Save as PNG bytes -> BMPBase64 key)
            // Note: Old code writes "png" format but assigns it to a key called "BMPBase64". We preserve this behavior.
            NImage nImage = subject.getFingers().get(0).getImage();
            java.awt.Image awtImage = nImage.toImage();
            BufferedImage bufferedImage;
            if (awtImage instanceof BufferedImage) {
                bufferedImage = (BufferedImage) awtImage;
            } else {
                // Convert generic Image to BufferedImage
                bufferedImage = new BufferedImage(awtImage.getWidth(null), awtImage.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = bufferedImage.createGraphics();
                g.drawImage(awtImage, 0, 0, null);
                g.dispose();
            }
            ByteArrayOutputStream pngStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", pngStream);
            String imageEncoded = Base64.getEncoder().encodeToString(pngStream.toByteArray());
            System.out.println("Fingerprint image processed...");

            // WSQ
            WSQInfo info = (WSQInfo) NImageFormat.getWSQ().createInfo(nImage);
            info.setBitRate(WSQInfo.DEFAULT_BIT_RATE);
            NBuffer wsqBuffer = nImage.save(info);
            String templateEncoded = Base64.getEncoder().encodeToString(wsqBuffer.toByteArray());

            //Biometric Feature Extraction
            NBiometricTask extractionTask = client.createTask(
                    EnumSet.of(NBiometricOperation.CREATE_TEMPLATE, NBiometricOperation.ASSESS_QUALITY),
                    subject
            );
            client.performTask(extractionTask);
            String nativeTemplateBase64 = "";


            NBiometricTask task = client.createTask(EnumSet.of(NBiometricOperation.ASSESS_QUALITY), subject);
            client.performTask(task);
            if (extractionTask.getStatus() == NBiometricStatus.OK) {
                NBuffer templateBuffer = subject.getTemplateBuffer(); // This is the extracted feature set
                if (templateBuffer != null) {
                    nativeTemplateBase64 = Base64.getEncoder().encodeToString(templateBuffer.toByteArray());
                    System.out.println("Feature Extraction Successful.");
                }
            }
            else {
                System.err.println("Feature Extraction Failed: " + extractionTask.getStatus());
            }
            int nfiq = 0;
            if (task.getStatus() == NBiometricStatus.OK) {
                nfiq = subject.getFingers().get(0).getObjects().get(0).getNFIQ(new NVersion(1, 0));
                System.out.format("Finger NFIQ is: %s\n", nfiq);
            }

//            saveFingerprintData(fingerType, templateEncoded, imageEncoded, nativeTemplateBase64, nfiq);
            Map<String, Object> result = new HashMap<>();
            result.put("WSQImage", templateEncoded);
            result.put("BMPBase64", imageEncoded);
            result.put("NFIQ", nfiq);
            result.put("NativeTemplate", nativeTemplateBase64);
            System.out.println("NativeTemplate from scan finger:  " + nativeTemplateBase64);
            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            // Pass exception to controller
            throw e;
        } finally {
            subject.dispose();
            finger.dispose();
            currentWorking.set(false);
        }
    }

    public void cancelScanner() {
        client.cancel();
        currentWorking.set(false);
    }

    public boolean isScanning() {
        return currentWorking.get();
    }

    // New helper: extract template from raw image bytes and return Base64 template string
    public String extractTemplateFromImageBytes(byte[] imageBytes) throws Exception {
        if (imageBytes == null || imageBytes.length == 0) return "";
        NSubject subject = null;
        NFinger finger = null;
        NImage nImage = null;
        try {
            nImage = NImage.fromMemory(new NBuffer(imageBytes));
            subject = new NSubject();
            finger = new NFinger();
            finger.setImage(nImage);
            subject.getFingers().add(finger);

            NBiometricTask extractionTask = client.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);
            client.performTask(extractionTask);
            if (extractionTask.getStatus() == NBiometricStatus.OK) {
                NBuffer templateBuffer = subject.getTemplateBuffer();
                if (templateBuffer != null) {
                    return Base64.getEncoder().encodeToString(templateBuffer.toByteArray());
                }
            } else {
                System.err.println("Template extraction failed: " + extractionTask.getStatus());
            }
            return "";
        } finally {
            if (subject != null) subject.dispose();
            if (finger != null) finger.dispose();
            if (nImage != null) nImage.dispose();
        }
    }

    private String extractTemplateFromSubject(NSubject subject) {
        NBiometricTask extractionTask = client.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE, NBiometricOperation.ASSESS_QUALITY), subject);
        client.performTask(extractionTask);
        if (extractionTask.getStatus() == NBiometricStatus.OK) {
            NBuffer buffer = subject.getTemplateBuffer();
            if (buffer != null) {
                return Base64.getEncoder().encodeToString(buffer.toByteArray());
            }
        }
        return null;
    }

    /**
     * SERVICE METHOD 2: IDENTIFY WITH DATABASE
     * Matches the probe template against all records in Postgres.
     */
    public Map<String, Object> identify(String probeTemplateBase64) {
        Map<String, Object> response = new HashMap<>();
        response.put("matchFound", false);
        System.out.println("Here at the identify service method");
        if (probeTemplateBase64 == null) return response;

        try {
            // 1. Prepare Probe (The scanned finger)
            NSubject probe = new NSubject();
            probe.setTemplateBuffer(new NBuffer(Base64.getDecoder().decode(probeTemplateBase64)));

            // 2. Prepare Gallery (Load ALL from DB)
            List<FingerprintEntity> dbRecords = fingerprintRepository.findAll();
            if (dbRecords.isEmpty()) {
                response.put("error", "Database is empty.");
                return response;
            }

            // 3. Enroll DB records into Matcher Engine
            NBiometricTask enrollTask = client.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
            for (FingerprintEntity record : dbRecords) {
                if (record.getNativeTemplate() != null) {
                    NSubject candidate = new NSubject();
                    candidate.setId(String.valueOf(record.getId())); // ID is key
                    candidate.setTemplateBuffer(new NBuffer(Base64.getDecoder().decode(record.getNativeTemplate())));
                    enrollTask.getSubjects().add(candidate);
                }
            }
            client.performTask(enrollTask);

            // 4. Match
            NBiometricTask identifyTask = client.createTask(EnumSet.of(NBiometricOperation.IDENTIFY), probe);
            client.performTask(identifyTask);

            // 5. Result
            if (identifyTask.getStatus() == NBiometricStatus.OK) {
                System.out.println("Identification successful, checking results..."+"\n" +
                        "Status: " + identifyTask.getStatus()+"\n" +
                        "Matching results count: " + probe.getMatchingResults().size());
                for (NMatchingResult result : probe.getMatchingResults()) {
                    response.put("matchFound", true);
                    response.put("suspectId", result.getId());
                    response.put("score", result.getScore());

                    // Fetch full details to show name/finger type
                    Optional<FingerprintEntity> entity = fingerprintRepository.findById(Long.valueOf(result.getId()));
                    entity.ifPresent(e -> {
                        response.put("fingerType", e.getFingerType());
                        response.put("originalQuality", e.getQuality());
                    });
                    break; // Return best match only
                }
            }
            else{
                System.out.println("Nbiometrics status not OK: " + identifyTask.getStatus());
            }
            client.clear(); // Clear memory

        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", e.getMessage());
        }
        return response;
    }
    /**
     * Ports logic for /getWsqFromBmp
     */
    public String convertBmpToWsq(MultipartFile file) throws IOException {
        // NBuffer.fromBinary(...) does not exist in this SDK; use the byte[] constructor
        NImage nImage = NImage.fromMemory(new NBuffer(file.getBytes()));
        WSQInfo info = (WSQInfo) NImageFormat.getWSQ().createInfo(nImage);
        info.setBitRate(WSQInfo.DEFAULT_BIT_RATE);
        NBuffer wsqBuffer = nImage.save(info);
        return Base64.getEncoder().encodeToString(wsqBuffer.toByteArray());
    }

    private void selectScanner() throws Exception {
        NDeviceManager deviceManager = client.getDeviceManager();
        deviceManager.setDeviceTypes(EnumSet.of(NDeviceType.FINGER_SCANNER));
        deviceManager.initialize();

        if (deviceManager.getDevices().isEmpty()) {
            throw new RuntimeException("There are no devices connected to read the fingerprint"); // Matches old 500 error message
        }
        // Auto-select first device
        client.setFingerScanner((NFScanner) deviceManager.getDevices().get(0));
    }
    public void saveFingerprintData(String fingerType, String wsq, String bmp, String nativeTemplate, int quality) {
        FingerprintEntity entity = new FingerprintEntity();
        entity.setFingerType(fingerType != null ? fingerType : "");
        entity.setWsqData(wsq != null ? wsq : "");
        entity.setBmpBase64(bmp != null ? bmp : "");
        // Ensure nativeTemplate is not null to satisfy DB not-null constraint
        entity.setNativeTemplate(nativeTemplate != null ? nativeTemplate : "");
        entity.setQuality(quality);
        fingerprintRepository.save(entity);
    }
}
