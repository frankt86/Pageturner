# AGENTS.md

Pageturner is a modular Android audiobook player and ebook reader, based on Voice.

- Build the app: `./gradlew :app:assembleFreeDebug`.
- If installing a debug build fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the installed app was signed with a different key. Never uninstall it to get around this, as that wipes the user's library. Ask the user instead.
- Local builds take the version code of the newest release tag. If an install fails with `INSTALL_FAILED_VERSION_DOWNGRADE`, run `git fetch --tags` and build again. Over an installed debug build, `adb install -r -d` also works. Otherwise ask the user, never uninstall.
- Only write code comments when absolutely necessary, to explain a non-obvious *why*. Don't restate what the code does or describe the change you made. This includes KDoc.
- Keep complexity low, as every line of code is maintained for years. Build the simplest version that solves the problem listeners actually have. If an edge case would add a lot of code, stored data or a migration, leave it out and name it in the pull request, or ask first.
- In the Play build, logs from `Logger.d` up go to Crashlytics. Never log names, titles, URIs, ids, server addresses, what the listener typed or whole objects. Log counts, positions and types instead. Pass exceptions as the throwable, don't put them in the message. Use `Logger.nonFatal` only for what should never happen.

## Writing style

Write replies to the user, comments, commit messages, pull requests, issues and docs in ASD-STE100 Simplified Technical English: active voice, simple tenses, one word per meaning, noun clusters of three words or fewer, and sentences of 20 words or fewer. UI strings and the store listing follow the translate-strings skill instead.

Keep pull requests, commit messages and issues short: say in a few sentences what changes for listeners.

## Pull requests

- Always commit and push your work, open a pull request and merge it as soon as CI passes. Don't leave finished work unmerged.
- Commit as frankt86 <frank.terry.james@gmail.com>. Never credit an AI in commits, pull requests or code.

Pageturner is maintained by frankt86. Report bugs and ideas as issues: https://github.com/frankt86/Pageturner/issues/new
