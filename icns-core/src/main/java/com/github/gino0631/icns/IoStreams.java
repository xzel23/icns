package com.github.gino0631.icns;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * Utilities for working with input and output streams.
 */
final class IoStreams {
    @FunctionalInterface
    public interface Counter {
        long add(long delta);
    }

    static abstract class DelegatingInputStream extends InputStream {
        private final InputStream is;

        DelegatingInputStream(InputStream is) {
            this.is = is;
        }

        @Override
        public int read() throws IOException {
            return is.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return is.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return is.skip(n);
        }

        @Override
        public int available() throws IOException {
            return is.available();
        }

        @Override
        public void close() throws IOException {
            is.close();
        }

        @Override
        public synchronized void mark(int readlimit) {
            is.mark(readlimit);
        }

        @Override
        public synchronized void reset() throws IOException {
            is.reset();
        }

        @Override
        public boolean markSupported() {
            return is.markSupported();
        }
    }

    static abstract class DelegatingOutputStream extends OutputStream {
        private final OutputStream os;

        DelegatingOutputStream(OutputStream os) {
            this.os = os;
        }

        @Override
        public void write(int b) throws IOException {
            os.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            os.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            os.flush();
        }

        @Override
        public void close() throws IOException {
            os.close();
        }
    }

    private IoStreams() {
    }

    public static InputStream limit(InputStream is, long limit) {
        return new DelegatingInputStream(is) {
            private long available = limit;
            private long mark = -1;

            @Override
            public int read() throws IOException {
                if (available > 0) {
                    int b = super.read();
                    if (b >= 0) {
                        available--;
                        return b;
                    }
                }

                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (available > 0) {
                    len = (int) Math.min(len, available);
                    len = super.read(b, off, len);
                    available -= len;

                    return len;

                } else {
                    return -1;
                }
            }

            @Override
            public long skip(long n) throws IOException {
                n = Math.min(n, available);

                if (n > 0) {
                    n = super.skip(n);
                    available -= n;
                    return n;

                } else {
                    return 0;
                }
            }

            @Override
            public int available() throws IOException {
                return (int) Math.min(super.available(), available);
            }

            @Override
            public synchronized void mark(int readlimit) {
                super.mark(readlimit);
                mark = available;
            }

            @Override
            public synchronized void reset() throws IOException {
                if (mark >= 0) {
                    super.reset();
                    available = mark;

                } else {
                    throw new IOException("Mark not set");
                }
            }
        };
    }

    public static InputStream count(InputStream is, Counter counter) {
        return new DelegatingInputStream(is) {
            private long mark = -1;

            @Override
            public int read() throws IOException {
                int b = super.read();
                if (b >= 0) {
                    counter.add(1);
                }

                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int n = super.read(b, off, len);
                if (n > 0) {
                    counter.add(n);
                }

                return n;
            }

            @Override
            public long skip(long n) throws IOException {
                n = super.skip(n);
                counter.add(n);

                return n;
            }

            @Override
            public synchronized void mark(int readlimit) {
                super.mark(readlimit);
                mark = counter.add(0);
            }

            @Override
            public synchronized void reset() throws IOException {
                if (mark >= 0) {
                    super.reset();
                    counter.add(mark - counter.add(0));

                } else {
                    throw new IOException("Mark not set");
                }
            }
        };
    }

    public static OutputStream count(OutputStream os, Counter counter) {
        return new DelegatingOutputStream(os) {
            @Override
            public void write(int b) throws IOException {
                super.write(b);
                counter.add(1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                super.write(b, off, len);
                counter.add(len);
            }
        };
    }

    public static InputStream closeProtect(InputStream is) {
        return new DelegatingInputStream(is) {
            @Override
            public void close() throws IOException {
                // Ignore
            }
        };
    }

    public static OutputStream closeProtect(OutputStream os) {
        return new DelegatingOutputStream(os) {
            @Override
            public void close() throws IOException {
                // Just flush
                flush();
            }
        };
    }

    public static void close(Closeable closeable, Consumer<IOException> errorHandler) {
        if (closeable != null) {
            try {
                closeable.close();

            } catch (IOException e) {
                if (errorHandler != null) {
                    errorHandler.accept(e);
                }
            }
        }
    }

    public static long skip(InputStream is, long count) throws IOException {
        long remaining = count;
        long read;

        while ((remaining > 0) && ((read = is.skip(remaining)) > 0)) {
            remaining -= read;
        }

        return count - remaining;
    }

    public static long waste(InputStream is, long count) throws IOException {
        long remaining = count;
        long read;
        byte[] buf = new byte[(int) Math.min(count, 8192)];

        while ((remaining > 0) && ((read = is.read(buf, 0, (int) Math.min(buf.length, remaining))) >= 0)) {
            remaining -= read;
        }

        return count - remaining;
    }

    public static long exhaust(InputStream is) throws IOException {
        long total = 0;
        long read;
        byte[] buf = new byte[8192];

        while ((read = is.read(buf)) >= 0) {
            total += read;
        }

        return total;
    }

    /**
     * Reads all bytes from an input stream and writes them to an output stream.
     * <p>
     * Copied from {@code java.nio.file.Files}.
     */
    public static long copy(InputStream source, OutputStream sink) throws IOException {
        long nread = 0L;
        byte[] buf = new byte[8192];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
    }
}
