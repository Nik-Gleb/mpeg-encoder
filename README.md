# Mpeg Encoder
Fast and easy creating of videos from pictures.

## Quick Start
Most likely you will need to complete 3 steps:

#### 1). Create and configure the encoder
```
final MpegEncoder encoder =
        MpegEncoder
                .from(INPUT_BUFFER, WIDTH, HEIGHT)
                .fps(FRAME_RATE).motion(Motion.LOW)
                .to(mOutputFilePath, WIDTH, HEIGHT);
```
#### 2). Drawing frames loop
```
for (int i = 0; i < NUM_FRAMES; i++) {
    if (isCancelled()) {
        break;
    }
                            
    try (final InputStream is = mAssetManager.open(fileNames[i])) {
        final Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
        if (bitmap != null) {
            INPUT_BUFFER.rewind();
            bitmap.copyPixelsToBuffer(INPUT_BUFFER);
        }
    } catch (IOException exception) {
        Log.w(TAG, exception);
    }
    
    encoder.draw();
}
```
#### 3). Release the encoder's resources
```
encoder.close();                                       
```

### Summary
The project contains two modules: __lib__ and __app__.

This solution doesn't use any third-party libraries, except for android.annotations for 
(@Nullable/@NonNull) and proguard.annotations for easy setup shrinking.

The encoder uses hardware graphics capabilities of the phone, which ensures 
maximum encoding performance.

All classes and methods are covered by instrumented tests.

**MIN SDK - 19**

 - Total methods in compiled aar: **157**
 - Total fields in compiled aar:  **48**
 - The file size of compiled aar: **27 762 bytes (27.1KB)**

**Devices on which development and test were conducted:**
 
 * Galaxy S4 mini (API19)
 * LG G3 (API19)
 * HTC One (M8) (API19)
 * Moto E (API19)
 * Galaxy Tab Pro 8.4 (API19)
 * Xperia Z2(3rd Gen) (API21)
 * Galaxy A5 (API21)
 * Moto G(1st Gen) (API22)
 * Moto G(3rd Gen) (API22)
 * Nexus 5 (API22)
 * Galaxy J5 (API23)
 * Galaxy S3 (API23 - СyanogenMod 14.1)
 * Galaxy S7 (API23)
 * Nexus 9 (API23)
 * Xperia Z3 Compact (API23)
 * General AVD's (API21 - API25)

### Known issues:
 - General AVD (19API)
 - Emulator Nexus 5 (22API)
 - Galaxy Pocket Duos (19API - CyanogenMod 11)
