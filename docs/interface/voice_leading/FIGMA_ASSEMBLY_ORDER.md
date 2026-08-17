# Figma assembly order
1. Place the 1536×1024 background.
2. Add navigation and persistent chrome.
3. Choose one screen template as the geometry scaffold.
4. Place panels.
5. Place voice-motion primitives, note tokens, keyboard/staff primitives.
6. Add real text layers for prompts, voicing labels, statuses, progress and feedback.
7. Convert visual controls to named components with variants.
8. Add transient drawers/modals as separate components/frames.
9. After approval, record actual node IDs and measured bounds in `12_maps/voice-leading.json`.
10. Do not rasterize the complete screen for production.
