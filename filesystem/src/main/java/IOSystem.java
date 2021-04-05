public class IOSystem {
    private final int LENGTH = 64;
    private final int BLOCK_SIZE = 64;
    private final byte[][] ldisk;

    public IOSystem() {
        this.ldisk = new byte[LENGTH][BLOCK_SIZE];
    }

    public IOSystem(int l, int b) {
        this.ldisk = new byte[l][b];
    }

    public void readBlock(int i, byte[] p) {
        for (int j = 0; j < p.length; j++) {
            p[j] = this.ldisk[i][j];
        }
    }

    public void writeBlock(int i, byte[] p) {
        for (int j = 0; j < p.length; j++) {
            this.ldisk[i][j] = p[j];
        }
    }
}
