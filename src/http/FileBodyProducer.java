package http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileBodyProducer implements BodyProducer {

    private static final int CHUNK_SIZE = 8192; // 8KB

    private final InputStream in;
    private boolean closed = false;

    public FileBodyProducer(Path file) throws IOException {
        this.in = Files.newInputStream(file);
    }

    @Override
    public byte[] nextChunk() throws IOException {
        if (closed) return null;

        byte[] buffer = new byte[CHUNK_SIZE];
        int read = in.read(buffer);

        if (read == -1) {
            in.close();
            closed = true;
            return null;
        }

        if (read == buffer.length) {
            return buffer;
        }

        // trim last chunk
        byte[] chunk = new byte[read];
        System.arraycopy(buffer, 0, chunk, 0, read);
        return chunk;
    }
}
