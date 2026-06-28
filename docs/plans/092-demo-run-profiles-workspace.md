# Demo Run Profiles Workspace

## Goal

Add reusable demo run profiles so LinguaFrame can apply a complete localization configuration in one action instead of manually setting target language, voice, translation style, glossary, subtitle style, and subtitle polishing for every run.

This feature should make demos more repeatable and easier to compare while staying private-demo focused. It must not introduce user accounts, billing plans, or editable prompt experiments.

## Scope

- Add a backend read-only catalog of built-in demo profiles.
- Expose profiles through a safe API endpoint.
- Add a React profile selector that applies the selected profile to the upload form.
- Add terminal demo script support for selecting the same profile.
- Include selected profile metadata in job evidence and demo handoff surfaces where safe.
- Update README, roadmap, target state, decisions, and execution log.

## Built-In Profiles

Start with a small fixed catalog:

- `quick-baseline`: Chinese target, default voice, natural translation, standard subtitles, polishing off, no glossary.
- `tears-showcase`: Chinese target, default voice, formal or natural translation, high-contrast subtitles, balanced polishing, Tears of Steel glossary terms.
- `concise-review`: Chinese target, concise translation, large subtitles, strict polishing, no glossary.

Profiles are presets only. Upload requests still send the resolved fields already supported by the backend. The selected profile id should be stored as metadata when provided so reports can explain which preset was used.

## Backend Plan

1. Add `demo_profile_id` to `localization_jobs` with nullable/default empty migration.
2. Add `DemoRunProfileVo` with id, label, description, target language, TTS voice, translation style, subtitle style preset, subtitle polishing mode, and translation glossary.
3. Add a read-only `DemoRunProfileService` and `GET /api/demo-run-profiles`.
4. Accept optional `demoProfileId` during media upload, validate it against the catalog when present, and persist it on the job.
5. Thread `demoProfileId` through job records, job list/detail VOs, queued messages, worker summaries, diagnostics/evidence, delivery manifests, handoff package metadata, demo run package metadata, and browser cache replay evidence.
6. Keep provider/cache behavior based on resolved fields, not profile id, so equivalent manual settings can still reuse compatible outputs.

## Frontend Plan

1. Add API/domain types for demo run profiles.
2. Load profiles on startup and show a compact `Demo profile` selector near the upload controls.
3. Applying a profile updates target language, TTS voice, translation style, glossary, subtitle style, and subtitle polishing.
4. Preserve manual editing after applying a profile; changing a field should not be blocked.
5. Show the selected profile id/label in recent jobs, job detail, delivery handoff, demo checklist, session report, and cache replay evidence.

## Demo Script Plan

1. Add `LINGUAFRAME_DEMO_PROFILE_ID`, defaulting to `quick-baseline`.
2. Resolve known profile ids in `scripts/demo/lib/linguaframe-demo.sh` into the same multipart upload fields used by the browser.
3. Keep existing explicit env vars as overrides, so `LINGUAFRAME_DEMO_TRANSLATION_STYLE=CONCISE` can override a profile for one run.
4. Print profile metadata in terminal quality evidence and session reports.

## Testing

- Backend service/controller tests for catalog shape and invalid profile rejection.
- Media upload tests for persisting profile id and defaulting blank values.
- Frontend API tests for profile fetch and upload `demoProfileId`.
- React tests for applying a profile to the form and preserving manual edits.
- Recent job tests for safe profile metadata defaults.
- Demo script tests for default profile, profile resolution, and explicit env override precedence.
- Full validation: `mvn -pl LinguaFrame test`, `cd frontend && npm test -- --run`, `cd frontend && npm run build`, `bash scripts/demo/test-linguaframe-demo-client.sh`, `bash -n ...`, and `git diff --check`.

## Acceptance Criteria

- A user can choose a profile in the browser and upload with all related fields populated consistently.
- A terminal demo can use `LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase` and produce the same resolved upload settings.
- Job detail and demo evidence can explain which profile was used without exposing secrets, raw media paths, or provider payloads.
- Manual field choices remain possible and are not hidden behind profiles.
- The feature is merged back to `main` after verification.
