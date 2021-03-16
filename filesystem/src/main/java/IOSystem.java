public class IOSystem {
    private final int LENGTH = 64;
    private final int BLOCK = 64;
    private char[][] ldisk;

    public IOSystem() {
        this.ldisk = new char[LENGTH][BLOCK];
    }

    public IOSystem(int l, int b) {
        this.ldisk = new char[l][b];
    }

    public void read_block(int i, char[] p) {
        System.arraycopy(this.ldisk[i], 0, p, 0, BLOCK);
    }

    public void write_block(int i, char[] p) {
        System.arraycopy(p, 0, this.ldisk[i], 0, BLOCK);
    }
}
