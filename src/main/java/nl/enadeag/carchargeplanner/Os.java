package nl.enadeag.carchargeplanner;

/** The operating system the app runs on, which decides its folders and its platform's parts. */
enum Os {
    /** Linux, with the freedesktop.org folders, D-Bus services and desktop entries. */
    LINUX,
    /** Windows, with its application data folders, the Credential Manager and the Run key. */
    WINDOWS,
    /** Any other system, such as macOS: the Linux folders, and no keychain or menu entry. */
    OTHER;

    /**
     * The system that a name stands for.
     *
     * @param osName the name as the {@code os.name} property gives it, such as "Linux" or "Windows 11"; never null
     * @return the system; {@link #OTHER} for a name that is neither
     */
    static Os of(String osName) {
        if (osName.startsWith("Windows")) {
            return WINDOWS;
        }
        return osName.startsWith("Linux") ? LINUX : OTHER;
    }

    /**
     * The system this JVM runs on. Read it once, in the composition root, and hand it on.
     *
     * @return the system named by the {@code os.name} property
     */
    static Os current() {
        return of(System.getProperty("os.name"));
    }
}
