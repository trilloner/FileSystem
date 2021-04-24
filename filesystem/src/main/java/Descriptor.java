import array.UnsignedByteArray;

public class Descriptor {
    private UnsignedByteArray array;

    public Descriptor(int fileLength, int[] blockIndices) {
        array = new UnsignedByteArray(blockIndices.length + 1);
        array.set(0, fileLength);
        for (int i = 0; i < blockIndices.length; i++) {
            array.set(i + 1, blockIndices[0]);
        }
    }
    public Descriptor(UnsignedByteArray array) {
        this.array = array;
    }

    public int getFileLength() {
        return array.get(0);
    }

    public void setFileLength(int fileLength) {
        array.set(0, fileLength);
    }

    public void setBlockIndex(int blockNumber, int blockIndex) {
        array.set(blockNumber + 1, blockIndex);
    }

    public int getBlockIndex(int blockNumber) {
        return array.get(1 + blockNumber);
    }
}
