import array.IntArray;
import array.UnsignedByteArray;

public class Bitmap {
    private static final int[] MASK = createMask();
    private static final int[] MASK2 = createMask2();

    private static int[] createMask() {
        int[] mask = new int[Integer.SIZE];
        mask[Integer.SIZE - 1] = 1;
        for (int i = Integer.SIZE - 2; i >= 0; i--) {
            mask[i] = mask[i + 1] << 1;
        }
        return mask;
    }

    private static int[] createMask2() {
        int[] mask = createMask();
        int[] mask2 = new int[Integer.SIZE];
        for (int i = 0; i < Integer.SIZE; i++) {
            mask2[i] = ~mask[i];
        }
        return mask2;
    }

    private IOSystem ioSystem;
    private UnsignedByteArray buffer;

    public Bitmap(IOSystem ioSystem) {
        this.ioSystem = ioSystem;
        this.buffer = new UnsignedByteArray(ioSystem.getLength() / Byte.SIZE);
    }

    public int getFreeBlockIndex() {
        IntArray bufferAsIntArray = buffer.asIntArray();

        for (int k = 7; k < ioSystem.getLength(); k++) {
            int i = k / Integer.SIZE;
            int j = k % Integer.SIZE;

            if ((bufferAsIntArray.get(i) & MASK[j]) == 0) {
                return k;
            }
        }

        return FileSystem.NOT_ALLOCATED_INDEX;
    }

    public void setBlockIndexTaken(int blockIndex) {
        IntArray bufferAsIntArray = buffer.asIntArray();
        bufferAsIntArray.set(blockIndex / Integer.SIZE, bufferAsIntArray.get(blockIndex / Integer.SIZE) | MASK[blockIndex % Integer.SIZE]);

        ioSystem.writeBlock(0, buffer);
    }

    public void setBlockIndexFree(int blockIndex) {
        IntArray bufferAsIntArray = buffer.asIntArray();
        bufferAsIntArray.set(blockIndex / Integer.SIZE, bufferAsIntArray.get(blockIndex / Integer.SIZE) & MASK2[blockIndex % Integer.SIZE]);

        ioSystem.writeBlock(0, buffer);
    }
}
