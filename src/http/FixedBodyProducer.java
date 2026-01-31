package http;

import java.io.IOException;

public class FixedBodyProducer implements BodyProducer {

    private final byte[] data;
    private boolean sent = false;

    public FixedBodyProducer(byte[] data) {
        this.data = data;
    }

    @Override
    public byte[] nextChunk() throws IOException {
        if (sent) return null;
        sent = true;
        return data;
    }
}
