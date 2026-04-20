# ilink-tui

iLink terminal chat client built with TamboUI.

## Requirements

- JDK 17+
- Maven 3.9+

## Local Development

```bash
mvn test
mvn -Pnative package
```

The native binary is written to `target/`:

- macOS / Linux: `target/ilink-tui`
- Windows: `target/ilink-tui.exe`

## Releases

This repository publishes native binaries through GitHub Releases.

1. Push code to `main`
2. Create and push a tag such as `v1.0.0`
3. GitHub Actions builds native binaries for Linux, macOS, and Windows
4. The workflow creates or updates the matching GitHub Release and uploads the binaries

Example:

```bash
git tag v1.0.0
git push origin v1.0.0
```
