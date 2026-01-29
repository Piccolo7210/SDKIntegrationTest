package org.neurotecfinger.config;

import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.licensing.NLicense;
import com.neurotec.licensing.NLicenseManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@Configuration
public class BiometricConfig {

    @Bean
    public NBiometricClient biometricClient() throws IOException {

        NLicenseManager.setTrialMode(true);
        String[] licenses = { "FingerClient", "FingerMatcher" };
        for (String license : licenses) {
            try {
                boolean obtained = NLicense.obtain("/local", "5000", license);
                if (obtained) {
                    System.out.println("Obtained License: " + license);
                } else {
                    System.err.println("Failed to obtain License: " + license);
                    System.err.println(" Please ensure 'ActivationWizard.exe' has been run.");
                }
            } catch (IOException e) {
                System.err.println("IO Error obtaining license: " + license);
                e.printStackTrace();
            }
        }

        // 3. Initialize Client
        NBiometricClient client = new NBiometricClient();
        client.setUseDeviceManager(true);
        client.setFingersCalculateNFIQ(true);
        client.setMatchingThreshold(50);
        // client.setFingersCalculateNFIQ2(true); // Uncomment if you need NFIQ2.0 (slower but newer)

        return client;
    }
}