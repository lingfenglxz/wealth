# Repository Instructions

- Work from the `master` branch by default.
- Commit changes after modifying code.
- During design and development, when a missing local runtime, command-line tool, SDK, package manager, or dependency blocks the task, restore the required environment as part of the work using official or project-declared sources. Ask before elevation, system-wide or PATH/registry changes, interactive sign-in or license acceptance, non-official sources, paid services, or destructive/conflicting upgrades or removals.

## UI Page Change Verification Rules

**ALL frontend page layout, style, and interaction changes MUST be verified with Playwright in a real browser before committing.** Do not rely solely on unit tests passing.

**Only commit when**: build is clean + all tests pass + Playwright verification passes. Never commit UI changes without seeing the visual result.
