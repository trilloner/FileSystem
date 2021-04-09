public class IOSystem {
    private final static int LENGTH = 64;
    private final static int BLOCK_SIZE = 64;
    private final UnsignedByteArray[] ldisk;

    public IOSystem() {
        this(LENGTH, BLOCK_SIZE);
    }
    public IOSystem(int length, int blockSize) {
        ldisk = new UnsignedByteArray[length];
        for (int i = 0; i < length; i++) {
            ldisk[i] = new UnsignedByteArray(blockSize);
        }
    }

    public void readBlock(int i, UnsignedByteArray p) {
        for (int j = 0; j < p.length(); j++) {
            p.set(j, ldisk[i].get(j));
        }
    }

    public void writeBlock(int i, UnsignedByteArray p) {
        for (int j = 0; j < p.length(); j++) {
            ldisk[i].set(j, p.get(j));
        }
    }
}
