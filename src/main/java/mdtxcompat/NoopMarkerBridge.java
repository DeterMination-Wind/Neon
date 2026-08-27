package mdtxcompat;

/** Inert fallback behind {@link MarkerBridge#UNSUPPORTED}. */
final class NoopMarkerBridge implements MarkerBridge {
    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public void mark(String text, int tileX, int tileY) {
    }
}
