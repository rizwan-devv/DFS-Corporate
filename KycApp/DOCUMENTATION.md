# KycApp — Complete Technical Reference

This document is a full technical walkthrough of the KycApp Android project: what every file does, how the original Python biometric backend was ported to on-device Kotlin, and the specific reasoning behind the fingerprint pipeline and every non-obvious design decision. It's written so you can read it once and then work on the code by hand — editing thresholds, adding features, or debugging new issues — without needing anyone to re-explain the architecture.

## Table of Contents

1. [What this app is](#1-what-this-app-is)
2. [How the Python → Kotlin migration actually worked](#2-how-the-python--kotlin-migration-actually-worked)
3. [The debugging journey — real bugs found this session](#3-the-debugging-journey--real-bugs-found-this-session-and-how-they-were-diagnosed)
4. [Data Layer](#4-data-layer-comexamplekycappdata) — Room, SQLCipher encryption, repositories
5. [Face & Liveness](#5-face--liveness-comexamplekycappbiometricsface-liveness) — embedding, alignment, blink detection
6. [UI / Navigation Layer & the Shared Camera Screen](#6-ui--navigation-layer-and-the-shared-camera-screen)
7. [Fingerprint Biometrics](#7-fingerprint-biometrics-biometricsfingerprint-and-the-vendored-wsq-codec-orgjnbis) & the vendored WSQ codec
8. [OCR Pipeline](#8-ocr-pipeline-biometricsocr) — CNIC field extraction

**How to use this document:** it's organized by package, in the same order the app's data actually flows (camera capture → biometric processing → storage). Read top to bottom the first time; after that, use it as a reference — jump to the section for whichever file you're editing. Every function is documented with *what it does* and, where the code itself doesn't make it obvious, *why it's shaped that way*.

---

## 1. What this app is

KycApp is a standalone Android KYC (Know Your Customer) app that performs four kinds of identity verification **entirely on-device**, with no server calls:

1. **ID Card Detection** — points the camera at a Pakistani CNIC (national ID card) and confirms it's actually a CNIC.
2. **OCR** — reads the CNIC's six printed fields (Name, Father Name, Identity Number, Date of Birth, Date of Issue, Date of Expiry) and crops out the printed photo.
3. **Face Matching** — compares a live selfie against the photo OCR already extracted from the card, using liveness (blink) detection to make sure it's a real person, not a photo of a photo.
4. **Fingerprint Matching** — enrolls and later verifies a person's hand print (all four fingers photographed together, NADRA-style), stored as ORB/AKAZE feature templates plus three export formats (WSQ, Base64, ISO 19794-4).

It began as a port of an existing Python backend (`Enhanced Version 2 - Copy`, a Streamlit app using OpenCV, InsightFace, MediaPipe, and Tesseract, calling into a FastAPI-style backend) that the Android app originally talked to over HTTP. Over the course of development, all four pipelines were migrated to run natively on the phone, one at a time, in three phases:

- **Phase 1** — Fingerprint enroll/verify (pure OpenCV, ports closely) + blink liveness (MediaPipe, same model file) + an encrypted local database to replace the backend's unencrypted pickle files.
- **Phase 2** — ID card detection (TFLite/LiteRT classifier) + face embedding (ONNX Runtime Mobile, InsightFace model converted to FP16).
- **Phase 3** — OCR (Tesseract via `tess-two`/`Tesseract4Android`, the largest and most intricate port, since the Python version's field-extraction logic is a long chain of regex/heuristic rules).

By the end of this session, the app has zero dependency on the original Python backend or any network call — every pipeline in the list above runs fully offline on the phone.

---

## 2. How the Python → Kotlin migration actually worked

This wasn't a rewrite from scratch — it was a **faithful, function-by-function port**. The guiding rule throughout: match the Python module's logic 1:1 (same thresholds, same algorithm steps, same edge-case handling) unless there was a concrete, device-specific reason to diverge. You'll see this reflected directly in the code — most files' doc comments say things like *"ports mod_ocr.py's `_read_text()`"* or *"mirrors mod_fingerprint.py's `_next_sample_key`"*, and many numeric constants (thresholds, weights, padding ratios) are copied verbatim from the Python source rather than re-tuned, specifically so the on-device behavior would match what was already validated on the Python side.

Where a direct port wasn't possible, one of these substitution patterns was used instead:

| Python / server-side piece | Android replacement | Why |
|---|---|---|
| InsightFace / RetinaFace (face detection + embedding) | MediaPipe FaceLandmarker (detection/landmarks) + ONNX Runtime Mobile running the same InsightFace w600k_r50 embedding model, converted to FP16 | InsightFace's own Python detector has no direct Android build; MediaPipe's Tasks Vision library ships a ready-made on-device face landmarker, and the *embedding* model itself (the actual face-recognition network) could still be run as-is via ONNX Runtime, just re-exported to FP16 for mobile size/speed. FP16 conversion was validated to produce 1.0000 cosine similarity against the original FP32 outputs before being trusted. |
| InsightFace's 5-point face detector box | MediaPipe's 478-point face mesh, reduced to 5 ArcFace-template points (`extractFivePoints()`) | Same effective alignment input, computed from a different (but already on-device) landmark source. |
| pytesseract (Python's Tesseract CLI/TSV wrapper) | Tesseract4Android's native `TessBaseAPI`, called directly | Avoids shelling out to a CLI; the native API's `ResultIterator` gives the same per-line text/confidence/bounding-box data pytesseract's `image_to_data` TSV output does, just without the CLI round-trip. |
| Python's `difflib.SequenceMatcher.ratio()` (used throughout the OCR name-matching code) | A hand-written Kotlin port (`stringSimilarity()` in `NameExtraction.kt`) | Kotlin/Java has no equivalent in the standard library, so the exact same greedy-longest-matching-block algorithm was re-implemented and numerically verified against real Python output to confirm it produces identical similarity scores. |
| TensorFlow Lite (`org.tensorflow:tensorflow-lite`) for the ID card classifier | Google's LiteRT (`com.google.ai.edge.litert`) | Same `org.tensorflow.lite.Interpreter` API (LiteRT is TFLite's rebrand/successor), swapped in purely because the old TFLite artifact never shipped a 16KB-page-aligned native library for x86_64, which Google Play now requires (Nov 2025+). Zero application code changed — it was a dependency-only swap. |
| Server-side pickle template storage, no encryption | Room (SQLite) + SQLCipher, with the passphrase itself wrapped by an Android Keystore AES-GCM key | The original backend stored fingerprint templates as plain pickle files with no access control; since storage had to be rebuilt for on-device use anyway, real encryption-at-rest was added at the same time. See the Data Layer section for exactly how this works. |
| A WSQ-encoding Python library (`wsq`, wrapping NIST's NBIS C code) | A vendored, from-source Kotlin/Java port of a WSQ encoder/decoder (`org/jnbis/*.java`) | No ready-made Android-compatible WSQ encoder exists on Maven Central (the one that does, `jnbis`, is AWT-dependent and decode-only). A specific historical fork (a JMRTD project's `wsq_imageio` module) was found to contain a complete encoder with zero AWT/ImageIO imports, confirmed Android-safe, and vendored directly into the project. See the Fingerprint section for details. |

### Porting fidelity in practice — a concrete example

The clearest illustration of "port, don't redesign" is the OCR field-extraction logic in `CnicFieldExtractor.kt`. Its doc comment literally says *"ports mod_ocr.py's `_extract_cnic_info()` faithfully... this is a 1:1 translation, not a redesign."* Multiple stages of that function only exist because they fixed a *specific, real bug* the Python version's author had already hit and documented — e.g. the reason names fall back to *any* syntactically-valid cluster (not just ones confirmed by two separate OCR reads) is a direct quote from the Python comment: *"`scan_single_image` (the phone-photo, single-shot entry point) has no second capture cycle to fall back on."* When this session found and fixed new bugs in that same logic (see §3 below), the fixes were written in the same spirit — narrow, evidence-driven corrections layered onto the existing rule chain, not a rewrite of it.

---

## 3. The debugging journey — real bugs found this session, and how they were diagnosed

Everything below happened *after* the three migration phases were functionally complete and testing moved from the Android emulator (synthetic camera images) to a real phone (Samsung Galaxy A56). This distinction matters: several serious bugs were completely invisible on the emulator and only appeared once real camera hardware, real lighting, and real EXIF metadata were involved. The methodology that found each one was consistent: **when a real-device bug can't be reasoned about from the code alone, add targeted `Log.d`/`Log.w` calls at each pipeline stage, rebuild, install, have the bug reproduced, then read the actual `adb logcat` output** rather than guessing. Several of the fixes below were found *only* because a screenshot or a log line revealed something the code review alone would never have caught.

### 3.1 — 16KB page-size compatibility (native library alignment)

**Symptom:** Android showed a compatibility warning listing several native `.so` libraries as not 16KB-page-aligned (a Google Play requirement from Nov 2025 onward).

**Diagnosis:** The warning dialog's own list was misleading — it was truncated before showing the actual offending libraries. The APK was extracted and every native library under `lib/arm64-v8a/` and `lib/x86_64/` was checked directly with the NDK's `llvm-readelf -lW` (inspecting the ELF program headers' alignment field). This found the real culprits were `libopencv_java4.so` and its bundled `libc++_shared.so` — nothing the warning dialog had actually named.

**Fix:** `org.opencv:opencv` bumped `4.10.0 → 4.14.0` (ships 16KB-aligned natives), and the ID-card classifier's TensorFlow Lite dependency was swapped to LiteRT (`com.google.ai.edge.litert:litert:2.2.0`) for the same reason on x86_64 — this required a `compileSdk` bump from 34 to 36 (LiteRT's transitive deps need API 35+; `minSdk`/`targetSdk` were left unchanged at 24/34). Verified afterward by re-running `llvm-readelf` on all 16 native libraries across both ABIs and confirming every one was now 16KB-aligned.

### 3.2 — "Not an ID card" on real photos (EXIF rotation)

**Symptom:** ID Card detection and OCR both failed on real captured photos with "Not an ID card," despite working fine on the emulator.

**Diagnosis:** `BitmapFactory.decodeFile()` ignores EXIF orientation tags. A real phone's camera (via CameraX's `ImageCapture`) writes JPEGs in the sensor's raw orientation plus a separate EXIF rotation tag saying how to display it upright — the emulator's synthetic camera never produces rotated images, so this was invisible until real hardware was used.

**Fix:** `applyExifRotation()` was added to the shared `fileToMat()` helper in `CameraCaptureScreen.kt`, reading the JPEG's `ExifInterface.TAG_ORIENTATION` and applying the matching `Matrix` rotation/flip before any further processing. Because `fileToMat()` is the single shared entry point every capture mode uses to turn a captured file into an OpenCV `Mat`, this one fix corrected orientation for ID Card, OCR, Face Matching, and Fingerprint all at once.

### 3.3 — Face Matching: three separate rounds of "still not working"

This was the hardest bug to run down, because each fix addressed a real problem but wasn't the *whole* problem — it took three iterations, each backed by new log/screenshot evidence, to actually resolve.

**Round 1 — wrong detection scope.** The face-match "Capture ID Photo" step ran MediaPipe's face detector directly on the *entire* perspective-corrected card image (roughly 1800×2400px). Logs showed `detect() on 1800x2400 card returned 0 face(s)` repeatedly — hunting for a small printed photo inside a huge frame is a genuinely hard detection problem for a lightweight mobile detector. Fix: `CnicIdPhotoExtractor.kt`'s `extractIdPhoto()` was rewritten to try a cascade of progressively narrower candidate regions (a tight, empirically-tuned window first, then quadrants, then the full card as a last resort) instead of searching the whole card at once.

**Round 2 — architectural redundancy.** Face Matching still ran its *own* separate ID-card capture+detect step, duplicating (and less reliably than) what OCR's `scanSingleImage()` already did successfully. This was resolved by realizing the original Python backend never had this redundancy either — it always ran OCR once, saved the extracted photo, and any later face-verification pass reused that saved photo (`mod_face.py`'s `latest_id_photo_path()`). The Android app was rearchitected to match: a new `IdCardPhotoEntity`/`IdCardPhotoDao`/`IdCardPhotoRepository` table stores the photo OCR extracts, and Face Matching now loads it directly instead of capturing its own — eliminating the redundant, less-reliable capture step entirely.

**Round 3 — color corruption in the live capture.** Even after Round 2, matches kept failing with strongly *negative* similarity scores (not just "below threshold" — actively anti-correlated, which doesn't happen for two photos of the same real person even under bad conditions). A screenshot of the "View Compared Faces" dialog (added specifically to make this debuggable) revealed the live capture had visible red/green/blue channel fringing — a corrupted image, not a bad photo. Root cause: the live embedding code was re-extracting a `Bitmap` from the MediaPipe `MPImage` object via `BitmapExtractor.extract()`, and that specific round-trip was coming back corrupted on this device. Fix: a direct reference to the original, clean camera-frame `Bitmap` is now kept in a small relay variable and used for the embedding instead of re-extracting one from MediaPipe. This also explained why the *separate* standalone blink-liveness test screen had seemed to work fine all along — it only ever reads landmark *positions* from the MediaPipe result, never the pixel colors, so the corruption was invisible to it. (That standalone test screen has since been removed, since Face Matching's own liveness step now covers the same ground.)

**Round 4 (liveness-specific) — a false blink from a single noisy frame.** Separately, Face Matching would sometimes report "NO MATCH" almost instantly after pressing the verify button — far too fast for a real blink. `BlinkLivenessChecker.kt`'s state machine flagged "eyes closed" the moment a single video frame's Eye-Aspect-Ratio dipped below threshold, which can happen from ordinary landmark jitter, not an actual blink. Fix: the eyes must now register as closed for at least **two consecutive frames** (`MIN_CONSECUTIVE_CLOSED_FRAMES`) before it counts, filtering out one-frame noise while still reliably catching real blinks (which naturally span several frames).

### 3.4 — OCR field-extraction bugs

Several rounds of real-CNIC testing surfaced genuine logic bugs in `CnicFieldExtractor.kt`'s heuristic field-matching (as opposed to OCR *reading* accuracy, which is inherently noisy and not something code can fully fix):

- **Date of Issue / Date of Expiry occasionally swapped or both dropped.** The "Date of Issue" and "Date of Expiry" labels print side-by-side on one row on a real CNIC, with their date values also side-by-side on the row below — but the original matching rule only checked *vertical* adjacency in an OCR-sorted line list, with no concept of left vs. right column, so it could pair a value with the wrong same-row label depending on OCR line-ordering noise. When that produced `issue == expiry`, a safety check caught the contradiction and deleted *both* fields. Fix: a separate, more reliable signal — two dates that are exactly N years apart (5/7/10/15/20), which a CNIC's validity term always is — now runs *before* the fragile position-based check, and the position check only kicks in as a fallback when no valid date pair exists at all.
- **Father Name sometimes silently dropped even though it was read correctly.** The name-matching logic required a name to be read by at least two independent OCR passes ("corroboration") before trusting it — but if only one pass caught the father's name clearly that round, it was excluded from consideration entirely, even when it sat directly under a correctly-recognized "Father Name" label. Fix: single-read names are now eligible too (with a real confidence floor, so pure noise still can't win), with corroborated reads still preferred when there's genuine competition for the same slot.
- **A truncated OCR fragment beating the full correct name.** Widening the matching pool (previous fix) introduced a new failure mode: a partial misread like "Eem Akhtar" could out-compete the correct, complete "Muhammad Nadeem Akhtar" for the same slot purely by being a few hundredths closer in vertical position. Fix: any candidate that is a plain substring of a longer, more complete candidate is now dropped in favor of the fuller one, and OCR confidence was added as a tiebreaker for cases that aren't simple substrings.
- **Name and Father Name assigned backwards.** Because label-line Y-positions are themselves noisy (multiple OCR variants can detect the same physical label at slightly different Y coordinates), it was possible for the holder's name and the father's name to get assigned to each other's fields. Fix: since the holder's row always prints above the father's row on a real CNIC, the final assignment is checked against that ordering and swapped back if it comes out inverted.
- **Identity Number occasionally reading as a completely different (wrong) number.** This one has no logic fix — it's genuine single-shot OCR misread risk on small printed digits, and the two independent digit-reading passes (a general text pass and a digit-only whitelist re-OCR pass) can occasionally disagree while both still producing a syntactically valid-looking 13-digit number. Rather than pretend certainty it doesn't have, the app now tracks which fields came from a single unconfirmed read vs. two independent reads agreeing (`OcrResult.lowConfidenceFields`) — this data is still computed internally even though the UI no longer displays a "please double-check" label (removed per a later request), so it remains available if you want to surface it differently in the future.

### 3.5 — OCR speed

Each OCR capture was taking roughly 30–36 seconds — five separate Tesseract recognition passes (three image variants × one or two page-segmentation-mode configs each) run one after another on a single shared `TessBaseAPI` instance, with the slowest single pass alone eating ~13 seconds. The Python original deliberately includes all five passes for maximum text recall (a line missed by one binarization is often caught by another) — removing any of them was rejected as too risky to field accuracy. Instead, the fix left every pass exactly as-is but ran them **concurrently**, each on its own short-lived `TesseractEngine` instance (`readText()` in `OcrReader.kt` now uses `coroutineScope { passes.map { async(Dispatchers.Default) { ... } } }.awaitAll()`), cutting wall-clock time from the sum of all five passes down toward roughly the slowest single pass's time, with zero change to what actually gets recognized.

---


---

## 4. Data Layer (`com.example.kycapp.data`)

The `data` package is the app's persistence layer: a single encrypted Room/SQLCipher database (`AppDatabase`), two entity/DAO/repository trios (fingerprint templates, ID card photo), and a `crypto` sub-package that manages the database's encryption passphrase. Nothing in this package touches OpenCV matching logic directly except for BLOB conversion — that logic lives in `biometrics/fingerprint` and is only *called* from here.

### `AppDatabase.kt`

**What it is.** The Room database definition and app-wide singleton accessor. It is the single entry point every other file uses to reach either DAO.

**Declarations**

- `AppDatabase` (`AppDatabase.kt:15`) — an `abstract class` extending `RoomDatabase`, annotated `@Database(entities = [FingerprintTemplateEntity::class, IdCardPhotoEntity::class], version = 3, exportSchema = false)`. `exportSchema = false` means Room does not write JSON schema history files to the project (normally used for migration testing) — consistent with the fact that there is no real migration path yet (see below).
  - `fingerprintTemplateDao(): FingerprintTemplateDao` and `idCardPhotoDao(): IdCardPhotoDao` — abstract accessor methods; Room code-generates the implementations at compile time.
- `companion object`:
  - `instance: AppDatabase?` — a `@Volatile` nullable backing field for a classic double-checked-locking singleton.
  - `getInstance(context: Context): AppDatabase` (`AppDatabase.kt:23`) — returns the existing singleton if present; otherwise synchronizes on the companion object and builds it once. Double-checked locking avoids taking the lock on every call once the instance exists, while still being thread-safe for the first concurrent callers.
  - `build(context: Context): AppDatabase` (private, `AppDatabase.kt:28`) — does the actual construction:
    1. Calls `DbPassphraseProvider.getOrCreate(context)` to obtain the raw 32-byte SQLCipher passphrase.
    2. Wraps it in a `SupportOpenHelperFactory(passphrase)` from the `net.zetetic:sqlcipher-android` library (version `4.13.0`) — this is the factory that makes Room open the database through SQLCipher instead of the stock Android `SQLiteOpenHelper`.
    3. Builds via `Room.databaseBuilder(context, AppDatabase::class.java, "kyc.db")`, attaching the factory with `.openHelperFactory(factory)`.
    4. Calls `.fallbackToDestructiveMigration(true)` — **this is a deliberate, commented-on gotcha**: the code comment states the app is still under active on-device testing and no migration path exists yet for the export columns added in version 2/3 — so any schema version bump simply **wipes all existing enrollments** rather than migrating them. Anyone bumping `version` needs to either write a real `Migration` or accept that upgrading the app on a device with existing data deletes it.

**Callers/dependents.** `AppDatabase.getInstance(context)` is called from `camera/CameraCaptureScreen.kt` (to build both `FingerprintTemplateRepository` and `IdCardPhotoRepository`) and from `ui/screens/FingerprintRecordViewerScreen.kt` (to build a `FingerprintTemplateRepository` for the record viewer). Both DAOs are only ever invoked through this singleton — nothing constructs `AppDatabase` any other way, so there is exactly one open database connection per process.

**Gotchas**
- The destructive-migration comment above is the single most important thing to know before touching `version` or the entity schemas.
- Because `getInstance` is a plain object singleton (not scoped to `Application`), the database connection lives for the process lifetime; there's no explicit `close()` call anywhere in the codebase.

---

### `FingerprintTemplateEntity.kt`

**What it is.** The Room entity (table row) for one enrolled fingerprint sample. Represents a single capture of one hand for one person, at one "slot."

**Declarations**

- `FingerprintTemplateEntity` (`:20`), a `data class` annotated `@Entity(tableName = "fingerprint_templates", indices = [Index(value = ["personName", "hand", "sampleSlot"], unique = true)])`. The unique composite index is what enforces "at most one row per (person, hand, slot)" at the database level — combined with `OnConflictStrategy.REPLACE` on insert (see DAO below), this is what makes slot-based overwriting atomic and race-safe.
- Fields:
  - `id: Long` — `@PrimaryKey(autoGenerate = true)`, the Room row ID.
  - `personName: String`, `hand: String` — identify whose sample this is and which hand ("LEFT"/"RIGHT").
  - `sampleSlot: Int` — which of up to `MAX_ENROLL_SAMPLES` (3, defined in `FingerprintQualityGates.kt:12`) rotating slots this sample occupies for this person/hand pair.
  - `orbKeypoints: ByteArray`, `orbDescriptors: ByteArray`, `orbDescriptorRows: Int`, `orbDescriptorCols: Int`, `orbDescriptorType: Int` — the ORB feature set, flattened for BLOB storage. OpenCV's `Mat` (descriptor matrix) can't be stored directly, so `orbDescriptors` holds the raw pixel/byte buffer while rows/cols/type are stored alongside so the `Mat` can be reconstructed exactly (`Mat(rows, cols, type)` then `.put()`). `orbKeypoints` is a custom flat binary encoding (see `MatSerialization.kt`), not a `Mat`.
  - `akazeKeypoints`, `akazeDescriptors`, `akazeDescriptorRows`, `akazeDescriptorCols`, `akazeDescriptorType` — the same shape, for the AKAZE feature set. Two independent feature extractors are stored per sample because the matcher (`FingerprintMatcher.kt`) combines ORB and AKAZE scores with fixed weights (`ORB_WEIGHT = 0.45`, `AKAZE_WEIGHT = 0.55`).
  - `orbFeatureCount: Int`, `akazeFeatureCount: Int` — cached counts of detected keypoints/descriptor rows, for quick display/diagnostics without deserializing the BLOBs.
  - `sharpness: Float` — the sharpness score computed at capture time, stored for the record viewer.
  - `createdAt: Long` — capture timestamp (epoch millis); also used as the tie-breaker for round-robin slot eviction (see repository below).
  - `wsqExport: ByteArray = ByteArray(0)`, `base64Export: String = ""`, `isoExport: ByteArray = ByteArray(0)` — **export-only** copies of the enrolled fingerprint image in three interchange formats (WSQ, Base64-encoded PNG, ISO 19794-4-inspired record), mirroring the Python reference implementation's `export_fingerprint_formats()`. The doc comment is explicit that these are **never used for matching** — only the ORB/AKAZE fields are — they exist purely so the record-viewer screen can display "what was enrolled" in standard formats.

**Callers/dependents.** Constructed by `FingerprintTemplateRepository.saveSample()`; read back by `FingerprintTemplateRepository` (converted to `FingerprintTemplate` for matching) and directly by `FingerprintRecordViewerScreen.kt` (for display/deletion) and `FingerprintEnrollmentService.kt`.

**Gotchas**
- The class comment explicitly says the round-robin overwrite behavior "mirrors `mod_fingerprint.py`'s `_next_sample_key` behavior" — behavioral parity with that Python module is a design constraint.
- `ByteArray` fields make `equals()`/`hashCode()` on this data class compare by array reference, not contents (Kotlin doesn't auto-generate `contentEquals`-based comparison) — worth knowing if this entity is ever put in a `Set` or `==`-compared.

---

### `FingerprintTemplateDao.kt`

**What it is.** The Room DAO interface for the `fingerprint_templates` table — pure SQL/Room glue, no logic.

**Declarations** (all `suspend`, all in `@Dao interface FingerprintTemplateDao`):

- `upsert(entity: FingerprintTemplateEntity)` — `@Insert(onConflict = OnConflictStrategy.REPLACE)`. Because of the unique index on `(personName, hand, sampleSlot)`, inserting a row whose triple matches an existing row causes Room/SQLite to replace (delete + reinsert) rather than throw a constraint violation. This is the mechanism that makes round-robin slot overwriting work: the repository picks a `sampleSlot` and this call blindly "upserts" into it.
- `delete(entity: FingerprintTemplateEntity)` — `@Delete`, matches by primary key.
- `getSamples(personName: String, hand: String): List<FingerprintTemplateEntity>` — `SELECT * FROM fingerprint_templates WHERE personName = :personName AND hand = :hand`. Used both to fetch templates for matching and to compute the next round-robin slot.
- `getAllForHand(hand: String): List<FingerprintTemplateEntity>` — `SELECT * FROM fingerprint_templates WHERE hand = :hand`. Used during verification, to compare a live capture against every enrolled person for that hand (1:N identification, not 1:1 verification).
- `getDistinctPersonNames(): List<String>` — `SELECT DISTINCT personName ... ORDER BY personName`. Declared but not referenced anywhere outside the DAO — an available but currently-unused query, likely intended for a future person picker.
- `getAll(): List<FingerprintTemplateEntity>` — all rows, ordered by `personName, hand, sampleSlot`, for the record-viewer "show everything" list.
- `getAllForPerson(personName: String): List<FingerprintTemplateEntity>` — all of one person's samples (both hands), ordered by `hand, sampleSlot`, for the record-viewer's per-person filter.

**Callers/dependents.** Never called directly from outside `data/` — always accessed through `FingerprintTemplateRepository`.

---

### `FingerprintTemplateRepository.kt`

**What it is.** The layer between raw Room storage and the OpenCV-based matching/enrollment code. It does two jobs: (1) convert between `FingerprintTemplateEntity` (BLOB-friendly) and `FingerprintTemplate`/`FeatureSet` (OpenCV `Mat`-based), and (2) manage the round-robin sample-slot assignment.

**Declarations** — `class FingerprintTemplateRepository(private val dao: FingerprintTemplateDao)`:

- `samplesAsTemplates(personName: String, hand: String): List<Pair<FingerprintTemplateEntity, FingerprintTemplate>>` (`:21`) — fetches all samples for a person/hand and maps each to a `(entity, template)` pair via the private `toTemplate()` extension. Used by `FingerprintEnrollmentService` to compare a new capture against the person's own existing samples (duplicate-hand detection during enrollment).
- `allForHand(hand: String): List<Pair<String, FingerprintTemplate>>` (`:24`) — fetches every enrolled sample for a given hand across *all* people, paired with `personName`. Used by `FingerprintVerificationService` to run 1:N identification against everyone enrolled on that hand.
- `allRecords(): List<FingerprintTemplateEntity>` (`:28`) — plain passthrough to `dao.getAll()`, for the fingerprint record viewer screen.
- `recordsForPerson(personName: String): List<FingerprintTemplateEntity>` (`:30`) — passthrough to `dao.getAllForPerson`.
- `deleteRecord(entity: FingerprintTemplateEntity)` (`:33`) — passthrough to `dao.delete`.
- `saveSample(personName: String, hand: String, features: FeatureSet, sharpnessValue: Float, fullDetailGray: Mat): Int` (`:45`) — the core enrollment write path:
  1. Loads all existing samples for this `(personName, hand)`.
  2. Calls `nextSlot(existing)` to pick a slot number (1..`MAX_ENROLL_SAMPLES`).
  3. Looks up whether that slot is already occupied (`reused`) — if so, its `id` is reused so the upsert replaces that exact row rather than creating an orphan.
  4. Serializes `features.kpOrb`/`desOrb` and `features.kpAkaze`/`desAkaze` via `serializeKeypoints()`/`serializeMat()` (from `MatSerialization.kt`).
  5. Calls `exportFingerprintFormats(fullDetailGray)` to produce the WSQ/Base64/ISO export blobs.
  6. Builds a new `FingerprintTemplateEntity` and calls `dao.upsert(entity)`.
  7. Re-queries `dao.getSamples(...)` and returns its `.size` as the resulting sample count.
  - `fullDetailGray` is deliberately a separate parameter from the ORB/AKAZE-ready `features`: it's the as-captured grayscale ROI crop **before** CLAHE/Gabor enhancement, at native resolution — used only to build the export records so what's stored for display is "the fingerprint as actually captured," not the smaller enhanced image the matcher operates on. Since it's the same input `preprocessFingerprint()` consumes, it stays fully re-derivable into a fresh matching template later if ever needed.
- `nextSlot(existing: List<FingerprintTemplateEntity>): Int` (private, `:89`) — the round-robin logic:
  1. Collects the set of currently-used slot numbers.
  2. Scans `1..MAX_ENROLL_SAMPLES` in order and returns the first slot **not** in use (fills empty slots first).
  3. If all slots are full, returns the slot belonging to the sample with the **oldest** `createdAt` — evicts the least-recently-captured sample.
- `toTemplate()` (private extension on `FingerprintTemplateEntity`, `:97`) — reverses the BLOB storage: rebuilds `Mat` objects via `deserializeMat(MatBlob(...))` for both descriptor sets and `deserializeKeypoints(...)` for both keypoint sets, and assembles a `FingerprintTemplate`.

**Callers/dependents.** Constructed in `camera/CameraCaptureScreen.kt` and `ui/screens/FingerprintRecordViewerScreen.kt` (both via `AppDatabase.getInstance(context).fingerprintTemplateDao()`). Consumed by `biometrics/fingerprint/FingerprintEnrollmentService.kt` and `FingerprintVerificationService.kt`, and by `FingerprintRecordViewerScreen.kt`.

**Gotchas / design decisions**
- The round-robin/eviction logic is a direct behavioral port of `mod_fingerprint.py`'s `_next_sample_key` — do not "simplify" without checking parity against that reference.
- `saveSample` does an extra `SELECT` after the `upsert` just to compute a count rather than computing it locally — simpler but one more DB round trip than strictly necessary.

---

### `IdCardPhotoEntity.kt`

**What it is.** A **singleton-row** entity holding the most recently OCR-scanned ID card's cropped face photo, kept so the face-matching flow can compare a live selfie against it without a separate capture step.

**Declarations**

- `IdCardPhotoEntity` (`:15`), `data class` with `@Entity(tableName = "id_card_photo")`:
  - `id: Int = SINGLETON_ID` — `@PrimaryKey`, always the constant `1`. This enforces "single row": every insert uses the same primary key, so combined with `OnConflictStrategy.REPLACE`, every save overwrites the one existing row instead of adding a new one.
  - `photoPng: ByteArray` — the cropped face photo, PNG-encoded.
  - `capturedAt: Long` — capture timestamp.
  - `companion object { const val SINGLETON_ID = 1 }`.

The class doc comment explains *why* this table only ever holds one row: "face-matching a person against their own most recently scanned card is the only flow this app supports" — there's no multi-user/multi-card history by design.

**Callers/dependents.** Used exclusively through `IdCardPhotoRepository` and `IdCardPhotoDao`.

---

### `IdCardPhotoDao.kt`

**What it is.** The Room DAO for the singleton `id_card_photo` table.

**Declarations** (`@Dao interface IdCardPhotoDao`):

- `upsert(entity: IdCardPhotoEntity)` — `@Insert(onConflict = OnConflictStrategy.REPLACE)`. Because `entity.id` is always `IdCardPhotoEntity.SINGLETON_ID`, this always replaces the existing row (or inserts the first one).
- `get(): IdCardPhotoEntity?` — `SELECT * FROM id_card_photo WHERE id = ${IdCardPhotoEntity.SINGLETON_ID}`. Uses Kotlin string-template interpolation of a compile-time constant directly into the `@Query` SQL string (not a `:param` bind) — safe here only because `SINGLETON_ID` is a hardcoded `const val`, not user input. Returns `null` if no card has ever been scanned.

**Callers/dependents.** Only used by `IdCardPhotoRepository`.

---

### `IdCardPhotoRepository.kt`

**What it is.** Thin wrapper converting between OpenCV `Mat` and the PNG-encoded `ByteArray` stored in `IdCardPhotoEntity`.

**Declarations** — `class IdCardPhotoRepository(private val dao: IdCardPhotoDao)`:

- `save(photo: Mat, capturedAt: Long)` (`:10`) — PNG-encodes the given `Mat` via `Imgcodecs.imencode(".png", photo, buf)` into a `MatOfByte`, then calls `dao.upsert(...)` with the resulting bytes. Overwrites any previous photo (singleton-row design).
- `loadLatest(): IdCardPhotoEntity?` (`:16`) — passthrough to `dao.get()`.
- `decode(entity: IdCardPhotoEntity): Mat` (`:18`) — reverses `save`: `Imgcodecs.imdecode(MatOfByte(*entity.photoPng), Imgcodecs.IMREAD_COLOR)`, decoding the stored PNG bytes back into a color `Mat`.

**Callers/dependents.** Constructed in `camera/CameraCaptureScreen.kt` via `AppDatabase.getInstance(context).idCardPhotoDao()`. `save()` is called after a successful ID-card OCR scan crops out the face photo. `loadLatest()` + `decode()` are called together when the face-matching flow needs to load the previously scanned card's photo to compare against a live selfie.

**Gotchas**
- `decode()` requires a non-null entity — if `loadLatest()` returns `null` (no card ever scanned), callers must check before calling `decode()` (as `CameraCaptureScreen.kt` does: `idCardPhotoRepository.loadLatest() ?: return@withContext null`).

---

### `crypto/DbPassphraseProvider.kt`

**What it is.** A singleton `object` responsible for generating, persisting, and retrieving the random passphrase that encrypts the SQLCipher database. This is the file that makes `AppDatabase`'s encryption actually work.

**Declarations** — `object DbPassphraseProvider`:

- Constants: `KEYSTORE_ALIAS = "kyc_db_passphrase_key"`, `PREFS_NAME = "kyc_secure_prefs"`, `PREF_KEY_WRAPPED_PASSPHRASE = "wrapped_db_passphrase"`, `PASSPHRASE_LENGTH_BYTES = 32`, `GCM_TAG_LENGTH_BITS = 128`, `GCM_IV_LENGTH_BYTES = 12`.
- `getOrCreate(context: Context): ByteArray` (`:28`) — the public entry point, called once by `AppDatabase.build()`:
  1. Opens `SharedPreferences` named `kyc_secure_prefs` (private mode) and reads the string at `wrapped_db_passphrase`.
  2. Obtains (or creates) the AES Keystore key via `getOrCreateKeystoreKey()`.
  3. If a wrapped passphrase already exists, decrypts and returns it via `unwrap(existing, key)`.
  4. Otherwise, generates 32 fresh random bytes with `SecureRandom().nextBytes(...)`, wraps them with `wrap(passphrase, key)`, stores the wrapped (Base64) string in `SharedPreferences`, and returns the raw passphrase bytes.
- `getOrCreateKeystoreKey(): SecretKey` (private, `:43`) — opens the `AndroidKeyStore` provider, and if a key already exists under `KEYSTORE_ALIAS` returns it; otherwise generates a new one via `KeyGenerator` with `KeyGenParameterSpec`: AES, 256-bit, `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, `setUserAuthenticationRequired(false)` (so no biometric/PIN prompt is needed to use the key — it's available any time the app runs, not gated by device unlock).
- `wrap(passphrase: ByteArray, key: SecretKey): String` (private, `:61`) — encrypts the passphrase with `AES/GCM/NoPadding` using the Keystore key, letting the cipher generate a random IV, then Base64-encodes `iv + ciphertext` (IV prepended to the ciphertext, GCM tag included at the end of the ciphertext) as one string.
- `unwrap(blob: String, key: SecretKey): ByteArray` (private, `:69`) — reverses `wrap`: Base64-decodes, slices off the first `GCM_IV_LENGTH_BYTES` (12) bytes as the IV, treats the remainder as ciphertext+tag, and decrypts with `GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)`.

**Callers/dependents.** Called only from `AppDatabase.build()`.

**Gotchas / design decisions**
- The design intent is explicit: the wrapped blob is stored in **plain** `SharedPreferences` deliberately, because it "is useless without the Keystore-resident key" — the AES key never leaves the hardware/software-backed Android Keystore, so encrypting the `SharedPreferences` file itself would be redundant.
- If the app's Keystore entry is ever cleared while the wrapped-passphrase `SharedPreferences` value survives, `unwrap()` would fail to decrypt (key/blob mismatch) — there's no error handling/fallback path for that scenario; it would throw.
- Conversely, if `SharedPreferences` is cleared but the Keystore key is not, `getOrCreate` regenerates a brand-new random passphrase — and since the actual SQLCipher database was encrypted with the *old* passphrase, the app would then fail to open `kyc.db` at all (wrong key). There is no recovery/reset logic anywhere for that case.

---

### How the SQLCipher encryption works, end to end

1. **Passphrase generation.** The first time the app ever opens the database, `DbPassphraseProvider.getOrCreate()` generates 32 cryptographically random bytes (`SecureRandom`) — this raw byte array *is* the SQLCipher database passphrase (not a user-typed password; the user never sees or manages it).
2. **Wrapping with Android Keystore.** That raw passphrase is immediately encrypted ("wrapped") using AES-256-GCM with a key that lives inside the Android Keystore (alias `kyc_db_passphrase_key`). Android Keystore keys are hardware- or OS-backed and their raw key material can never be extracted from the device, even by the app itself — the app can only ask the Keystore to encrypt/decrypt *using* the key, never read the key's bytes.
3. **Storage.** The wrapped result (a 12-byte random IV + AES-GCM ciphertext-with-tag, Base64-encoded) is saved as a single string value in a normal (unencrypted) `SharedPreferences` file called `kyc_secure_prefs`. This is safe specifically because of step 2 — the blob is meaningless without the Keystore key.
4. **Every subsequent app start**, `getOrCreate()` finds the existing wrapped blob, asks the Keystore for the same AES key (looked up by alias, not regenerated), and decrypts the blob back into the original 32-byte passphrase.
5. **Handing the passphrase to Room/SQLCipher.** `AppDatabase.build()` takes that raw passphrase and wraps it in a `net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase)` — a drop-in replacement for Room's default `SupportSQLiteOpenHelper.Factory`. Instead of Room opening `kyc.db` through Android's stock (unencrypted) SQLite, it opens it through SQLCipher, passing the passphrase as the SQLCipher encryption key. SQLCipher then transparently encrypts/decrypts every page of the `kyc.db` file using that key — every Room entity, DAO, and query in this package works completely unaware that the underlying storage is encrypted.
6. **Net effect:** the on-disk `kyc.db` file is fully encrypted at rest; the key that decrypts it is itself encrypted at rest by a key that never leaves the Android Keystore; and the only thing needed to bootstrap the whole chain each app launch is the Keystore being available and the small wrapped blob in `SharedPreferences`.

---


---

## 5. Face & Liveness (`com.example.kycapp.biometrics.face`, `.liveness`)

These two packages implement fully on-device face verification: they confirm the person holding the phone is the same person pictured on their ID card, and that they are a live human (not a photo held up to the camera). Nothing here calls a server — face detection, alignment, embedding, matching, and blink-liveness all run locally via MediaPipe (landmark detection) and ONNX Runtime Mobile (the ArcFace recognition model). The code is a Kotlin port of an existing Python biometric backend (`mod_face.py`, `mod_liveness.py`, `mod_ocr.py`).

There is exactly one external caller for all seven files: `camera/CameraCaptureScreen.kt`, specifically its `CaptureMode.FACE_MATCH` flow.

### `biometrics/face/FaceAligner.kt`

**What it is.** Converts MediaPipe's 478-point face mesh into the standard 112×112 "ArcFace-aligned" face crop the recognition model expects, by picking 5 stable landmark points and warping them onto a fixed template.

**Declarations:**

- `ALIGNED_SIZE = 112.0` — the fixed output crop size baked into the ArcFace/InsightFace `w600k_r50` model; not configurable, since the embedding model's input layer is hard-wired to 112×112.
- Landmark index constants (`EYE_A_OUTER = 33`, `EYE_A_INNER = 133`, `EYE_B_OUTER = 362`, `EYE_B_INNER = 263`, `NOSE_TIP = 1`, `MOUTH_CORNER_A = 61`, `MOUTH_CORNER_B = 291`) — indices into MediaPipe's 468/478-point mesh (the first 468 indices are identical between the two point counts). The eye "centers" reuse the same outer/inner corner landmarks the liveness EAR calculation uses — deliberate reuse, not coincidence.
- `ARCFACE_TEMPLATE: Array<Point>` — the canonical InsightFace `arcface_dst` 5-point template (left eye, right eye, nose, left mouth, right mouth) for a 112×112 aligned crop, as literal pixel coordinates. Confirmed empirically against InsightFace's own RetinaFace keypoint output — reverse-verified, not derived from documentation.
- `midpoint(a, b)` — private helper, simple 2-point average.
- **`extractFivePoints(landmarks: List<NormalizedLandmark>, width: Int, height: Int): Array<Point>`** — takes MediaPipe's normalized (0–1) landmarks plus the source image's pixel dimensions, returns 5 points in pixel space `[leftEye, rightEye, nose, leftMouth, rightMouth]`. Each eye point is the midpoint of that eye's outer and inner corner. Because MediaPipe's indices aren't guaranteed to map to "the subject's actual left eye" relative to image x, the function sorts by x (`if (eyeA.x <= eyeB.x) eyeA to eyeB else eyeB to eyeA`) — same for mouth corners — rather than assuming a fixed index-to-side mapping.
- **`alignFace(bgr: Mat, points: Array<Point>): Mat`** — fits a similarity transform (uniform scale + rotation + translation, no shear) from the 5 extracted points to `ARCFACE_TEMPLATE` using `Calib3d.estimateAffinePartial2D`, then applies it via `Imgproc.warpAffine` to produce a 112×112 crop. Mirrors InsightFace's Python `estimate_norm()`/`norm_crop()`. This is a deliberate substitution: rather than porting RetinaFace's face-detection decoding to get 5 landmarks, MediaPipe's FaceLandmarker (already present for blink liveness) is reused to derive the same 5-point layout, then fit to the identical geometric template.

**Callers:** `extractFivePoints`/`alignFace` are both called from `FaceEmbeddingExtractor.extractFaceEmbedding()` (stored ID photo) and inline in `CameraCaptureScreen.kt`'s live-landmarker result callback (live selfie).

**Gotcha:** `estimateAffinePartial2D` can theoretically return a degenerate/null transform for pathological landmark geometry; not explicitly null-guarded before `warpAffine` — would throw rather than fail gracefully, though normal faces essentially never trigger it.

### `biometrics/face/FaceEmbedder.kt`

**What it is.** Wraps the ONNX Runtime session for InsightFace's `w600k_r50` ArcFace face-recognition model, turning a 112×112 aligned BGR face crop into a fixed-length, L2-normalized embedding vector — the actual "face fingerprint" used for matching.

**Declarations:**

- `MODEL_ASSET = "w600k_r50_fp16.onnx"` — the bundled model, an FP16 conversion of the `buffalo_l` model pack (done to shrink size/speed up mobile inference). Validated to have cosine similarity 1.0000 against the original FP32 model (no measurable accuracy loss from the conversion) and ~0.98–0.99 similarity between this Kotlin preprocessing pipeline and InsightFace's own official embedding output.
- `INPUT_SIZE = 112` — must match `FaceAligner.ALIGNED_SIZE`.
- **`class FaceEmbedder(context: Context)`**
  - Constructor eagerly loads ONNX model bytes from assets and creates an `OrtSession` — a real, possibly-slow init, which is why `CameraCaptureScreen.kt` only constructs it when entering face-match mode, and disposes it via `DisposableEffect`.
  - `inputName` is read once from `session.inputNames` rather than hardcoded.
  - **`embed(alignedBgr112: Mat): FloatArray`** — the core inference call. Requires a 112×112 `CV_8UC3` BGR `Mat` from `FaceAligner.alignFace()`. Steps:
    1. Copies raw BGR bytes out of the `Mat` into a flat `ByteArray`.
    2. Manually reformats HWC-interleaved BGR into **planar CHW** float layout (all B, then all G, then all R) for the `[1, 3, 112, 112]` ONNX input.
    3. Normalizes each byte from `[0,255]` to `[-1,1]` via `(value - 127.5) / 127.5` — standard ArcFace preprocessing.
    4. **Keeps BGR channel order** (not RGB) — matches how the model was trained.
    5. Runs the ONNX session, extracts the `[1, N]` output tensor's first row.
    6. **L2-normalizes** the embedding before returning — this is what lets `FaceMatcher.matchFaces()` treat a raw dot product as cosine similarity without a separate normalization step at match time.
  - `close()` — releases the `OrtSession`. Must be called (leaks native ONNX Runtime memory otherwise).

**Callers:** Constructed/used only in `CameraCaptureScreen.kt`. `embed()` is called twice per verification: once inside `FaceEmbeddingExtractor.extractFaceEmbedding()` (stored ID photo) and once inline in the live-landmarker callback (live selfie).

### `biometrics/face/FaceMatcher.kt`

**What it is.** The final decision step: given two L2-normalized embeddings, decides whether they're the same person.

**Declarations:**

- `FACE_MATCH_THRESHOLD = 50.0` — the cosine-similarity-as-percentage cutoff, ported from `mod_face.py`'s `LiveFaceVerifier` (same value carried over from Python).
- `data class FaceMatchResult(val matched: Boolean, val confidencePercent: Double)`.
- **`matchFaces(idEmbedding: FloatArray, liveEmbedding: FloatArray): FaceMatchResult`** — computes the plain dot product `sum(id[i] * live[i])`. Because both inputs are already L2-normalized, this dot product **is** the cosine similarity by definition (no separate division-by-norms step needed). Scaled to a percentage (`dot * 100.0`), compared against `FACE_MATCH_THRESHOLD`. `matched = confidence > 50.0`.

**Callers:** Only `CameraCaptureScreen.kt`, once both embeddings are available. Drives `"MATCH (%.1f%%)"`/`"NO MATCH (%.1f%%)"` status text and gates the success dialog.

### `biometrics/face/FaceEmbeddingExtractor.kt`

**What it is.** A convenience pipeline function running the full detect → align → embed sequence on a **single still image**, used specifically for embedding the stored ID-card photo (not the live camera feed — see the narrative below for why those two paths differ).

**Declarations:**

- `data class FaceEmbeddingResult(val embedding: FloatArray, val alignedFace: Mat)` — bundles the embedding with the aligned crop (kept so the UI can preview exactly what was embedded, via `faceMatchImages`).
- **`extractFaceEmbedding(landmarker: FaceLandmarker, embedder: FaceEmbedder, bgr: Mat): FaceEmbeddingResult?`**
  1. Converts `bgr` to RGBA then to a `Bitmap` (`Utils.matToBitmap`).
  2. Wraps it as an MPImage via `BitmapImageBuilder(bitmap).build()`.
  3. Runs synchronous still-image detection: `landmarker.detect(mpImage)` — requires the landmarker built in `RunningMode.IMAGE` (via `FaceLandmarkerProvider.createForStillImages()`).
  4. Takes `result.faceLandmarks().firstOrNull()`; returns `null` with a warning log if no face found — mirrors `mod_ocr.py`'s `_extract_id_photo()` and `mod_face.py`'s `id_photo_embedding()`, both returning `None`/no-face rather than throwing.
  5. Calls `extractFivePoints()` then `alignFace()` on the *original* `bgr` `Mat`, then `embedder.embed(aligned)`.
  6. Returns the bundled `FaceEmbeddingResult`.
- Debug logging (tag `"FaceEmbedExtractor"`) traces input dimensions, detection results, success/failure — useful for diagnosing why a stored ID photo fails to embed.

**Callers:** `CameraCaptureScreen.kt`, once, inside a `LaunchedEffect(mode)` on entering `FACE_MATCH` mode: loads the latest `IdCardPhotoEntity`, decodes it, calls `extractFaceEmbedding(stillFaceLandmarker!!, faceEmbedder!!, photoMat)`. The live selfie path deliberately does *not* call this function.

### `biometrics/liveness/BlinkLivenessAnalyzer.kt`

**What it is.** A CameraX `ImageAnalysis.Analyzer` that converts each raw camera frame into a `Bitmap` and hands it to a MediaPipe `FaceLandmarker` running in `LIVE_STREAM` (async) mode, guarding against overlapping async detection calls.

**Declarations:**

- **`class BlinkLivenessAnalyzer(private val checker: BlinkLivenessChecker, private val detectAsync: (bitmap: Bitmap, timestampMs: Long) -> Unit) : ImageAnalysis.Analyzer`**
  - `busy: Boolean` (`@Volatile`) — a manual re-entrancy guard. MediaPipe's `LIVE_STREAM` mode forbids submitting a new `detectAsync()` call before the previous one's result has been delivered, but CameraX keeps calling `analyze()` on every new frame regardless. `busy` + caller-invoked `notifyResultProcessed()` form an in-flight-request lock.
  - **`notifyResultProcessed()`** — called by the owner once the `FaceLandmarker`'s result listener has consumed a result, clearing `busy`. Wired in `CameraCaptureScreen.kt` as `livenessAnalyzerRelay[0]?.notifyResultProcessed()` inside the `onResult` callback.
  - **`analyze(image: ImageProxy)`** — if `busy`, or the checker already `passed`, or `timedOut`, just closes the frame and returns. Otherwise sets `busy = true`, converts to a rotated `Bitmap` via `toRotatedBitmap()`, invokes `detectAsync` with the bitmap and `SystemClock.uptimeMillis()` (MediaPipe `LIVE_STREAM` requires strictly increasing timestamps). Any exception resets `busy = false` so the pipeline doesn't permanently stall. `image.close()` always runs in `finally`.
- **`private fun ImageProxy.toRotatedBitmap(): Bitmap`** — manual YUV_420_888 → NV21 → JPEG → `Bitmap` conversion:
  - Copies the Y plane respecting `rowStride`/`pixelStride` (not a raw bulk copy) — stride padding beyond the logical width is common on real devices.
  - Interleaves V-then-U bytes for the chroma plane (NV21 is VU-interleaved).
  - Feeds the NV21 array into `YuvImage.compressToJpeg` (quality 85), decodes back via `BitmapFactory` — mirrors `CameraCaptureScreen`'s `toFaceJpegBytes()` plane handling elsewhere in the app.
  - Applies a final rotation correction using `imageInfo.rotationDegrees` via `Matrix().postRotate()` (skipped entirely if `rotation == 0`).

**Callers:** Instantiated once in `CameraCaptureScreen.kt`; its `detectAsync` lambda stores the bitmap into `lastLiveBitmapRelay[0]` (see the color-corruption fix in the narrative below) and calls `liveFaceLandmarker.detectAsync(...)`. Registered on the `ImageAnalysis` use case, active only in `CaptureMode.FACE_MATCH`.

### `biometrics/liveness/BlinkLivenessChecker.kt`

**What it is.** A stateful per-session tracker watching a stream of per-frame Eye Aspect Ratio (EAR) values, deciding whether a genuine blink occurred within a time window — the actual liveness gate.

**Declarations:**

- `RIGHT_EYE_IDX = [33, 159, 158, 133, 153, 145]`, `LEFT_EYE_IDX = [362, 380, 374, 263, 386, 385]` — six landmark indices per eye, following the Soukupová & Čech (2016) EAR paper's point layout (index 0/3 are horizontal corners, 1/2 and 4/5 are vertical top/bottom pairs). Ported from `mod_liveness.py`. Note: indices 33/133/362/263 are the *same* ones `FaceAligner.kt` uses as eye corners — deliberate reuse.
- `EAR_BLINK_THRESHOLD = 0.21` — EAR value below which eyes are "closed" (open eyes typically EAR ≈ 0.25–0.35).
- `BLINK_WINDOW_SEC = 8.0` — time budget for a qualifying blink.
- `MIN_CONSECUTIVE_CLOSED_FRAMES = 2` (private) — requires EAR under threshold for 2+ consecutive frames before eyes count as genuinely closed. **This prevents a real bug found this session**: without it, one noisy/jittery low-EAR frame right after `reset()` could flip `passed` to `true` almost instantly, capturing an embedding from an arbitrary frame the user wasn't ready for — and likely to mismatch the ID photo.
- `dist()`/`eyeAspectRatio()` (private) — standard EAR formula: `(verticalA + verticalB) / (2 * horizontal)`.
- **`meanEar(points, width, height): Double`** — averages `eyeAspectRatio()` over both eyes.
- **`class BlinkLivenessChecker(private val windowSec = BLINK_WINDOW_SEC, private val threshold = EAR_BLINK_THRESHOLD)`**
  - State: `startTimeMs`, `eyesWereClosed`, `closedFrameStreak` (private); public `passed`, `lastEar`, `faceSeen`.
  - `init { reset() }` — pre-armed at construction.
  - **`reset()`** — resets the timer and all state. Called by `CameraCaptureScreen.kt` before each verification attempt, so a user can retry without recreating the object.
  - `elapsedSec`, `timedOut` — computed properties.
  - **`update(landmarks, width, height)`** — the per-frame state machine:
    - No-op if already `passed`.
    - `faceSeen = landmarks != null`; if null, resets `closedFrameStreak` and returns.
    - Computes `ear = meanEar(...)`, stores as `lastEar`.
    - If `ear < threshold`: increments `closedFrameStreak`; at `MIN_CONSECUTIVE_CLOSED_FRAMES` sets `eyesWereClosed = true` (latched).
    - Else: resets `closedFrameStreak = 0`; if `eyesWereClosed` was latched true, sets `passed = true` — **the pass condition is the recovery edge**: EAR dips below threshold for 2+ frames, then rises back above it. A static photo has constant EAR and can never produce this pattern.

**Callers:** `CameraCaptureScreen.kt` constructs one per face-match session, calls `.reset()` before each attempt, `.update()` inside the live landmarker's result callback, and reads its properties for UI status text and the wait-loop exit condition; `BlinkLivenessAnalyzer` reads `.passed`/`.timedOut` to decide whether to keep submitting frames.

### `biometrics/liveness/FaceLandmarkerProvider.kt`

**What it is.** A factory `object` centralizing construction of MediaPipe's `FaceLandmarker` for the app's two usage modes: continuous live-camera streaming vs. one-off still images.

**Declarations:**

- `MODEL_ASSET_PATH = "face_landmarker.task"` — shared model asset.
- **`object FaceLandmarkerProvider`**
  - **`create(context, onResult: (FaceLandmarkerResult, MPImage) -> Unit, onError = {}): FaceLandmarker`** — builds a `RunningMode.LIVE_STREAM` landmarker, `setNumFaces(1)`, wires `onResult`/`onError` as async listeners. `LIVE_STREAM` is the mode for a continuous camera feed; results arrive asynchronously after each `detectAsync()` call, which needs strictly increasing timestamps.
  - **`createForStillImages(context): FaceLandmarker`** — builds an `IMAGE`-mode (synchronous) landmarker, also `setNumFaces(1)`, but **lowers confidence thresholds**: `setMinFaceDetectionConfidence(0.3f)`, `setMinFacePresenceConfidence(0.3f)` (down from ~0.5 defaults). **Fixes a real observed bug**: a CNIC's printed photo is a harder detection target than a live selfie (security-pattern background, lamination glare, print/scan quality loss), and the default 0.5 threshold was rejecting real, valid printed photos outright.
  - A second factory method exists because `LIVE_STREAM` instances can only be driven via `detectAsync()` with monotonic timestamps, which doesn't fit a single independent still-image capture.

**Callers:** `CameraCaptureScreen.kt` uses both: `createForStillImages()` builds `stillFaceLandmarker` (used only for embedding the stored ID photo); `create()` builds `liveFaceLandmarker`, driven by `BlinkLivenessAnalyzer` for every live camera frame.

---

### How face matching works end-to-end

Given a previously-saved ID card photo and a live front-camera feed, here is the actual order of execution in `CaptureMode.FACE_MATCH` (all in `CameraCaptureScreen.kt`):

1. **Load and embed the ID photo (once, on entering face-match mode).** A `LaunchedEffect(mode)` loads the latest `IdCardPhotoEntity` from `IdCardPhotoRepository`, decodes it to a `Mat`, and calls `FaceEmbeddingExtractor.extractFaceEmbedding(stillFaceLandmarker, faceEmbedder, photoMat)`. Internally: `stillFaceLandmarker.detect()` (synchronous, `IMAGE`-mode, lowered confidence thresholds tuned for printed CNIC photos) → `extractFivePoints()` (x-sorted 5-point ArcFace layout) → `alignFace()` (similarity-transform warp to the fixed template) → `embedder.embed()` (planar CHW, `[-1,1]` normalization, BGR order, ONNX inference, L2-normalize). Result stored as `idEmbeddingResult`.

2. **Start the live blink + live-embedding stream (on "verify" tap).** `livenessChecker.reset()` re-arms the blink state machine; a CameraX `ImageAnalysis` use case (640×480, only bound in `FACE_MATCH` mode) feeds every frame to `BlinkLivenessAnalyzer.analyze()`, which (guarded by `busy`) converts each frame to an upright `Bitmap` and calls `detectAsync()` on the `LIVE_STREAM` landmarker. Each result triggers `livenessChecker.update()`, computing `meanEar()` and running the dip-then-recover state machine (EAR < 0.21 for 2+ consecutive frames, then EAR ≥ 0.21 again, within 8s).

3. **Embed the exact frame where liveness passes.** The moment `passed` flips `false → true` inside the `onResult` callback, the live face is embedded **inline** — not via `FaceEmbeddingExtractor`. It reuses the already-detected landmarks (no re-detection) and calls `extractFivePoints()` → `alignFace()` → `embed()` directly. **This is the fix for a real color-corruption bug found this session**: the bitmap used is `lastLiveBitmapRelay[0] ?: BitmapExtractor.extract(mpImage)` — preferring the exact clean `Bitmap` originally handed to `detectAsync()`, falling back to MediaPipe's `BitmapExtractor.extract()` only if unavailable. Extracting a fresh `Bitmap` back out of the `MPImage` was coming back with visibly corrupted colors (RGB channel fringing) on-device, badly distorting the embedding — even though face *detection* on the same `MPImage` worked fine, which is why the (now-removed) standalone blink-liveness test screen — which only reads landmark positions, never pixel colors — never caught it. This is exactly why `FaceEmbeddingExtractor` is used only for the still ID photo, and the live path re-implements the same three steps inline instead of reusing it: the live path needs the *original* clean camera `Bitmap` reference, not a reconstruction from the `MPImage`.

4. **Match.** Once both `idEmbeddingResult` and `liveEmbeddingResult` are non-null (a polling loop with a 9-second deadline), `FaceMatcher.matchFaces()` computes the dot product of the two L2-normalized embeddings (= cosine similarity), scales to a percentage, compares against `FACE_MATCH_THRESHOLD` (50.0). Drives the match/no-match status text, the side-by-side aligned-face comparison view, and the success dialog.

---


---

## 6. UI / Navigation Layer and the Shared Camera Screen

### 6.1 App entry point

#### `MainActivity.kt`
A minimal `ComponentActivity`. `onCreate()` calls `setContent { }` and renders `MaterialTheme { Surface { ... } }` wrapping a single `NavHostController` (via `rememberNavController()`) passed straight into `KycNavGraph(navController)`. There is no other activity in the app — every screen is a Compose destination inside this one host. There's no ViewModel layer — state lives inside each composable (mostly inside `CameraCaptureScreen`), not in shared app-level state holders.

#### `KycApplication.kt`
Custom `Application` subclass, referenced from the manifest (`android:name`), doing two pieces of one-time native-library bootstrapping before any screen runs:

- `OpenCVLoader.initLocal()` — loads OpenCV's native `.so`. Logs success/failure but does not hard-fail the app if it returns `false`.
- `System.loadLibrary("sqlcipher")` — explicit manual load of libsqlcipher. Necessary because the `net.zetetic:sqlcipher-android` artifact (unlike the older `android-database-sqlcipher`) does **not** auto-load its native library, so it must be loaded by hand before the first Room database (via `SupportOpenHelperFactory`) is opened.

Both must succeed before `AppDatabase.getInstance()` or any OpenCV `Mat` operation is touched anywhere else in the app.

### 6.2 Navigation layer

#### `Routes.kt`
Defines every navigation destination as a string constant/route-builder pair, plus the two enums that parameterize the shared screens.

**Route constants and builders:**
- `MAIN_MENU` — plain string, no args.
- `ID_CARD_INTRO`, `OCR_INTRO`, `FACE_MATCH_INTRO`, `FINGERPRINT_INTRO` — plain intro-screen routes, no args.
- `FINGERPRINT_NAME_ENTRY` — shown once before the left-hand enroll tutorial (match doesn't need a name — it identifies the person from stored templates instead of being told).
- `FINGERPRINT_TUTORIAL = "fingerprint_tutorial/{mode}/{hand}/{name}"` with builder `fingerprintTutorial(mode, hand, name = "")`.
- `CAMERA = "camera/{mode}/{hand}/{name}"` with builder `camera(mode, hand = HandSide.NONE.name, name = "")` — the single generic camera route reused by all five `CaptureMode`s.
- `FINGERPRINT_RECORDS = "fingerprint_records/{name}"` with builder `fingerprintRecords(name = "")` — empty name browses all records; a specific name (used right after an enroll session) filters to just that person.

**The `{name}` placeholder trick:** Compose's `NavHost` doesn't reliably match an *actually empty* string segment against a required path argument, so `Routes` defines a private sentinel `NO_NAME = "_"`. `encodeName(name)` returns `NO_NAME` when blank, otherwise `Uri.encode(name)`; the public `decodeName(encoded)` reverses this. Every route/screen carrying a name argument goes through this pair.

**`enum class CaptureMode(val label: String)`** — `ID_CARD`, `OCR`, `FACE_MATCH`, `FINGERPRINT_ENROLL`, `FINGERPRINT_MATCH`, each with a human-readable `label`. `CaptureMode.fromRoute(value)` parses the route's `{mode}` segment back into the enum, defaulting to `ID_CARD` if unmatched.

**`enum class HandSide(val label: String)`** — `LEFT`, `RIGHT`, `NONE` (empty label, non-fingerprint flows). `HandSide.fromRoute(value)` mirrors `CaptureMode.fromRoute`, defaulting to `NONE`.

#### `KycNavGraph.kt`
The single `@Composable fun KycNavGraph(navController)` builds the whole `NavHost`, `startDestination = Routes.MAIN_MENU`.

| Route | Screen | Notes |
|---|---|---|
| `MAIN_MENU` | `MainMenuScreen` | Five click callbacks routed to the four intro screens plus fingerprint-records. |
| `ID_CARD_INTRO` | `ActionIntroScreen(title="ID Card Detection", buttonLabel="Start Scanning")` | → `camera(CaptureMode.ID_CARD.name)`. |
| `OCR_INTRO` | `ActionIntroScreen(title="Perform OCR", buttonLabel="Start OCR")` | → `camera(CaptureMode.OCR.name)`. |
| `FACE_MATCH_INTRO` | `ActionIntroScreen(title="Face Matching", buttonLabel="Start Face Scan")` | → `camera(CaptureMode.FACE_MATCH.name)`. |
| `FINGERPRINT_INTRO` | `FingerprintIntroScreen` | `onEnrollClick` → `FINGERPRINT_NAME_ENTRY`; `onMatchClick(hand)` → `fingerprintTutorial(FINGERPRINT_MATCH, hand.name)`; `onViewRecordsClick` → `fingerprintRecords()`. |
| `FINGERPRINT_RECORDS` (arg `name`) | `FingerprintRecordViewerScreen(personNameFilter=decodeName(name), onBack=popBackStack)` | |
| `FINGERPRINT_NAME_ENTRY` | `FingerprintNameScreen` | `onContinue(name)` → `fingerprintTutorial(FINGERPRINT_ENROLL, LEFT, name)`. |
| `FINGERPRINT_TUTORIAL` (args `mode`, `hand`, `name`) | `FingerprintTutorialScreen(hand, onScanClick, onBack)` | `onScanClick` → `camera(mode.name, hand.name, name)`. |
| `CAMERA` (args `mode`, `hand`, `name`) | `CameraCaptureScreen(...)` | See below for `onScanSuccess` branching. |

**`onScanSuccess` branching:**
1. **`FINGERPRINT_ENROLL` + `LEFT` hand** → `fingerprintTutorial(FINGERPRINT_ENROLL, RIGHT, name)` with `popUpTo(FINGERPRINT_NAME_ENTRY)` — one enroll session walks both hands, back stack collapses to name-entry.
2. **`FINGERPRINT_ENROLL` + `RIGHT` hand** (both hands done) → `fingerprintRecords(name)` with `popUpTo(MAIN_MENU)` — shows the freshly enrolled records.
3. **Anything else** (a match, or ID_CARD/OCR/FACE_MATCH) → `MAIN_MENU` with `popUpTo(MAIN_MENU){inclusive=true}` — full back-stack reset.

### 6.3 Small screen composables

- **`MainMenuScreen.kt`** — stateless, five `() -> Unit` callbacks, renders a `Column` of five full-width buttons. No navigation logic — just fires callbacks; `KycNavGraph` supplies the actual `navController.navigate(...)` calls.
- **`ActionIntroScreen.kt`** — generic reusable "title + one Start button" screen, shared by ID Card, OCR, and Face Matching intros. Params: `title`, `buttonLabel`, `onStartClick`. No internal state.
- **`FingerprintIntroScreen.kt`** — params `onEnrollClick`, `onMatchClick: (HandSide) -> Unit`, `onViewRecordsClick`. Renders "Enroll Fingerprint", then "Match Left Hand"/"Match Right Hand" (invoking `onMatchClick` with the corresponding `HandSide`), then "View Stored Records". Match needs an explicit hand choice because enrolled templates are keyed per-hand (as in the original Python module) — the app can't infer which hand is being presented.
- **`FingerprintNameScreen.kt`** — one local `var name by remember { mutableStateOf("") }` bound to an `OutlinedTextField`. Params `onContinue: (name) -> Unit`, `onBack`. "Continue" is `enabled = name.isNotBlank()`, calls `onContinue(name.trim())`. Shown once per enroll session so both hands land under one person's name.
- **`FingerprintTutorialScreen.kt`** — params `hand`, `onScanClick`, `onBack`. Reused for both hands. Renders back button, "Instructions" title, static instruction text, a reference illustration (`R.drawable.fingerprint_instruction_hand`), a do/don't row via a private `ExampleBadge(good: Boolean)` composable, and a red "SCAN {HAND}" button.
- **`FingerprintRecordViewerScreen.kt`** — the most stateful small screen. Params `personNameFilter`, `onBack`.
  - Builds its own `FingerprintTemplateRepository`.
  - `LaunchedEffect(personNameFilter)` calls a local `suspend fun reload()` loading either `allRecords()` (blank filter) or `recordsForPerson(personNameFilter)`.
  - Renders a `LazyColumn` of record cards (name + hand + sample slot + ORB/AKAZE feature counts), each with a delete `IconButton` and a whole-card click opening the detail dialog.
  - **`private enum class ExportFormat`** — `ORIGINAL`, `WSQ`, `BASE64`, `ISO` — each with a `label` and `description`. Clarifies these are **export-only representations** — matching still runs on the stored ORB/AKAZE templates, not these decoded images.
  - **`RecordDetailDialog`** — switches between the four `ExportFormat`s via `FilterChip`s; decodes the corresponding byte column off-main-thread into a grayscale `Bitmap`. Tapping the preview opens a full-screen enlarged dialog.
  - Delete confirmation is a separate `AlertDialog`, calling `repository.deleteRecord(record)` then `reload()`.

### 6.4 `CameraCaptureScreen.kt` — the shared capture hub

This single composable is bound to `Routes.CAMERA` and parameterized entirely by `mode: CaptureMode`, `hand: HandSide = HandSide.NONE`, `personName: String = ""`, plus `onBack` and `onScanSuccess: () -> Unit = onBack`. Every one of the five `CaptureMode`s flows through this one file, branching internally rather than five separate screens.

**6.4.1 Remembered state (top of composable)**

Permission/basic UI:
- `hasCameraPermission` — seeded from `ContextCompat.checkSelfPermission`; a `permissionLauncher` updates it; a `LaunchedEffect(Unit)` triggers the request if not already granted.
- `statusText: String` — the single line of user-facing status shown under the preview and inside dialogs.
- `showSuccessDialog: Boolean` — controls the shared success/review `AlertDialog`.

OCR-specific: `ocrResultText: String`, `ocrIdPhoto: Bitmap?` (the face crop OCR pulled out of the ID card, shown alongside field results instead of discarded, since OCR's own ID-card-classifier gate already confirmed a good, in-frame capture).

Face-match specific: `faceMatchImages: Pair<Bitmap, Bitmap>?` (the two 112×112 aligned crops the match decision was made from), `showFaceCompareDialog`, `idEmbeddingResult: FaceEmbeddingResult?` (from the stored ID photo), `idCardPhotoBitmap: Bitmap?` (thumbnail overlay: "Matching against this ID card photo"), `liveEmbeddingResult: FaceEmbeddingResult?` (set once by the liveness callback when a blink is detected).

Camera/CameraX plumbing: `cameraProvider`, `previewView`, `camera`, `imageCapture`; `lensFacing` seeded to `LENS_FACING_FRONT` if `mode == FACE_MATCH` (face-match only ever does a live selfie via camera now, since the ID photo comes from storage — it starts on the front camera immediately instead of switching mid-flow), else `LENS_FACING_BACK`; `torchOn`, `zoomRatio`, `hasFlashUnit`.

Derived: `isFingerprintMode`, `isOnDeviceMode` (picks "Processing..." vs "Uploading..." wording — a vestige of a design where some mode might upload to a server; every current mode is on-device), `fingerprintRepository`, `idCardPhotoRepository` (both `remember`-built via `AppDatabase.getInstance(context)`).

**6.4.2 Camera/CameraX setup**

Binding happens in a `LaunchedEffect(cameraProvider, lensFacing, previewView)`, rerunning on first successful provider/view acquisition and whenever the user flips the camera.

- `Preview.Builder().build()` wired to `previewView.surfaceProvider`.
- One `ImageCapture` built with `.setCaptureMode(CAPTURE_MODE_MAXIMIZE_QUALITY)` — every mode is either a single deliberate still shot (ID classification, OCR, fingerprint ORB matching, face embedding all need crisp detail) or, for face-match's *live* verify step, a separate analysis stream that doesn't touch `ImageCapture` at all.
- `useCases` starts as `[preview, capture]`; only when `mode == FACE_MATCH` an `ImageAnalysis` use case is added (`ResolutionSelector` pinned to 640×480, `STRATEGY_KEEP_ONLY_LATEST` backpressure), with `livenessAnalyzer` set as its analyzer on a dedicated single-thread executor.
- `provider.unbindAll()` then `bindToLifecycle(...)`. On success: `hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()`, `zoomRatio` resets to 1f, and **`torchOn = isFingerprintMode`**.

**Torch/flash logic:** a second `LaunchedEffect(torchOn, camera)` calls `camera?.cameraControl?.enableTorch(torchOn)` if `hasFlashUnit`. The manual flash toggle button is only rendered `if (hasFlashUnit && !isFingerprintMode)` — **fingerprint mode has no manual toggle**, since `torchOn` is forced `true` every camera (re)bind in fingerprint mode. Deliberate: fingerprint capture needs *consistent* illumination across up to 8 captures in one enroll session, so the torch auto-enables rather than depending on the user remembering; every other mode keeps the torch off by default and user-toggleable.

Pinch-zoom and tap-to-focus are wired via `pointerInput` modifiers on the `AndroidView` (`detectTransformGestures` → `setZoomRatio`, `detectTapGestures` → `FocusMeteringAction`). Lens flip is a bottom-start icon button toggling `lensFacing`.

**6.4.3 Per-mode resource initialization**

Four heavyweight, mode-gated resources, each `remember(mode)` + a matching `DisposableEffect` cleanup:

- **`idCardClassifier`** — only for `ID_CARD` or `OCR`. `ID_CARD` uses it directly; `OCR` uses it as the same capture gate the Python backend's `scan_single_image` used, before running OCR.
- **`faceEmbedder`** — only for `FACE_MATCH`. Produces the embedding for both the stored ID photo and live selfie.
- **`stillFaceLandmarker`** (via `FaceLandmarkerProvider.createForStillImages()`) — for `FACE_MATCH` **or** `OCR`. OCR needs it to crop the CNIC's printed photo out of the card image; FACE_MATCH needs it to re-run landmark detection on the *stored* ID photo bitmap to re-embed it.
- **`liveFaceLandmarker`** — only for `FACE_MATCH`, via the streaming `FaceLandmarkerProvider.create()` overload, callback-driven per analyzed frame.
- **`livenessChecker`** — only for `FACE_MATCH`; tracks EAR state across frames.
- **`livenessAnalyzer`** — `remember(livenessChecker, liveFaceLandmarker)`, the `ImageAnalysis.Analyzer` actually attached to the use case.

Only `FACE_MATCH` constructs `faceEmbedder`/`liveFaceLandmarker`/`livenessChecker`/`livenessAnalyzer` — the other four modes never touch MediaPipe/embedding resources at all.

**6.4.4 The ID-card-photo loading `LaunchedEffect` (Face Matching)**

Keyed on `mode`, returns immediately for every mode except `FACE_MATCH`. For face-match:
1. `statusText = "Loading your ID card photo..."`.
2. Off-main-thread: `idCardPhotoRepository.loadLatest()` fetches the most-recently-saved entity (or `null`); `.decode(entity)` → `Mat`; `extractFaceEmbedding(stillFaceLandmarker!!, faceEmbedder!!, photoMat)` runs detection+alignment+embedding, `null` if no face found.
3. If nothing loaded: `statusText = "No ID card on file — run \"Perform OCR\" first, then come back here to verify."`, capture button stays disabled (`enabled = mode != FACE_MATCH || idEmbeddingResult != null`).
4. Otherwise `idEmbeddingResult`/`idCardPhotoBitmap` populated, `statusText = "Ready — look at the camera and blink naturally to verify."`.

**Why Face Matching no longer captures its own ID photo:** Face Matching verifies a live liveness-gated selfie against the ID card photo **OCR already extracted and saved**, rather than re-photographing/re-classifying the card. This mirrors the original Python backend's design exactly (`mod_face.py`'s `latest_id_photo_path()`): run OCR once, verify against that saved photo any number of times. This is why `idCardClassifier` is *not* constructed for `FACE_MATCH` — there is no capture-and-classify-a-card step left in this mode. Practically, the user must run "Perform OCR" successfully at least once before Face Matching can do anything.

**6.4.5 The live liveness/embedding callback**

The `onResult` callback passed into `FaceLandmarkerProvider.create()` when constructing `liveFaceLandmarker`. Fires once per analyzed frame, only in `FACE_MATCH` mode:
1. `val wasPassed = livenessChecker.passed` — snapshot before this frame.
2. `livenessChecker.update(result.faceLandmarks().firstOrNull(), mpImage.width, mpImage.height)`.
3. `livenessAnalyzerRelay[0]?.notifyResultProcessed()` — clears the analyzer's busy flag.
4. Debug log of `faceSeen`/`lastEar`/`elapsedSec`.
5. **The pass-transition check:** `if (!wasPassed && livenessChecker.passed && liveEmbeddingResult == null)` — fires exactly once per attempt, on the frame where `passed` flips false→true. On that frame: get the bitmap via `lastLiveBitmapRelay[0] ?: BitmapExtractor.extract(mpImage)`, convert to Mat, `extractFivePoints()` → `alignFace()` → `faceEmbedder.embed()`, store `liveEmbeddingResult`. Exceptions are swallowed silently (leaves it null so the wait loop times out rather than crashing).

**The `lastLiveBitmapRelay` bitmap-relay trick** — a bug workaround. The "obvious" way to get the pixel bitmap for `mpImage` is `BitmapExtractor.extract(mpImage)`, but on the test device that round-trip came back with visibly corrupted colors (channel fringing), badly distorting the embedding even though **landmark detection** on the same `MPImage` worked fine — exactly why a standalone blink-liveness test (landmarks only) never caught this. Fix: `BlinkLivenessAnalyzer`'s constructor callback stashes the *exact original* `Bitmap` into `lastLiveBitmapRelay[0]` before submitting it to `detectAsync()`; the `onResult` callback reads that stashed bitmap directly instead of re-extracting from `mpImage`. `BitmapExtractor.extract()` is kept only as a defensive fallback. Safe against races because `BlinkLivenessAnalyzer`'s `busy` flag guarantees a new frame's bitmap can't overwrite the relay slot before the current frame's `onResult` has read it.

**6.4.6 The main capture button's `onClick` — per-mode walkthrough**

Two top-level arms: `mode == FACE_MATCH` (handled entirely inline — a *live* verification loop, no still capture) and `else` (calls the shared `captureImage()` helper, then branches inside `onSaved`).

**`FACE_MATCH` branch:**
1. Bails early with "No ID card on file..." if `idEmbeddingResult == null`.
2. `statusText = "Get ready..."`, `delay(500)`.
3. `livenessChecker?.reset()`, `liveEmbeddingResult = null`, `statusText = "Blink naturally..."`.
4. Polls (150ms cadence) up to a 9-second deadline for `liveEmbeddingResult` to become non-null or `livenessChecker.timedOut`.
5. Nothing captured in time → "No blink detected — try again".
6. Otherwise `matchFaces(idResult.embedding, liveResult.embedding)` → `statusText` = "MATCH (xx.x%)" or "NO MATCH (xx.x%)". `faceMatchImages` is always populated (win or lose) so the comparison can be inspected regardless of outcome. `showSuccessDialog = true` only if matched.

**`else` branch** — `captureImage()` takes one still photo; `onSaved` sets "Processing..." and launches a coroutine switching on `mode`:
- **`ID_CARD`**: `fileToMat()` → `idCardClassifier!!.classify(mat)`. Status = "ID Card detected (0.NN)" or "Not an ID card — try again"; dialog shown only if `isIdCard`.
- **`OCR`**: `fileToMat()` → `scanSingleImage(idCardClassifier!!, { TesseractEngine(context) }, stillFaceLandmarker!!, mat)`.
  - Not ok: `statusText = result.message`, `ocrIdPhoto = null`.
  - Ok: status = "${message} (${found}/6)"; `ocrResultText` built from joined `"key:\nvalue"` fields; `ocrIdPhoto = result.idPhoto?.let { matToBitmap(it) }`.
  - **Crucially**: `result.idPhoto?.let { photo -> withContext(Dispatchers.Default) { idCardPhotoRepository.save(photo, System.currentTimeMillis()) } }` — every successful OCR run overwrites the single saved ID-card photo record Face Matching later loads. This is the persistence side of the OCR→FaceMatch handoff.
  - Dialog shown if `found > 0 || result.allFieldsFound` (partial extraction still shown, letting the user judge whether to retake).
- **`FINGERPRINT_ENROLL`**: `FingerprintEnrollmentService.enroll(fingerprintRepository, personName, hand.name, mat)`. Dialog shown if `result.ok`.
- **`FINGERPRINT_MATCH`**: `FingerprintVerificationService.verify(fingerprintRepository, hand.name, mat)`. Dialog shown if `result.matched`.

Any exception is caught and turned into "Processing failed: ..." / "Upload failed: ..." depending on `isOnDeviceMode`.

**6.4.7 Success/review dialogs**

**Main `showSuccessDialog`** — one dialog reused across all modes, not dismissible by tapping outside. Title "Review Details" for OCR, "SCAN SUCCESSFUL!" otherwise. Body:
- **OCR**: reminder text + `ocrIdPhoto` thumbnail (labeled "Face captured from ID card") + `ocrResultText`.
- **FACE_MATCH**: `statusText` (MATCH/NO MATCH line) + "View Match" button opening `showFaceCompareDialog`.
- **fingerprint modes**: `statusText` directly.
- **`hand != NONE` fallback**: "${hand.label} captured successfully."
- **else**: generic "Capture completed successfully."

Buttons: OCR gets "Retake" (dismiss + `statusText = "Retrying capture..."`) alongside a half-width "Accept"; every other mode gets only a full-width "Continue". Both confirm paths: `showSuccessDialog = false; onScanSuccess()` — where `KycNavGraph`'s branching takes over.

**`showFaceCompareDialog`** — only relevant when `faceMatchImages != null`. Two-column row: "ID Photo" / "Live Capture", 150dp tall, `ContentScale.Fit`. Single "Close" button. Reachable both from the inline "View Compared Faces" button under the live preview, and from "View Match" inside the success dialog.

**6.4.8 Helper functions**

- **`drawDShapeGuide()` / `drawCrosshairReticle()`** — `DrawScope` extensions for the fingerprint-mode `Canvas` overlay (white D-shaped hand-placement guide + crosshair). Horizontally mirrored (`scale(scaleX = -1f, ...)`) for `HandSide.RIGHT` rather than redrawn, since the thumb side flips between hands.
- **`captureButtonLabel(mode): String`** — maps each mode to its button text ("Capture ID Card", "Capture for OCR", "Capture Face", "Capture Fingerprint (Enroll)", "Capture Fingerprint (Match)"). Note `FACE_MATCH`'s button actually shows "Start Face Verification" instead, set inline.
- **`fileToMat(file: File): Mat`** — decodes the JPEG, applies `applyExifRotation`, converts to OpenCV Mat (RGBA→BGR). Must run off-main-thread (every call site wraps it in `withContext(Dispatchers.Default)`).
- **`applyExifRotation(file, bitmap): Bitmap`** — reads `ExifInterface.TAG_ORIENTATION` and applies the matching rotation/flip, falling back to the original bitmap for `ORIENTATION_NORMAL` or a read failure. **Why this exists:** CameraX writes JPEGs in sensor orientation plus a separate EXIF tag saying how to display upright — `BitmapFactory.decodeFile()` ignores that tag entirely, so a phone held normally decodes sideways unless corrected. Specific to **real devices**: the emulator's synthetic camera never produced EXIF rotation tags at all, so this bug was invisible until real-device testing.
- **`matToBitmap(bgr: Mat): Bitmap`** — `cvtColor(BGR2RGBA)` then `Utils.matToBitmap`. Used for aligned face-match crops and the stored ID card photo.
- **`captureImage(context, imageCapture, mode, hand, onSaved, onError)`** — builds an output filename under `context.filesDir/captures/`, calls `imageCapture.takePicture(...)`. Guards against a null `imageCapture` (camera not yet bound) by calling `onError("Camera not ready")` immediately.

### 6.5 Cross-file wiring summary

- `MainActivity` → `KycNavGraph` (only navigation root).
- `KycNavGraph` is the only place that constructs screens and knows route string shapes; all five small screens and `CameraCaptureScreen` are pure callback-driven composables with no direct `NavController` reference.
- `CameraCaptureScreen` is registered once under `Routes.CAMERA`, reached from `ID_CARD_INTRO`/`OCR_INTRO`/`FACE_MATCH_INTRO` (direct) and `FINGERPRINT_TUTORIAL` (via `onScanClick`) for both enroll and match.
- The `IdCardPhotoRepository` created inside `CameraCaptureScreen` is the sole link between the `OCR` and `FACE_MATCH` modes of that same composable — OCR writes to it, Face Matching reads from it on entry — there is no ViewModel mediating that handoff.

---


---

## 7. Fingerprint Biometrics (`biometrics/fingerprint`) and the vendored WSQ codec (`org/jnbis`)

This section documents the on-device fingerprint enrollment/verification pipeline — a line-by-line Kotlin port of a Python/OpenCV backend module referred to throughout the code comments as `mod_fingerprint.py`. It also covers `IdCardClassifier.kt` (a sibling biometrics component, ported from `mod_id_card.py`/`mod_ocr.py`) and the vendored `org.jnbis` WSQ (Wavelet Scalar Quantization) codec used for one of the fingerprint export formats.

All fingerprint package files live under `biometrics/fingerprint/`. Everything in this package operates on OpenCV `Mat` objects (never `Bitmap`/`ImageProxy` directly) and has no Android-framework dependency except `MatSerialization.kt` (Room BLOBs) and `FingerprintExportFormats.kt` (`android.util.Base64`).

### 7.1 End-to-end flow (enrollment and verification)

1. **Capture.** `CameraCaptureScreen.kt` shows a D-shaped on-screen guide box positioned at 2% left / 14% top / 92% width / 62% height of the camera preview, and captures a full-resolution hand photo as an OpenCV `Mat` (BGR).
2. **ROI crop.** `fingerprintRoi()` (`FingerprintRoi.kt`) recomputes the *identical* box fractions against the captured frame's own pixel dimensions (not the screen's), guaranteeing the crop is exactly what the user saw framed on screen regardless of camera resolution/aspect ratio. Two crops are produced:
   - `cropRoiRaw()` — the guide box at native captured resolution, no resizing.
   - `cropRoi()` — the same crop resized (`INTER_AREA` downscaling, `INTER_CUBIC` upscaling) to a canonical width of 460px, preserving the guide box's own aspect ratio (no forced square, no distortion).
3. **Preprocessing (matching path only).** `cropRoi()`'s canonical-size image is converted to grayscale, run through CLAHE contrast equalization, an 8-orientation Gabor ridge-enhancement filter bank, and a final Gaussian blur (`preprocessFingerprint()`).
4. **Feature extraction.** The preprocessed image is fed to both an ORB detector and an AKAZE detector with tuned parameters (`extractFeatures()`), producing a `FeatureSet` (keypoints + descriptors for both algorithms, plus the processed Mat).
5. **Quality gates.** Sharpness (Laplacian variance) and minimum ORB feature count are checked before anything is accepted, for both enroll and verify.
6. **Enroll branch** (`FingerprintEnrollmentService.enroll()`): rejects blank names, too-blurry captures, too-few-features captures; runs a duplicate check against every existing stored sample for that person/hand (rejects if any score ≥ `ENROLL_DUPLICATE_THRESHOLD`); on success calls `repository.saveSample()`, which round-robins into one of `MAX_ENROLL_SAMPLES` (3) sample slots per person/hand, serializes the ORB/AKAZE keypoints+descriptors for Room, and generates the WSQ/Base64/ISO export triple from the **raw, un-enhanced, native-resolution** crop, not the CLAHE/Gabor-processed matching image.
7. **Verify branch** (`FingerprintVerificationService.verify()`): loads all stored templates for the given hand across all enrolled people; runs the same crop → preprocess → extract-features → quality-gate pipeline on the live capture; scores the query against every stored template, keeping the best score per person, then picks the best-scoring person overall; declares a match if the best score ≥ `VERIFY_THRESHOLD` (0.20).
8. **Review.** `FingerprintRecordViewerScreen.kt` lists enrolled records, lets the user preview each in all four representations — Original (decoded from the lossless Base64 PNG), WSQ (lossy), Base64 (raw text), ISO 19794-4 — and permanently delete a record.

### 7.2 `FingerprintRoi.kt`

**What/why:** Defines the single source of truth for "what part of the captured photo is the fingerprint." Ported from an earlier arbitrary-centered-square crop to instead crop *exactly* the region the D-shaped on-screen guide box frames, so what the user visually aimed at is what gets matched and stored. A comment notes this must be kept in sync manually with `CameraCaptureScreen.kt`'s guide box, since the two are drawn/computed in entirely different places.

**Declarations:**
- `fingerprintRoi(mat: Mat): Rect` — computes the guide-box `Rect` in captured-frame pixel coordinates from `GUIDE_LEFT_FRACTION`/`GUIDE_TOP_FRACTION`/`GUIDE_WIDTH_FRACTION`/`GUIDE_HEIGHT_FRACTION` applied to `mat.cols()`/`mat.rows()`.
- `cropRoiRaw(mat: Mat): Mat?` — crops that `Rect` out of `mat` at native resolution (an OpenCV sub-Mat view, not a copy); `null` if width/height is non-positive.
- `cropRoi(mat: Mat): Mat?` — calls `cropRoiRaw()`, then resizes to `CANONICAL_WIDTH` (460px) wide if not already, deriving height proportionally.

**Callers:** `FingerprintEnrollmentService.kt` (both `cropRoi()` for matching and `cropRoiRaw()` for the raw export source), `FingerprintVerificationService.kt` (`cropRoi()` only).

**Constants:**

| Constant | Value | Meaning |
|---|---|---|
| `GUIDE_LEFT_FRACTION` | 0.02 | Guide box left edge, fraction of frame width |
| `GUIDE_TOP_FRACTION` | 0.14 | Guide box top edge, fraction of frame height |
| `GUIDE_WIDTH_FRACTION` | 0.92 | Guide box width, fraction of frame width |
| `GUIDE_HEIGHT_FRACTION` | 0.62 | Guide box height, fraction of frame height |
| `CANONICAL_WIDTH` | 460 | Fixed width (px) the matching pipeline normalizes crops to |

These fractions are identical to `CameraCaptureScreen.kt`'s on-screen guide box drawing.

### 7.3 `FingerprintPreprocessing.kt`

**What/why:** Image-enhancement stage making ridge structure easier for feature detectors to lock onto, applied only to the matching pipeline (never the export images). Ports `preprocess_fingerprint()`. Explicitly does **not** port the Python module's older CLAHE-only fallback path (kept there historically for backward compatibility with pre-Gabor pickle templates) — since this is a brand-new on-device database with no legacy templates, that dead branch was dropped.

**Declarations:**
- `gaborKernels: List<Mat>` (private, lazy) — 8 Gabor kernels evenly spaced across 0..π, each via `Imgproc.getGaborKernel(Size(15,15), sigma=3.0, theta, lambda=8.0, gamma=0.5, psi=0.0, CV_32F)`.
- `gaborEnhance(gray: Mat): Mat` (private) — for each of the 8 kernels: filters the image, takes absolute response, keeps a running per-pixel maximum across all orientations. At any pixel, whichever orientation best matches the local ridge direction dominates the output. Normalized to 0–255, converted to 8-bit.
- `preprocessFingerprint(bgr: Mat): Mat` — BGR→grayscale, `CLAHE(clipLimit=3.0, tileGridSize=8x8)`, `gaborEnhance()`, final `GaussianBlur(3x3)` to smooth filtering artifacts.

**Callers:** `FingerprintFeatureExtractor.extractFeatures()` only.

**Constants:**

| Constant | Value | Meaning |
|---|---|---|
| `GABOR_ORIENTATIONS` | 8 | Filter bank orientation count |
| `GABOR_KSIZE` | 15 | Gabor kernel size |
| `GABOR_SIGMA` | 3.0 | Gaussian envelope std-dev |
| `GABOR_LAMBDA` | 8.0 | Wavelength |
| `GABOR_GAMMA` | 0.5 | Spatial aspect ratio |
| CLAHE clip limit | 3.0 | Inline in `preprocessFingerprint()` |
| CLAHE tile grid | 8×8 | Inline in `preprocessFingerprint()` |

### 7.4 `FingerprintFeatureExtractor.kt`

**What/why:** Runs ORB (fast, binary, rotation-invariant) and AKAZE (nonlinear-scale-space, finds complementary keypoints on fine ridge texture) on the preprocessed image. Ports `_make_orb()`/`_make_akaze()` with parameters copied 1:1.

**Declarations:**
- `makeOrb(): ORB` (private) — `ORB.create(nfeatures=800, scaleFactor=1.2, nlevels=8, edgeThreshold=15, firstLevel=0, WTA_K=2, scoreType=HARRIS_SCORE, patchSize=31, fastThreshold=10)`.
- `makeAkaze(): AKAZE` (private) — `AKAZE.create(DESCRIPTOR_MLDB, 0, 3, threshold=0.0008, nOctaves=4, nOctaveLayers=4)`.
- `data class FeatureSet(kpOrb, desOrb, kpAkaze, desAkaze, processed)`.
- `extractFeatures(bgr: Mat): FeatureSet` — `preprocessFingerprint(bgr)` once, then `ORB.detectAndCompute()` and `AKAZE.detectAndCompute()` against that single preprocessed Mat.

**Callers:** `FingerprintEnrollmentService.enroll()` and `FingerprintVerificationService.verify()` call `extractFeatures()` directly on the `cropRoi()` output.

**Constants:**

| Constant | Value | Meaning |
|---|---|---|
| `ORB_FEATURES` | 800 | Max ORB keypoints |
| ORB scaleFactor | 1.2f | Pyramid scale factor |
| ORB nlevels | 8 | Pyramid levels |
| ORB edgeThreshold | 15 | Undetected border width |
| ORB WTA_K | 2 | Points per BRIEF element |
| ORB scoreType | HARRIS_SCORE | Ranking method |
| ORB patchSize | 31 | Descriptor patch size |
| ORB fastThreshold | 10 | FAST corner threshold |
| `AKAZE_THRESHOLD` | 0.0008f | Detector response threshold |
| AKAZE descriptor type | DESCRIPTOR_MLDB | Binary descriptor |
| AKAZE nOctaves/nOctaveLayers | 4/4 | Scale-space depth |

### 7.5 `FingerprintMatcher.kt`

**What/why:** The scoring engine — given two `FingerprintTemplate`s, produces a similarity score roughly in [0,1]. Ports `_ratio_match`/`_ransac_inlier_ratio`/`_one_way_score`/`_symmetric_score`/`_match_score`, constants identical to the Python original. The legacy CLAHE-only ORB fallback branch is intentionally dropped, same reasoning as `FingerprintPreprocessing.kt`.

**Declarations:**
- `data class FingerprintTemplate(orbKeypoints, orbDescriptors, akazeKeypoints, akazeDescriptors)`.
- `ratioMatch(desA, desB, minGood): List<DMatch>` (private) — brute-force Hamming-distance KNN (k=2), keeping a match only if `best.distance < RATIO_TEST_THRESHOLD * secondBest.distance` (0.70). Fewer than `minGood` survivors → empty (treated as "no match").
- `ransacInlierRatio(kpA, kpB, good): Double` (private) — fits a homography via RANSAC (`reprojThreshold=5.0`), returns the inlier fraction. 0.0 if fewer than 4 good matches.
- `oneWayScore(...): Double` (private) — `inlierRatio * strength`, where `strength = min(1.0, goodMatchCount * inlierRatio / max(minGood, 1))` — a high score requires *both* good inlier ratio *and* enough matches relative to the minimum, so a handful of coincidentally-consistent matches can't dominate.
- `symmetricScore(...): Double` (private) — averages `oneWayScore(A→B)` and `oneWayScore(B→A)`, since ratio-test matching is directionally asymmetric.
- `matchScore(a, b): Double` — computes `symmetricScore` for ORB (`minGood=8`). If both templates have AKAZE descriptors, also computes it for AKAZE (`minGood=6`) and blends `ORB_WEIGHT * orb + AKAZE_WEIGHT * akaze` (0.45/0.55). Falls back to ORB-only if either side lacks AKAZE.

**Callers:** `FingerprintEnrollmentService.enroll()` (duplicate-rejection), `FingerprintVerificationService.verify()` (scoring against every stored template).

**Constants:**

| Constant | Value | Meaning |
|---|---|---|
| `RATIO_TEST_THRESHOLD` | 0.70 | Lowe's ratio test cutoff |
| `MIN_GOOD_MATCHES_ORB` | 8 | Min survivors for nonzero ORB score |
| `MIN_GOOD_MATCHES_AKAZE` | 6 | Min survivors for nonzero AKAZE score |
| `VERIFY_THRESHOLD` | 0.20 | Minimum blended score to declare a match |
| `ORB_WEIGHT` | 0.45 | ORB blend weight |
| `AKAZE_WEIGHT` | 0.55 | AKAZE blend weight |
| RANSAC reprojection threshold | 5.0 | Inline in `ransacInlierRatio()` |

### 7.6 `FingerprintQualityGates.kt`

**What/why:** Central home for capture-quality thresholds and quality-tier labeling. Ports the Python enroll quality-gate constants unchanged.

**Declarations:**
- `enum class QualityTier(label)` — `EXCELLENT`, `GOOD`, `FAIR` ("try pressing finger more firmly"), `POOR` ("not enough detail").
- `qualityTier(orbCount: Int): QualityTier` — `>=500`→EXCELLENT, `>=300`→GOOD, `>=MIN_ENROLL_FEATURES(20)`→FAIR, else→POOR.
- `sharpness(gray: Mat): Double` — Laplacian-variance blur metric: `Imgproc.Laplacian` (CV_64F), `Core.meanStdDev`, returns stddev² (higher = sharper).

**Callers:** `FingerprintEnrollmentService.enroll()` uses both; `FingerprintVerificationService.verify()` uses `MIN_ENROLL_FEATURES` directly but not `qualityTier()`.

**Constants:**

| Constant | Value | Meaning |
|---|---|---|
| `MIN_ENROLL_FEATURES` | 20 | Minimum ORB feature count to accept a capture (enroll and verify) |
| `ENROLL_SHARPNESS_MIN` | 50.0 | Minimum sharpness to accept an enrollment capture |
| `ENROLL_DUPLICATE_THRESHOLD` | 0.85 | Match score above which a new sample is rejected as a near-duplicate |
| `MAX_ENROLL_SAMPLES` | 3 | Sample slots stored per person/hand |
| `QUALITY_FAIR` (private) | 300 | FAIR→GOOD boundary |
| `QUALITY_GOOD` (private) | 500 | GOOD→EXCELLENT boundary |

### 7.7 `MatSerialization.kt`

**What/why:** Bridges OpenCV's in-memory `Mat`/`MatOfKeyPoint` to flat `ByteArray`s for Room BLOB columns. Mirrors `_serialize_keypoints`/`_deserialize_keypoints`, keeping the same field order for round-trip parity with the Python original.

**Declarations:**
- `data class MatBlob(bytes, rows, cols, type)` — a `Mat`'s raw bytes plus shape metadata needed to reconstruct it.
- `serializeMat(mat: Mat): MatBlob` — reads `mat.total() * mat.elemSize()` bytes, packages with rows/cols/type.
- `deserializeMat(blob: MatBlob): Mat` — allocates `Mat(rows, cols, type)`, writes bytes back in.
- `serializeKeypoints(keypoints: List<KeyPoint>): ByteArray` — for each keypoint, writes 7 fields little-endian: `x, y, size, angle, response` (floats) then `octave, class_id` (ints) — 28 bytes per keypoint.
- `deserializeKeypoints(bytes): MatOfKeyPoint` — inverse, reconstructing `KeyPoint`s.

**Callers:** `FingerprintTemplateRepository.kt` — `saveSample()` serializes when writing; the private `toTemplate()` extension deserializes when reading records back for matching.

### 7.8 `FingerprintEnrollmentService.kt`

**What/why:** Orchestrates one full enrollment attempt end-to-end — direct Kotlin port of `enroll_hand()`. A stateless singleton `object`.

**Declarations:**
- `data class EnrollResult(ok, message, sample=0, max=MAX_ENROLL_SAMPLES)`.
- `object FingerprintEnrollmentService { suspend fun enroll(repository, personName, hand, frame: Mat): EnrollResult }`:
  1. Rejects blank `personName`.
  2. `cropRoi(frame)` → fails ("Invalid crop area") if too small.
  3. Also computes `cropRoiRaw(frame)` (falls back to `cropped`) and grayscales it as `fullDetailGray` — not used for matching, only saved for export generation, at full captured detail with no CLAHE/Gabor enhancement (enhancement is a matching-only concern).
  4. Grayscales the canonical `cropped` image, runs `sharpness()`; rejects below `ENROLL_SHARPNESS_MIN`.
  5. `extractFeatures(cropped)`, computes `qualityTier()`, rejects if `orbCount < MIN_ENROLL_FEATURES`.
  6. Fetches existing samples; rejects if any scores `>= ENROLL_DUPLICATE_THRESHOLD` against the new capture.
  7. On success, calls `repository.saveSample(personName, hand, features, sharp, fullDetailGray)`, returns a success message summarizing slot, quality tier, ORB/AKAZE counts, sharpness.

**Callers:** `CameraCaptureScreen.kt` calls `FingerprintEnrollmentService.enroll(...)` from the capture UI.

### 7.9 `FingerprintVerificationService.kt`

**What/why:** Orchestrates one verification attempt — port of `verify_hand()`. Also a stateless singleton.

**Declarations:**
- `data class VerifyResult(ok, matched=false, name=null, message="")`.
- `object FingerprintVerificationService { suspend fun verify(repository, hand, frame: Mat): VerifyResult }`:
  1. `repository.allForHand(hand)` — fails immediately if empty.
  2. `cropRoi(frame)` and `extractFeatures()` (no sharpness check here, only feature count via `MIN_ENROLL_FEATURES`).
  3. Scores the query against every stored `(person, template)` pair, keeping each person's *best* sample score.
  4. Picks the overall best-scoring person.
  5. `matched = bestEntry.value >= VERIFY_THRESHOLD`; returns the person's name + score on match, or the closest score + gap-below-threshold on no match.

**Callers:** `CameraCaptureScreen.kt` calls `FingerprintVerificationService.verify(...)` from the capture UI.

### 7.10 `FingerprintExportFormats.kt`

**What/why:** Generates the three portable export representations of an enrolled fingerprint, stored alongside the matching template. Port of `export_fingerprint_formats()`. Always operates on the **raw captured crop** (full native resolution, no CLAHE/Gabor), never the matching-enhanced image — matching enhancement is a machine-vision aid, not something belonging in a record meant to represent "the fingerprint as captured."

**Declarations:**
- `data class FingerprintExports(wsq, base64, iso)`.
- `exportFingerprintFormats(processedGray: Mat): FingerprintExports` — extracts raw grayscale bytes once, produces all three formats from the same source.
- `grayBytes(mat: Mat): ByteArray` (private).
- `encodeWsq(gray, width, height): ByteArray` (private) — wraps bytes in `org.jnbis.Bitmap(gray, width, height, ppi=FINGERPRINT_PPI, depth=8, lossyflag=1)`, calls `WSQEncoder.encode(outputStream, bitmap, WSQ_BIT_RATE)`. `FINGERPRINT_PPI=500` and `WSQ_BIT_RATE=0.75` match NBIS's own default fingerprint compression (`cwsq -r 0.75`); 500dpi is also baked into the ISO header, so the two formats are internally consistent about capture resolution (a convention carried from the fingerprint-scanner domain this format was designed for, even though a phone photo isn't literally 500dpi).
- `encodeBase64Png(gray: Mat): String` (private) — PNG-encodes then Base64s (`Base64.NO_WRAP`). This is the losslessly-reversible "Original" representation used elsewhere.
- `encodeIso19794(gray, width, height): ByteArray` (private) — hand-built, byte-for-byte port of `_write_iso19794_record`: a **44-byte** big-endian header followed by raw 8-bit grayscale pixels. Header layout:

  | Bytes | Field | Value |
  |---|---|---|
  | 0–3 | Format ID | `"FIR\0"` |
  | 4–7 | Version | `"020\0"` |
  | 8–11 | Record length | `44 + gray.size` |
  | 12–19 | Reserved | `0`s |
  | 20 | byte | `1` |
  | 21 | byte | `1` |
  | 22–29 | Resolution fields (H/V, ×2) | `500` each |
  | 30 | Bit depth | `8` |
  | 31–38 | Reserved/flags | mostly `0`, bytes 35/36 = `1` |
  | **39–40** | **Image width** | `width` |
  | **41–42** | **Image height** | `height` |
  | 43 | byte | `0` |

  A `check(header.position() == ISO_HEADER_BYTES)` assertion guards against the field list ever drifting from exactly 44 bytes. Final record is `header.array() + gray`.
- `decodeWsq(bytes): Triple<ByteArray, Int, Int>` — `WSQDecoder.decode()` → `(pixels, width, height)`.
- `decodeIso19794(bytes): Triple<ByteArray, Int, Int>` — port of `read_iso19794_record()`: reads width/height from big-endian offsets 39 and 41, slices pixels from offset 44.
- `decodeBase64Png(base64): Triple<ByteArray, Int, Int>` — Base64-decode then `Imgcodecs.imdecode(..., IMREAD_GRAYSCALE)`.

**Callers:** `FingerprintTemplateRepository.saveSample()` calls `exportFingerprintFormats()`. `FingerprintRecordViewerScreen.kt` calls all three decode functions for the four preview tabs (Original/WSQ/Base64/ISO).

**Constants:**

| Constant | Value | Meaning |
|---|---|---|
| `WSQ_BIT_RATE` | 0.75 | Matches NBIS's `cwsq -r 0.75` default |
| `FINGERPRINT_PPI` | 500 | Resolution metadata in both WSQ bitmap and ISO header |
| `ISO_HEADER_BYTES` | 44 | Fixed header size |

### 7.11 `IdCardClassifier.kt`

**What/why:** Not part of the fingerprint pipeline, but a sibling biometrics component (`biometrics/idcard`). A TFLite binary classifier deciding whether a captured frame is an ID card. Port of `predict_id_card_roi()` combined with `to_card_aspect()`.

**Declarations:**
- `data class IdCardResult(label, confidence, isIdCard)`.
- `class IdCardClassifier(context: Context)` — loads `id_card_classifier.tflite` into a memory-mapped `Interpreter`.
  - `classify(bgr: Mat): IdCardResult` — crops to card aspect ratio (`toCardAspect()`), preprocesses, runs inference. Single output `logit`: `label = "ID Card"` if `logit > 0f` else `"Not ID Card"`, `confidence = abs(logit)`.
  - `toCardAspect(bgr: Mat): Mat` (private) — the model was trained only on a fixed 400×250 (~1.6:1) live-webcam crop, so a phone photo must be center-cropped to that same ratio first or confidence degrades even on a genuinely good photo.
  - `preprocess(bgr: Mat)` (private) — grayscales, resizes to 128×128, builds a `[1][128][128][3]` float tensor where the grayscale value is *replicated* into all three "RGB" channels, matching the Python training pipeline.
  - `close()`.

**Callers:** `CameraCaptureScreen.kt` — instantiated only for `ID_CARD` or `OCR` mode.

**Constants:**

| Constant | Value | Meaning |
|---|---|---|
| `MODEL_ASSET` | `"id_card_classifier.tflite"` | Bundled model |
| `IMAGE_SIZE` | 128 | Model input resolution |
| `ROI_WIDTH`/`ROI_HEIGHT` | 400/250 | Training-time aspect ratio (1.6:1) |

### 7.12 The vendored `org.jnbis` WSQ codec

**Where it came from:** `app/src/main/java/org/jnbis/` contains 7 files (`Bitmap.java`, `BitmapWithMetadata.java`, `WSQEncoder.java`, `WSQDecoder.java`, `WSQHelper.java`, `NISTConstants.java`, `WSQConstants.java`) — a vendored Java port of JNBIS 1.0.3 (Apache License 2.0), itself derived from NBIS (NIST Biometric Image Software, public domain), further modified under a mixed LGPL 2.1/Apache 2.0 license per each file's header comment. It traveled through an old JMRTD-fork lineage before landing here. Pulled into this project specifically because it has **zero AWT/ImageIO dependencies** — the original JNBIS/NBIS Java code typically leans on `java.awt.image.BufferedImage`, which doesn't exist on Android; this vendored copy works with raw `byte[]` pixel buffers via `Bitmap`/`BitmapWithMetadata` instead, the only reason it's usable in an Android app at all.

**Public API surface:**
- `Bitmap` — plain data holder: `width, height, ppi, depth, lossyflag, pixels: byte[], length`.
- `BitmapWithMetadata extends Bitmap` — adds `Map<String,String> metadata` and `List<String> comments`, used by the decoder to surface WSQ file comment/metadata fields.
- `WSQEncoder` — `public class WSQEncoder implements WSQConstants, NISTConstants`. Four static `encode(...)` overloads funneling into one implementation; this app only calls the simplest `encode(OutputStream, Bitmap, double)` overload from `FingerprintExportFormats.encodeWsq()`.
- `WSQDecoder` — `decode(InputStream): BitmapWithMetadata` and `decode(DataInput): BitmapWithMetadata` (plus internal helpers). This app calls `decode(InputStream)` from `decodeWsq()`.
- `WSQHelper` — internal utility (`MAX_SUBBANDS = 64`, plus generic `Token<T>`/`Ref<T>` mutable-holder helpers used to pass values by reference through the C-derived algorithm). Not called directly by app code.
- `NISTConstants`/`WSQConstants` — marker interfaces holding the magic-number tables (Huffman tables, quantization tables, subband filter taps) driving the wavelet compression math.

The dense algorithm internals of `WSQEncoder`/`WSQDecoder` (~1000+ lines each: forward/inverse wavelet transform, quantization, Huffman coding) are a faithful transcription of NIST's C reference implementation, not meant to be hand-edited.

**Only caller in the app:** `FingerprintExportFormats.kt` — `encodeWsq()` calls `WSQEncoder.encode()`, `decodeWsq()` calls `WSQDecoder.decode()`. No other file references `org.jnbis`.

---


---

## 8. OCR Pipeline (`biometrics/ocr`)

### Overview

This package takes one phone-captured photo of a Pakistani CNIC (national ID card), rectifies and upscales it, runs it through Tesseract with several image renderings and page-segmentation modes in parallel, then applies a large amount of hand-tuned heuristic logic to pull out six text fields — **Name, Father Name, Identity Number, Date of Birth, Date of Issue, Date of Expiry** — plus a cropped photo of the face printed on the card. Everything happens on-device; there is no server call and no cloud OCR API.

The package is a close Kotlin port of an original Python prototype (`mod_ocr.py`, referenced throughout in comments) built for a webcam-based, multi-capture desktop tool. Several files explicitly note they are "1:1 ports" and that the design choices exist to fix specific real bugs seen with real cards, not stylistic preferences — so nearly every constant and branch has a concrete failure mode behind it.

The only external entry point is `scanSingleImage()` in `OcrService.kt`, called from `CameraCaptureScreen.kt` after a photo is captured in `CaptureMode.OCR`. No other file in the app touches this package directly — everything else (`ImageRectification`, `OcrVariants`, `TesseractEngine`, `OcrReader`, `CnicPatterns`, `NameExtraction`, `LabelMatching`, `CnicIdPhotoExtractor`) is consumed only from within `CnicFieldExtractor.kt` / `OcrService.kt` themselves.

### 8.1 `ImageRectification.kt`

**What/why:** OpenCV-based geometric preprocessing — finds the card's quadrilateral in the photo and perspective-warps it flat, straightens residual tilt, and picks a resize scale for OCR. Ports `_perspective_warp`, `_deskew`, and `_warp_and_upscale`.

**Declarations:**
- `orderCorners(pts)` (private) — sorts 4 unordered corner points into `[topLeft, topRight, bottomRight, bottomLeft]` via the standard "sum/diff" trick (min/max of `x+y` for TL/BR, min/max of `y-x` for TR/BL).
- `perspectiveWarp(roi: Mat): Pair<Mat, Boolean>` — grayscale → Gaussian blur (5×5) → Canny (30–120) → 3×3 dilation → external contours, largest-area-first. For each: `approxPolyDP` (epsilon 2% of perimeter) must yield exactly 4 vertices; area must be ≥55% of frame area; aspect ratio must be within `ASPECT_TOLERANCE=0.25` of the real CNIC ratio `CNIC_ASPECT=85.6/54.0` (physical ID-1 card mm dimensions). First quad passing all checks is perspective-transformed flat and returned with `true`; otherwise returns the original unchanged with `false`.
- `deskew(img: Mat): Mat` — lighter fallback for when no clean quad was found. Grayscale → Canny (60–180) → probabilistic Hough (`HoughLinesP`) to find long near-horizontal lines (angle within `DESKEW_MAX_DEG=12.0`). Needs ≥4 such lines or no-ops. Takes the **median** angle (robust to outlier lines); if below `DESKEW_MIN_DEG=0.4` treats it as noise and no-ops; otherwise rotates by `-angle` with `BORDER_REPLICATE`.
- `warpAndUpscale(roiIn: Mat): Mat` — tries `perspectiveWarp` first, falls back to `deskew` on the original if it failed. Then resizes toward `UPSCALE_TARGET_W=1800.0`px: upscales (clamped `[UPSCALE_MIN=2.0, UPSCALE_MAX=5.0]`×) if smaller, downscales (floor `DOWNSCALE_MIN=0.5`×) if larger; Lanczos4 for upscale, Area for downscale.

**Callers:** `warpAndUpscale` is called once per scan, from `OcrReader.readText()`.

**Constants:** `CNIC_ASPECT≈1.585`, `ASPECT_TOLERANCE=0.25`, `DESKEW_MAX_DEG=12.0`, `DESKEW_MIN_DEG=0.4`, `UPSCALE_TARGET_W=1800.0`, `UPSCALE_MIN/MAX=2.0/5.0`, `DOWNSCALE_MIN=0.5`.

### 8.2 `OcrVariants.kt`

**What/why:** Produces several different image renderings of the rectified card, each paired with the Tesseract page-segmentation configs to run it through — redundancy across binarizations rather than picking one "best" rendering up front.

**Declarations:**
- `data class TessConfig(pageSegMode, variables)`. Predefined: `TESS_BLOCK` (PSM_SINGLE_BLOCK + preserve spacing), `TESS_SPARSE` (PSM_SPARSE_TEXT), `TESS_COLUMN` (PSM_SINGLE_COLUMN + preserve spacing), `TESS_DIGITS_LINE`/`TESS_DIGITS_WORD` (PSM_SINGLE_LINE/WORD, digit-only whitelist `"0123456789-"`, `classify_bln_numeric_mode=1`) — exported (non-private) since `CnicFieldExtractor.refineCnicDigits` reuses them.
- `data class OcrVariant(name, image, configs)`.
- `ocrVariants(colorUpscaled: Mat): List<OcrVariant>` — builds three renderings:
  1. Grayscale → CLAHE (clip 2.0, tile 8×8).
  2. Bilateral filter (`d=7, sigmaColor=sigmaSpace=45`) on the CLAHE output.
  3. From the smoothed image: **adaptive threshold** (Gaussian, 31×31, constant 10) → `"adaptive"` variant × [`TESS_BLOCK`, `TESS_SPARSE`]; **Otsu threshold** → `"otsu"` variant × [`TESS_BLOCK`]; **unsharp mask** (`addWeighted(clahe, 1.7, blur(sigma 2.0), -0.7, 0)`) → `"sharp"` variant × [`TESS_BLOCK`, `TESS_COLUMN`].

  Total: **5 Tesseract passes** per scan, each on a different image/config, later merged and deduplicated.

**Callers:** Called once per scan from `OcrReader.readText()`.

### 8.3 `TesseractEngine.kt`

**What/why:** Thin wrapper around `TessBaseAPI`.

**Declarations:**
- `data class OcrLine(text, conf, left, top, width, height, image: Mat, variant: String, yFrac: Double)` — one reconstructed OCR text line. `image`/`variant` let `refineCnicDigits` later re-crop and re-OCR the exact same source pixels at higher resolution. `yFrac` = vertical center as a fraction of card height (0=top,1=bottom) — the coordinate used throughout `CnicFieldExtractor` for row-proximity heuristics.
- `class TesseractEngine(context)` — init copies `eng.traineddata` from assets to `filesDir/tesseract/tessdata/` once (Tesseract needs a real filesystem path, can't stream from APK assets), then `api.init(...)` with `"eng"`/`OEM_DEFAULT`.
  - `recognizeLines(bitmap, pageSegMode, variables, variant, variantImage, cardHeightPx): List<OcrLine>` — sets PSM/variables, walks Tesseract's native `resultIterator` at `RIL_TEXTLINE` granularity (native line grouping, not manual word-row regrouping). Confidence normalized 0–100→0–1.
  - `recognizeText(bitmap, pageSegMode, variables): String` — simpler single-string form, used only by the CNIC digit-refinement pass.
  - `close()` — `api.recycle()`.

**Callers:** Instantiated via a factory lambda (`() -> TesseractEngine`), not a shared singleton — `CameraCaptureScreen.kt` passes `{ TesseractEngine(context) }`. Both `OcrReader.readText()` and `OcrService.scanSingleImage()` create a **fresh engine per Tesseract pass**, closing immediately after — deliberate, since `TessBaseAPI` isn't documented safe for concurrent multi-thread use on one instance, and `readText()` fires 5 passes concurrently.

### 8.4 `OcrReader.kt`

**What/why:** Orchestrates "every image variant × every configured pass, in parallel, merged into one deduplicated flat list." Ports `_read_text()`.

**Declarations:**
- `MIN_LINE_CONFIDENCE = 0.30` (private).
- `data class OcrReadResult(lines, colorUpscaled)` — the rectified color image is threaded back out for reuse in face-photo cropping.
- `matToBitmap(mat: Mat): Bitmap` — `org.opencv.android.Utils.matToBitmap` wrapper, reused elsewhere in the package too.
- `suspend fun readText(engineFactory, roi: Mat): OcrReadResult`:
  1. `warpAndUpscale(roi)` once → `colorUpscaled`.
  2. `ocrVariants(colorUpscaled)` → flattens to 5 `(variant, config)` passes.
  3. `coroutineScope`: one `async(Dispatchers.Default)` per pass, each builds its own `TesseractEngine` via the factory, converts to `Bitmap`, calls `recognizeLines()`, closes the engine in `finally`. A failed pass is caught/logged, contributes an empty list.
  4. `awaitAll()` → flatten to `allLines`.
  5. Sort by `top`, single linear dedup pass: drop lines below `MIN_LINE_CONFIDENCE`; drop exact-text duplicates (`HashSet<String>`), keeping only the first (topmost) occurrence of each distinct string.
  6. Returns `OcrReadResult(result, colorUpscaled)`.

  Doc comment: originally all 5 passes ran sequentially on one shared engine, and "one pass alone routinely took ~13s" on-device; concurrency was added later purely for speed — same 5 passes, same images, no accuracy change.

**Callers:** Called once, from `OcrService.scanSingleImage()`.

### 8.5 `CnicPatterns.kt`

**What/why:** All regexes, digit-lookalike normalization, and date/CNIC validation — the shared toolkit everything downstream builds on. Verbatim port of the Python pattern/validation section.

**Declarations:**
- `DIGITISH = "0-9OoQDIl|!ijSsZzBGgTbAe"` (private) — digits plus every letter Tesseract commonly confuses with a digit, used to build "loose" digit-shaped regexes.
- Regexes: `DATE_RE` (strict `DD.MM.YYYY`/`YYYY.MM.DD`), `DATE_LOOSE` (digit-lookalike + separator tolerant), `DATE_COMPACT` (8 digitish chars, no separators), `CNIC_RE` (strict 5-7-1), `CNIC_LOOSE` (lookalike-tolerant 5-7-1), `NAME_RE` (`^[A-Za-z][A-Za-z .'\-]{2,50}$`, applied to a *cleaned* name, not raw text), `NAME_TOKEN_RE` (word tokenizer).
- `DIGIT_LOOKALIKES: Map<Char,Char>` (private) — concrete letter→digit map (`O/o/Q/D/e→0`, `I/l/|/!/i/j→1`, `Z/z→2`, `A→4`, `S/s→5`, `G/b→6`, `T→7`, `B→8`, `g→9`).
- `digitize(text): String` — applies the lookalike map, strips non-digits.
- `validateCnic(cnicStr): Boolean` — exactly 13 digits after removing hyphens; first digit valid province code `1`–`8`; **more than 2 distinct digit values** required (rejects degenerate reads like `1111111111111`/`1212121212121` that would otherwise pass shape checks).
- `findCnics(text): List<String>` — strict `CNIC_RE` first (validated, collected), then `CNIC_LOOSE` with `digitize()` on captured groups (requires exactly 13 digits after digitizing), both feeding one deduped accumulator, strict preferred but loose still catches messier reads.
- `dateToYmd(dateStr): Triple<Int,Int,Int>?` — parses normalized date string to `(year,month,day)`.
- `validateDate(dateStr): Boolean` — range check (month 1-12, day 1-31, year 1900-2100) then real calendar validation via non-lenient `GregorianCalendar` (catches e.g. Feb 31).
- `findDates(text): List<String>` — `DATE_RE` direct, then `DATE_LOOSE` (digitize each of 3 groups, year must digitize to exactly 4 digits), then `DATE_COMPACT` (digitize whole 8-char run, split positionally); all through a shared `add()` closure requiring `validateDate` to pass and no duplicate.
- `asCalendar`/`asDate(dateStr): Calendar?` — date-string → `GregorianCalendar` for arithmetic.
- `plausibleDob(dateStr, today): Boolean` — not in future, not >120 years (`120*366` days) in the past.
- `plausibleIssue(dateStr, today): Boolean` — year ≥2000 (CNICs issued since 2000), not >1 day in the future.
- `plausibleExpiry(dateStr, today): Boolean` — year in `2005..(currentYear+25)`.
- `termYears(issueStr, expiryStr): Int?` — requires expiry strictly after issue; checks whether the day-gap is within `TERM_SLACK_DAYS=45` of a real printed CNIC term (`5,7,10,15,20` years, compared via `years*365.2425` to absorb leap drift); returns the matching term or `null`.
- `dateBefore(aStr, bStr): Boolean` — lexicographic year/month/day comparison; returns `true` (don't-block default) if either date fails to parse.

**Constants:** `DIGITISH` char class; 17-entry `DIGIT_LOOKALIKES`; province range `'1'..'8'`; distinct-digit floor `>2`; DOB window `0..120*366` days; issue year floor `2000`; expiry year range `2005..(currentYear+25)`; `TERM_SLACK_DAYS=45`; valid terms `{5,7,10,15,20}` years.

**Callers:** `CnicFieldExtractor.kt` (`findCnics`, `validateCnic`, `findDates`, `plausibleDob/Issue/Expiry`, `termYears`, `dateBefore`, `asDate`); `NameExtraction.kt` (`NAME_RE`, `NAME_TOKEN_RE`).

### 8.6 `NameExtraction.kt`

**What/why:** Turns raw OCR lines into validated, clustered name candidates — the messiest part of the pipeline, since names have no fixed shape the way dates/CNIC numbers do. Exists specifically to reject OCR debris that superficially looks like a name while accepting real short/unusual names, and to merge multiple slightly-different reads of the same printed name.

**Declarations:**
- Constants: `NAME_MIN_LETTERS=8`, `NAME_MIN_LONG_TOK=4`, `NAME_MAX_NOISE=0.34`, `NAME_MIN_CONF=0.45`, `NAME_CLUSTER_SIM=0.82`, `NAME_Y_WINDOW=0.05` (used in `CnicFieldExtractor`).
- `SKIP_WORDS` — set of card boilerplate ("pakistan", "islamic", "republic", "identity", "gender", "holder", "signature", "father", "cnic", etc.); `SKIP_FUZZ=0.82` fuzzy threshold for slightly-misread boilerplate.
- `similar(a,b): Double` — public alias for the module's string-similarity function (also used by `LabelMatching`).
- `wordlike(token): Boolean` — ≥3 letters; rejects triple-repeated chars (`(.)\1\1`); requires ≥1 vowel present; rejects 4+ consecutive consonants. Each rule annotated in code with the literal garbage string that motivated it.
- `cleanNameCandidate(text): String` — tokenizes via `NAME_TOKEN_RE`, drops <3-letter tokens, drops tokens matching/fuzzy-matching `SKIP_WORDS`, trims stray hyphens, rejoins.
- `isValidName(text): Boolean` — full gate: reject if contains digits; reject if noise ratio (non-letter/apostrophe/hyphen chars / total non-space chars) exceeds `NAME_MAX_NOISE`; clean and require match against `NAME_RE`; require ≥2 words; require total letters ≥`NAME_MIN_LETTERS`; require ≥1 word with ≥`NAME_MIN_LONG_TOK` letters; require every word `wordlike()`.
- `normName(text): String` — `cleanNameCandidate` + title-case each word.
- `data class NameCluster(value, y, conf, variants: MutableSet<String>, reads, members: MutableList<Pair<String,Double>>)` — one detected "printed name row," aggregating every OCR line across every pass that resolved to approximately the same text. `variants` (which image renderings contributed a read) is a corroboration signal — agreement across independently-processed images is stronger evidence than repeats within one image.
- `clusterRepresentative(members): String` — majority vote by exact-string tally; ties broken by most votes → longest string → highest confidence.
- `nameClusters(lines): List<NameCluster>`:
  1. Filter to `conf>=NAME_MIN_CONF` and `isValidName`; sort by confidence **descending** (cleanest reads seed clusters first).
  2. For each: normalize, find an existing cluster with `stringSimilarity>=NAME_CLUSTER_SIM(0.82)`; merge or create new.
  3. Recompute each cluster's `value` via `clusterRepresentative`.
  4. Return sorted by `y` (topmost row first) — lets `CnicFieldExtractor` assume Name prints above Father Name.
- `stringSimilarity(a,b)` + `matchingBlockLength(...)` (private) — from-scratch port of Python `difflib.SequenceMatcher.ratio()` (`2*M/(len(a)+len(b))`, greedy-recursive longest-common-block), since Kotlin/Java has no stdlib equivalent.

**Callers:** `nameClusters()`/`isValidName()`/`normName()` from `CnicFieldExtractor.extractCnicInfo()`; `similar()` from `LabelMatching.labelKind()`.

### 8.7 `LabelMatching.kt`

**What/why:** Recognizes which field label a given OCR line represents ("Father Name", "Name", DOB/Issue/Expiry), tolerant of OCR misreads. Ports the Python label keyword sets and `_label_kind`/`_strip_label`.

**Declarations:**
- Keyword sets `LBL_FATHER`, `LBL_NAME`, `LBL_DOB`, `LBL_ISSUE`, `LBL_EXPIRY` — literal lowercase substrings including known misspellings (`"fther"`, `"date of blrth"`, `"date of lssue"`, `"date of explry"`, etc.).
- `CANON_LABELS: Map<String,List<String>>` (private) — second-tier canonical phrases for fuzzy fallback. `LABEL_FUZZ=0.72` (private).
- `has(s, keywords): Boolean` (private) — substring containment check.
- `labelKind(line): String?`:
  1. Checks, in fixed priority order — `LBL_FATHER`, `LBL_DOB`, `LBL_ISSUE`, `LBL_EXPIRY`, `LBL_NAME` — first exact-substring hit wins. Order matters: father checked before name so "Father Name" isn't misclassified as bare "name".
  2. Fallback: strips to letters+spaces (`head`); for each `CANON_LABELS` phrase, compares similarity against a windowed prefix (`max(phraseLen+4,8)` chars, tolerant of trailing garbage) and the whole `head`, takes the higher; tracks best `(kind,score)` across phrases; returns the kind only if score `>=LABEL_FUZZ`.
- `stripLabel(line, keywords): String?` — finds the *longest* matching keyword present (longest tried first, so "father name" beats generic "name"), returns everything after it, trimmed, leading `:` stripped; `null` if nothing remains (common — the value is often on the *next* line).

**Callers:** `labelKind()`/`stripLabel()` extensively from `CnicFieldExtractor.extractCnicInfo()`.

### 8.8 `CnicIdPhotoExtractor.kt`

**What/why:** Locates and crops the face photo printed on the card. Uses MediaPipe's `FaceLandmarker` rather than a dedicated face-detector model — the same substitution already made for the face-match/liveness feature. Ports `_extract_id_photo()`.

**Declarations:**
- `FACE_PADDING_RATIO=0.3` (private) — margin padding (30% of face width/height) around the detected landmark bounding box.
- `CANDIDATE_REGIONS: List<DoubleArray>` (private) — 4 fraction-of-card `(left,top,width,height)` rectangles tried in order:
  1. `(0.55, 0.28, 0.42, 0.40)` — tight right-middle window, empirically sized from real successful detections; tried first because a smaller, face-proportionate crop is far more reliable for MediaPipe than the full ~1800×2400 card.
  2. `(0.5, 0.0, 0.5, 0.65)` — top-right quadrant fallback.
  3. `(0.0, 0.0, 0.5, 0.65)` — top-left quadrant (mirrored/unusual layouts).
  4. `(0.0, 0.0, 1.0, 1.0)` — whole card, last resort.
- `TIGHT_REGION_INDEX=0`, `TIGHT_REGION_RETRY_SCALE=1.6` (private) — the tight region, if it fails, is retried once upscaled 1.6× before moving to broader quadrants.
- `extractIdPhoto(landmarker, colorUpscaled): Mat?` — iterates regions in order; crops each, calls `detectAndCropPhoto`; returns the first success; retries region 0 upscaled if it failed; returns `null` (with a warning log) if all fail.
- `detectAndCropPhoto(landmarker, region, label): Mat?` (private) — converts region to `Bitmap`/MPImage, runs `landmarker.detect()`, takes the first detected face's landmarks. Bounding box = min/max of every landmark's `x()`/`y()` scaled to the region's pixel size (derived from the full mesh extent, not a detector-native box). Padded by `FACE_PADDING_RATIO`, clamped to region bounds, rejected if degenerate.

**Constants:** `FACE_PADDING_RATIO=0.3`; 4 `CANDIDATE_REGIONS`; `TIGHT_REGION_INDEX=0`; `TIGHT_REGION_RETRY_SCALE=1.6`.

**Callers:** Called once, from `OcrService.scanSingleImage()`, on the same `colorUpscaled` image `readText()` produced.

### 8.9 `CnicFieldExtractor.kt`

**What/why:** The core heuristic engine — turns deduplicated OCR lines into the six named fields via a long, deliberately-ordered rule sequence with confidence tiers and fallbacks. Most of the package's real complexity lives here; comments repeatedly reference the specific real-card bugs each rule fixed.

**Declarations:**
- `STRONG=2`, `WEAK=1` — confidence tiers (`WEAK` surfaces to the UI as `lowConfidenceFields`).
- `data class Cand(value, weight)`.
- `FIELDS` — canonical 6-field-name list, reused by `OcrService` for "N/6 found."
- `refineCnicDigits(engine, line: OcrLine): String?` — **targeted re-OCR**, not text parsing. Re-crops the *original* source `Mat` the CNIC-matching line came from (via `line.image`/`variant`), pads 8px, resizes 2× cubic, re-runs Tesseract with `TESS_DIGITS_LINE` (digit whitelist, single-line PSM), falling back to `TESS_DIGITS_WORD` if that doesn't yield exactly 13 digits. Far more accurate than the general full-card passes for this one field, because it's a small, tightly-cropped, whitelisted, higher-resolution re-read of exactly the region that matters.
- `extractCnicInfo(engine, ocrLines): Map<String,Cand>` — main function. `offer(field,value,weight)` (local closure) is the sole write path — only overwrites on strictly-higher weight (STRONG never clobbered by WEAK; ties → first-write-wins).

  Filters blank lines; builds parallel `texts`/`fullText`/`labels` (each line's precomputed `labelKind()`).

  **Stage 1 — Identity Number.** Scans lines in order for the first `findCnics()` match; records `cnicLineIdx` (reused for DOB row-anchoring); takes the first candidate as `coarse`; calls `refineCnicDigits` on that line. If refined and coarse **agree** → `STRONG` (two independent methods agreeing); if they disagree → `WEAK` on whichever is available. Stops at the first matching line. Falls back to searching the whole joined `fullText` at `WEAK` if nothing found line-by-line.

  **Stage 2 — Dates**, in a load-bearing order. All dates pooled into `dated` (tagged with line index/label) plus deduped `allDates`.
  - *2.1* — DOB from the identity-number row: first plausible-DOB date on the *same line* as the CNIC number → `STRONG` (exploits that ID number and DOB print on the same row).
  - *2.2* — Label-anchored DOB only: date's own line or the line **immediately above** labeled "dob" → `STRONG`. (Only DOB anchored here; Issue/Expiry anchoring deferred — see 2.3.)
  - *2.3* — **Term structure, before Issue/Expiry label anchoring, deliberately.** Issue and Expiry print side-by-side on one row (their values also side-by-side below), which defeats simple line-adjacency label anchoring. Every ordered date pair is checked via `termYears`, requiring `plausibleIssue(a)`/`plausibleExpiry(b)`; the pair with the **longest matching term** wins (ties broken toward whichever date was already tentatively Date-of-Issue elsewhere), offered at `STRONG` for both fields — "these two dates are exactly N printed-valid years apart" is more reliable than line-adjacency for this layout.
  - *2.4* — Label anchoring for Issue/Expiry, fallback only, for whichever is still missing after term-structure matching.
  - *2.5* — Ordering fallback using `spare` (unassigned dates). Missing DOB: earliest plausible-DOB spare date, but only if it's also the earliest **among every date found anywhere on the card** (sanity check against an already-assigned-but-actually-earlier date). Missing Issue with known Expiry: latest plausible-issue spare before expiry. Missing Expiry with known Issue: earliest plausible-expiry spare after issue. Both missing with ≥2 spares: sorted, earliest=issue/latest=expiry if both plausible. All `WEAK`.
  - *2.6* — Consistency checks, can retract earlier `STRONG` assignments: if Issue+Expiry assigned but `!dateBefore(issue,expiry)`, **both removed** (an inconsistent pair means at least one read was wrong — safer to have neither). If DOB+Issue assigned but DOB not before Issue, DOB alone removed.

  **Stage 3 — Names.**
  1. `nameClusters(lines)`; `corroborated` = clusters from ≥2 distinct variants or ≥2 total reads. If *none* reach that bar (common on a single-shot phone capture vs. the original multi-cycle webcam tool), `corroborated` widens to *every* cluster.
  2. `dominated` — clusters whose value is a strict substring of a longer cluster's value; excluded everywhere (a truncated partial read must never beat a fuller read of the same name).
  3. `fatherRowsAll`/`nameRowsAll` — label-line indices. If both exist, `fatherRows` filtered to rows at/below the topmost Name-label row minus `0.01` slack (Father Name always prints below Name; a "father" label above Name is likely misclassified).
  4. `fatherWindowRows`/`nameWindowRows` — further filtered to `conf>=0.40` (`labelPositionMinConf`) — a low-confidence label read is weak anchor evidence.
  5. `sameRowCluster(i, keywords)` — strips the label off via `stripLabel`, validates/normalizes the remainder, matches first among `corroborated` clusters, falling back to any non-`dominated` cluster. A single-read cluster is accepted here without requiring 2-read corroboration, since sitting on the same printed line as the label is itself strong corroborating evidence.
  6. Candidate list `(priority, kind, cluster)`:
     - *Same-row* (priority `0.0`) — from `sameRowCluster` hits, the strongest signal.
     - *Position-window* (priority ≈`1.0 + dy + corroborationPenalty - confBonus`) — non-dominated clusters within `dy ∈ [-0.01, NAME_Y_WINDOW=0.05]` below a label row. Uncorroborated single-read clusters are allowed (requiring 2-read corroboration on top of position was silently dropping real single-read father names) but only if their own confidence `>= singleReadMinConf=0.55`, and penalized `corroborationPenalty=0.5` in priority relative to corroborated clusters at the same row-distance (so corroborated always wins a tie). `confBonus = 0.05*conf` further nudges toward cleaner reads among similar candidates.
  7. **Greedy assignment** — candidates sorted ascending by priority; first candidate for a not-yet-used `kind` whose specific `NameCluster` (tracked by **reference identity**, not value) hasn't already been consumed by the other kind wins, at `WEAK`.
  8. **Inverted-order swap correction** — if both assigned but Name's `y` is below Father Name's `y` (impossible on a real card), swap values while preserving each field's original weight.
  9. **Printed-order fallback**, only for what's still missing, drawing from *every* non-dominated cluster: `freshAfter(assigned)` returns clusters not already similar to an assigned value. Neither assigned + ≥2 fresh: topmost=Name, next=Father Name (`WEAK`). Only Name missing its pair + a fresh cluster below `y>0.2`: lowest such cluster → Father Name. Only Father Name present: topmost fresh → Name. Exactly one fresh cluster, neither assigned: → Name if `y<0.38` else → Father Name.

  Returns the accumulated `out` map (up to 6 entries).

**Constants:** `STRONG=2`/`WEAK=1`; `refineCnicDigits` padding `8`px, resize `2.0`×; `labelPositionMinConf=0.40`; `singleReadMinConf=0.55`; `corroborationPenalty=0.5`; `confBonus` factor `0.05*conf`; Name/label Y-window `[-0.01, 0.05]`; Father-row filter margin `-0.01`; fresh-Father-below threshold `y>0.2`; single-cluster split threshold `y<0.38`.

**Callers:** `extractCnicInfo()`/`refineCnicDigits()` both called only from `OcrService.scanSingleImage()`.

### 8.10 `OcrService.kt`

**What/why:** The package's single public entry point — ties card classification, OCR, field extraction, and face-photo extraction into one call per captured photo. Ports `scan_single_image()`.

**Declarations:**
- `data class OcrResult(ok, message, fields: Map<String,String>, lowConfidenceFields: Set<String>, allFieldsFound, idPhoto: Mat?)` — `lowConfidenceFields` flags fields from a single unconfirmed (`WEAK`) read rather than two independent readings agreeing; worth surfacing since (unlike the ID-classifier confidence score) there's no other capture-quality signal that would catch a single-shot misread. (Currently tracked but no longer shown in the UI per explicit user request — see history section.)
- `ID_SAVE_CONFIDENCE = 0.80f` (private) — minimum `IdCardClassifier` confidence required before OCR is attempted at all.
- `suspend fun scanSingleImage(idCardClassifier, tesseractEngineFactory, idPhotoLandmarker, frame: Mat): OcrResult`:
  1. `idCardClassifier.classify(frame)`. Not a card, or confidence `< ID_SAVE_CONFIDENCE`, → immediate failed result with a retake message; no OCR attempted.
  2. `readText(tesseractEngineFactory, frame)` → `(ocrLines, colorImg)`.
  3. Creates one more fresh `TesseractEngine` (`digitEngine`) specifically for the CNIC-digit refinement pass inside `extractCnicInfo` (not worth parallelizing this one small pass), closed in `finally`.
  4. `extractCnicInfo(digitEngine, ocrLines)` → maps `Cand` map to plain `field->value` strings (`fields`) and the `WEAK`-weight field-name set (`lowConfidenceFields`).
  5. `allFound = fields.size == FIELDS.size` (all 6/6 extracted, regardless of confidence tier).
  6. `extractIdPhoto(idPhotoLandmarker, colorImg)` → cropped face photo or `null`.
  7. Returns the assembled `OcrResult`; `message` = `"All fields found"` or `"Found N/6 fields"`.

**Callers:** Called once, from `CameraCaptureScreen.kt`, inside the `CaptureMode.OCR` post-capture branch, on `Dispatchers.Default`. `TesseractEngine` factory = `{ TesseractEngine(context) }`; `idPhotoLandmarker` = the same still-image `FaceLandmarker` (`FaceLandmarkerProvider.createForStillImages()`) used elsewhere in that screen for face embedding. The caller displays `result.fields` in a review dialog, converts `result.idPhoto` to a `Bitmap` for display, and persists it via `idCardPhotoRepository.save(...)` so Face Matching can later compare a live selfie against the printed CNIC photo without recapturing the card.

### 8.11 End-to-end OCR narrative

Starting from a captured CNIC photo through to a finished `OcrResult`:

1. **Capture & gating.** `IdCardClassifier.classify()` must judge the frame a CNIC with confidence `>=0.80`; otherwise immediate failure, no OCR work attempted.
2. **Perspective warp / deskew.** Frame is edge-detected and searched for a 4-cornered quad covering `>=55%` of frame area with an aspect ratio near the real CNIC's `85.6:54`mm ratio; if found, warped flat. If not (e.g. card fills the whole frame with no border), falls back to Hough-line-based deskew (skipped if tilt `<0.4°`).
3. **Upscale** toward 1800px wide (clamped 2×–5× up, floor 0.5× down), Lanczos/Area interpolation. This single `colorUpscaled` image feeds everything downstream.
4. **5 parallel Tesseract passes** across 3 renderings (adaptive/otsu/sharp thresholding built on a shared CLAHE+bilateral base), each on its own coroutine + its own `TesseractEngine` instance (concurrency-safe since `TessBaseAPI` isn't meant to be shared across threads). Added specifically because one sequential pass alone took ~13s on-device — parallelizing doesn't skip or downscale any computation.
5. **Line dedup** — pool all lines, sort by Y, filter `conf>=0.30`, keep only the first (topmost) occurrence of each exact-text string.
6. **CNIC number + digit refinement** — first CNIC-shaped match found; its exact source line/pixels re-cropped, padded, 2× upscaled, and re-OCR'd digit-only. Agreement between general and targeted reads → `STRONG`; disagreement → `WEAK`. Card-wide fallback for numbers split across lines.
7. **Dates, priority order:** same-row-as-CNIC date → DOB (`STRONG`); label-anchored DOB (`STRONG`); **term-structure Issue/Expiry pairing runs before label anchoring** (since Issue/Expiry print side-by-side, defeating line-adjacency anchoring) — longest-matching valid term pair wins (`STRONG`); label-anchoring fallback for whichever of Issue/Expiry term-matching missed; chronological-ordering fallback for anything still missing (`WEAK`); final consistency pass discards Issue+Expiry if not chronologically ordered, discards DOB if not before Issue.
8. **Name/Father Name clustering** — name-shaped lines grouped by similarity (`>=0.82`) across all 5 passes into `NameCluster`s, voted to majority spelling. Corroborated (2+ variants/reads) preferred, falling back to all clusters if none qualify. Assignment priority: same-row label match → position-window match (corroborated preferred, cleaner reads slightly preferred) → inverted-order swap-back if needed → printed-order fallback (topmost=Name, position-based split for stragglers) when no label evidence exists at all.
9. **Face photo extraction**, independent of the text pipeline, on the same `colorUpscaled` image — tries a small empirically-tuned tight region first (best detector accuracy), then broader quadrants, then the full card; MediaPipe `FaceLandmarker` per region, bounding box from mesh-landmark extent (not a detector-native box), padded 30%; tight-region failure gets one upscale retry before falling through.
10. **Final result** — 6 fields flattened to strings, `WEAK` fields flagged as `lowConfidenceFields`, `allFieldsFound` = whether 6/6 came through at all, cropped face `Mat` (or `null`) attached. Caller displays fields for review and persists the ID photo for later Face Matching use.

---
