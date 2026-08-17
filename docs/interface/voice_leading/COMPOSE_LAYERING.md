# Production layering
Box(fillMaxSize)
  1. BackgroundPlate
  2. VoiceLeadingVisualizationLayer
  3. PersistentHud / Navigation
  4. ExerciseControls
  5. Feedback / Progress / MIDI state
  6. Transient drawer/sheet/modal when open

Dynamic state belongs in ViewModel/StateFlow and is rendered by Compose. SVG assets are styling primitives, not behavior.
