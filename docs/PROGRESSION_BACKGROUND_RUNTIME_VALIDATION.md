# Progression Run background runtime validation

The production Progression Run background must be a real raster image, not base64 text stored under an image extension.

Current canonical source: `interface/assets/progression-run-background.jpg`.

Validation requirements before release:

- JPEG starts with `FF D8` and ends with `FF D9`.
- File size is greater than 100 KB.
- `file` identifies it as JPEG image data.
- Android build syncs this exact source into `R.drawable.progression_run_background`.
- CI must assemble the debug APK successfully.
- The packaged drawable must be extracted from the APK and independently decoded before distribution.

The prior `.png` and `.webp` files were removed because they contained encoded text rather than decodable raster bytes, which caused `ResourceResolutionException` in `painterResource()` at runtime.
