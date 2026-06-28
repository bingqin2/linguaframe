# Demo Media References

LinguaFrame demo media should be public, attributable, and kept outside git unless a future fixture is intentionally small and license-reviewed.

Use `GET /api/operator/demo-sample-media-catalog`, the browser `Demo sample media` panel, or `scripts/demo/demo-sample-media-catalog.sh` to inspect the structured sample list, configured local sample status, upload duration limit, and safe commands. The catalog intentionally does not download remote media, edit `.env`, upload files, start Docker, call OpenAI, or reveal full local paths.

## Tears of Steel

- **Recommended use:** primary full-video localization demo.
- **Local demo file:** `/Users/wangbingqin/Downloads/tos_casting-720p.mp4`.
- **Original project:** [Tears of Steel](https://studio.blender.org/films/tears-of-steel/) by Blender Studio / Blender Foundation.
- **Demo clip:** "Casting the Actors", listed in the Blender Studio Tears of Steel gallery as a 1280x720, 1:50 free video.
- **License reference:** the official [Mango / Tears of Steel About page](https://mango.blender.org/about/) states that Tears of Steel is licensed as Creative Commons Attribution 3.0.
- **Attribution text:** "Tears of Steel" / "Casting the Actors" by Blender Studio / Blender Foundation, licensed under Creative Commons Attribution 3.0.

Do not commit the MP4 into this repository. Keep downloaded source media under `~/Downloads`, `/tmp/linguaframe-demo`, or another local path ignored by git.

## Other Public Sources

- **Big Buck Bunny / W3Schools sample:** use for lightweight upload and media-processing checks. The W3Schools MP4 is stable and credits Big Buck Bunny; confirm attribution before external demos.
- **Sintel:** use for future longer-context translation tests. The full film is longer than the default 5-minute upload limit, so only use it when the configured duration limit supports the complete selected file.
- **NASA Image and Video Library:** use for technology-themed demos such as space, rockets, missions, and public science explainers. Check each asset metadata page before downloading.
- **Internet Archive Movies:** use for speech, documentary, lecture, or newsreel samples. License varies per item; verify public-domain or Creative Commons status before use.
