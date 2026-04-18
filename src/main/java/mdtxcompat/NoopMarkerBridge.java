package mdtxcompat;

final class NoopMarkerBridge implements MarkerBridge {
    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public void mark(String text, int tileX, int tileY) {
    }
}
