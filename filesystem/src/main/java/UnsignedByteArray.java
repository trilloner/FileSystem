public class UnsignedByteArray {
    private final byte[] array;

    public UnsignedByteArray(int length) {
        this.array = new byte[length];
    }

    public void set(int index, int value) {
        this.array[index] = (byte) (value & 0xff);
    }

    public int get(int index) {
        return this.array[index] & 0xff;
    }

    public int length() {
        return this.array.length;
    }
}
