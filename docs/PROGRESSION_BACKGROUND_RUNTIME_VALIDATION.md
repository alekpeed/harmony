# Progression Run background runtime validation

The production Progression Run background must be a real raster image, not base64 text stored under an image extension.

Current canonical source: `interface/assets/progression-run-background.png`.

Validation requirements before release:

- PNG starts with the eight-byte signature `89 50 4E 47 0D 0A 1A 0A`.
- File size is greater than 100 KB.
- `file` identifies it as PNG image data.
- Android build syncs this exact source into `R.drawable.progression_run_background`.
- CI must assemble the debug APK successfully.
- The packaged drawable must be extracted from the APK and independently decoded before distribution.

Earlier `.png` and `.webp` exports were rejected because they contained encoded text rather than
decodable raster bytes, which caused `ResourceResolutionException` in `painterResource()` at
runtime; an intermediate build used a validated JPEG while that was tracked down. The pipeline
has since moved back to a genuine, decodable PNG — see `git log` for
`docs/PROGRESSION_BACKGROUND_RUNTIME_VALIDATION.md` for the sequence.
