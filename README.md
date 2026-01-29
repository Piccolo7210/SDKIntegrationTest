NeurotecFingerprint — Setup & Build Instructions

Overview
--------
This repository contains a Spring Boot sample that integrates with the Neurotechnology biometric SDK to capture and identify fingerprints. Sensitive SDK runtime files (native libraries, proprietary JARs and license files) are not included in this repository and must be obtained separately from Neurotechnology.

This README explains how to prepare your machine, add the Neurotec SDK files, and build/run the sample locally. It also includes a sanitized demo Gradle file (demo-build.gradle) so you can share the repo without publishing local paths or SDK files.

Important security note
-----------------------
You must NOT commit the following folders to version control because they contain proprietary SDK artifacts and/or license material:
- `Bin/` (contains Neurotec JARs and native libraries)
- `Licenses/` (license keys, trial files)
- `utils/` (helper libs distributed with the SDK)
- `buildCommon/` (local build scripts that may contain private paths)

This repository already ignores these folders in `.gitignore`. When you clone this repo, create those folders locally and copy the SDK files into them as described below.

Obtain Neurotechnology SDK
--------------------------
1. Download the Neurotechnology Megamatcher SDK (the same major version used in the project) from Neurotechnology's site or your organization's distribution portal. You'll need valid entitlement/license credentials.
2. From the SDK package extract the following artifacts into the project structure described below:
   - JARs: place required JAR files into `Bin/Java/` (e.g. `neurotec-core.jar`, `neurotec-biometrics.jar`, `neurotec-biometrics-client.jar`, `neurotec-licensing.jar`, `neurotec-devices.jar`, `jna.jar`, etc.)
   - Native libraries: place native binaries for your platform into `Bin/Win64_x64` (Windows x64) or equivalent subfolder for your OS (e.g. `Bin/Linux_x64`) and make sure `java.library.path` points there when running.
   - License files: if you use local license files, put them into `Licenses/` or configure the license server details per Neurotechnology docs.

Trial version note
------------------
This project was developed and tested using the Neurotechnology SDK trial/evaluation package (a local trial license such as `TrialFlag.txt` was used during development). Trial licenses are intended for evaluation and may impose limitations (for example: time-limited evaluation, restricted number of enrollments, or reduced features). The sample in this repository may therefore behave differently under a full production license.

Note from the author: I used the Neurotechnology trial license to test and validate the SDK while developing this sample. Keep in mind trial licenses may limit features or the number of enrollments; for production use obtain a proper commercial license and update the `Licenses/` folder or license server settings accordingly.

If you are using trial license files for development, keep them in the `Licenses/` folder locally and do NOT commit them to source control. For production or deployment, obtain and configure a proper Neurotechnology production license according to their documentation.

Folder layout (what the sample expects)
--------------------------------------
Put the SDK artifacts into the repo root (local only, not committed):

```
Bin/Java/                 <- Neurotec JARs (neurotec-core.jar, neurotec-biometrics.jar, ...)
Bin/Win64_x64/            <- native DLL/.so files for runtime (used by JNA)
Licenses/                 <- license files (TrialFlag.txt etc.)
utils/                    <- helper jars
```

Sanitized demo Gradle file
--------------------------
A demo build file `demo-build.gradle` has been provided in the project root. It contains placeholders instead of absolute, personal paths. Use it as a template – copy or rename it to `build.gradle` and fill in the SDK paths and any other local settings before building.

Key build/run prerequisites
---------------------------
- Java Development Kit (JDK) 17 installed. The project config uses a Java toolchain set to 17.
- Gradle wrapper is included; use the wrapper to build: `./gradlew` (Windows: `gradlew.bat`).
- PostgreSQL (or your configured DB) available and `application.properties` set with DB connection URL/credentials if you plan to persist templates.
- Neurotec licenses properly configured (local license files or license server). The sample calls `NLicense.obtain(...)` — update the parameters as appropriate.

Basic local setup and build (Windows PowerShell)
------------------------------------------------
1) Clone the repository:

```powershell
git clone <your-repo-url>
cd NeurotecFingerprint
```

2) Create the private SDK folders and copy SDK files (do NOT commit them):

```powershell
mkdir Bin\Java
mkdir Bin\Win64_x64
mkdir Licenses
# Copy neurotec jars into Bin\Java and native libs into Bin\Win64_x64
```

3) Verify Java 17 is available and set as JAVA_HOME if needed:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
# or ensure your IDE/terminal uses JDK 17
```

4) Build using the Gradle wrapper (uses the toolchain configured in the provided Gradle file):

```powershell
.\gradlew.bat clean build
```

5) Run the app (example, dev):

```powershell
.\gradlew.bat bootRun
```

Configuring native library path
-------------------------------
The sample uses JNA and native Neurotec libraries. There are two ways to make the native DLLs available at runtime:
- Copy the native libraries (DLLs/so) into `Bin/Win64_x64` and configure `bootRun` system properties in `build.gradle` that point `java.library.path` and `jna.library.path` to that folder.
- Or add the folder to the system PATH (Windows) or LD_LIBRARY_PATH (Linux) before starting the JVM.

Database and schema
-------------------
- The project uses Spring Data JPA. Configure your `src/main/resources/application.properties` with a datasource URL, username, and password. Example:

```
spring.datasource.url=jdbc:postgresql://localhost:5432/neurotec
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
```

- Note: If large Base64 templates are stored, we use `text` columns in the entity mapping. If you previously had columns mapped to PostgreSQL large objects (OID), you may see numeric OIDs in the table; follow the README section earlier (migration) to convert them to text.

Troubleshooting
---------------
- java.lang.NoSuchFieldError (JCTree internal field missing): This is usually caused by a mismatch between the JDK used to run Gradle and the annotation processors (e.g. Lombok). Use the Gradle toolchain (already present in the sample) to compile with JDK 17, or update Lombok to a version compatible with your JDK.
- Licensing errors such as `Product 'Biometrics.FingerExtraction' is unknown`: Ensure the required Neurotec licenses are available and obtained at runtime (check `NLicense.obtain(...)` arguments).
- If the placeholder image or static resources do not load, make sure the images are placed in `src/main/resources/images` or use the provided `WebMvcConfig` which maps `/images/**` to classpath resources.

Sharing the repo safely
-----------------------
- Keep `Bin/`, `Licenses/`, `utils/`, and `buildCommon/` out of Git.
- Use `demo-build.gradle` (provided) when showing others how to build. Do not publish your local `build.gradle` with absolute personal paths or credentials.

Files created for you
---------------------
- `demo-build.gradle` — sanitized Gradle build file template (placed in project root)
- `README.md` — this file

If you want, I can also:
- create an `application.properties.example` with placeholder DB values,
- produce a one-shot Java migration tool to convert OID LOs to `text` columns,
- or produce a proper `favicon.ico` file into static resources.

License and third-party packages
-------------------------------
- This sample references Neurotechnology SDK artifacts which are under Neurotechnology's license agreements — distribute those artifacts only under the terms you have from Neurotechnology.
- Lombok and other OSS libs are used under their respective licenses.

---
Last updated: 2026-01-29
