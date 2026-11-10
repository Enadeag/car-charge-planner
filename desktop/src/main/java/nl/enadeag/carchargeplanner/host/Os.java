package nl.enadeag.carchargeplanner.host;

/** The operating system the app runs on, which decides its folders and its platform's parts. */
public enum Os {
    /** Linux, with the freedesktop.org folders, D-Bus services and desktop entries. */
    LINUX,
    /** Windows, with its application data folders, the Credential Manager and the Run key. */
    WINDOWS,
    /** Any other system, such as macOS: the Linux folders, and no keychain or menu entry. */
    OTHER;

    static Os of(String osName) {
        if (osName.startsWith("Windows")) {
            return WINDOWS;
        }
        return osName.startsWith("Linux") ? LINUX : OTHER;
    }

    /** Read it once, in the composition root, and hand it on. */
    static Os current() {
        return of(System.getProperty("os.name"));
    }
}
