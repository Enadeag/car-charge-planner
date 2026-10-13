# Windows builds with PyInstaller, unsigned until SignPath at the launch

The Windows installer wraps a PyInstaller one-folder build in Inno Setup. Nuitka was tried on the same machine: the installer came out the same size (23 MB), later starts were only 0.1 s faster and the first start was slower, Windows Defender flagged neither, and its windowed build found no keychain backend, which would leave API keys in a file. The one-folder layout keeps the Qt libraries as separate files that users can replace, as the LGPL asks of PySide6.

Until the public launch the installer is not code-signed: Windows shows "Windows protected your PC", and the README explains how to check the download against its SHA-256 checksum. At the launch the project applies to the SignPath Foundation, which signs open-source projects for free from builds on GitHub's own runners.

## Considered options

- **Azure Trusted Signing**: open to individuals only in the US and Canada.
- **A Certum open-source certificate**: €49 a year, but every signature needs a code from a phone app, which does not fit signing in CI.
- No certificate gives instant trust: SmartScreen reputation still has to grow with downloads.
