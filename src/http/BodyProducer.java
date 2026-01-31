package http;

import java.io.IOException;

public interface BodyProducer {
    /**
     * Produces the next body chunk.
     *
     * @return
     *   - byte[n > 0] : actual data
     *   - byte[0]     : no data available yet, try again later
     *   - null        : body fully produced (EOF)
     */
    byte[] nextChunk() throws IOException;
}