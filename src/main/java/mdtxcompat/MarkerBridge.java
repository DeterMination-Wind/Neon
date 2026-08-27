package mdtxcompat;

/** Posts a chat-style world marker; {@link #UNSUPPORTED} when the runtime has no marker feature. */
public interface MarkerBridge {
    MarkerBridge UNSUPPORTED = new NoopMarkerBridge();

    boolean isSupported();

    void mark(String text, int tileX, int tileY);
}
