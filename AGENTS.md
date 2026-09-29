# Repository workflow rules

- Do not build, test, package, or run this project on the local machine.
- Run all build and runtime verification through GitHub Actions on GitHub.
- Local source edits and read-only inspection are allowed.
- Run every `gh` command outside the sandbox (`require_escalated`).
- The deployment target uses Microsoft JDK 25. Do not ask the maintainer to
  confirm the installed JDK version again.
