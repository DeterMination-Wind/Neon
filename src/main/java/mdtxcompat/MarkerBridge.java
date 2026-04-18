package mdtxcompat;

public interface MarkerBridge {
    MarkerBridge UNSUPPORTED = new NoopMarkerBridge();

    boolean isSupported();

    void mark(String text, int tileX, int tileY);
}
