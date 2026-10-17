package io.github.enadeag.carchargeplanner.engine.forecast;

/**
 * An amount of data, such as the most an answer may hold.
 *
 * @param bytes the number of bytes, more than 0
 */
public record ByteSize(long bytes) {

    /**
     * Checks the amount.
     *
     * @throws IllegalArgumentException when {@code bytes} is 0 or less
     */
    public ByteSize {
        if (bytes <= 0) {
            throw new IllegalArgumentException("bytes must be more than 0, not " + bytes);
        }
    }

    /**
     * An amount in kilobytes of 1024 bytes.
     *
     * @param kilobytes the number of kilobytes, more than 0
     * @return the amount
     * @throws IllegalArgumentException when {@code kilobytes} is 0 or less
     */
    public static ByteSize ofKilobytes(long kilobytes) {
        return new ByteSize(Math.multiplyExact(kilobytes, 1024));
    }

    /**
     * An amount in megabytes of 1024 kilobytes.
     *
     * @param megabytes the number of megabytes, more than 0
     * @return the amount
     * @throws IllegalArgumentException when {@code megabytes} is 0 or less
     */
    public static ByteSize ofMegabytes(long megabytes) {
        return new ByteSize(Math.multiplyExact(megabytes, 1024 * 1024));
    }
}
