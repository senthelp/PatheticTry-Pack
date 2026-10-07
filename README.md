# PatheticTry Pack Hub (Fabric, Minecraft 26.1.2, Java 25) - client-side
- Type `/packs` (or `/pack`) in chat to open a screen with an ON/OFF button for each bundled pack.
- The packs are hidden from Options > Resource Packs; /packs is the only switch. Turning a pack on or off reloads resources (Minecraft always does that when textures change).
- Skeleton Skulls look and are named Dragon Head (the Wither Skeleton Skull stays normal): switch it in /packs (instant, no reload; saved to config/pathetictry-hub.properties as dragonSkull=true/false).
  The old standalone dragon-skull jar is no longer needed (it changes the wrong skull): delete it.

## Adding a pack
1. Unzip the pack into src/main/resources/resourcepacks/<folder>/ (pack.mcmeta must be at the top of <folder>, using min_format/max_format 84-88).
2. Add a line to src/main/resources/assets/packhub/packs.txt:   <folder>|Name shown in list|on or off
3. Push to GitHub, download the jar from Actions > Artifacts.
