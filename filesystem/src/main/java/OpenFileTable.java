public class OpenFileTable {
    private int currentPosition;
    private UnsignedByteArray buffer;
    private int descriptorIndex;
    private int length;
    private static final int DEFAULT_VALUE = -128;

    public OpenFileTable(int bufferSize) {
        this.buffer = new UnsignedByteArray(bufferSize);
        this.currentPosition = 0;
        this.descriptorIndex = -1;
        this.length = -1;
    }

    public void initBuffer() {
        for (int i = 0; i < buffer.length(); i++) {
            buffer.set(i, DEFAULT_VALUE);
        }
    }

    public int readByte(UnsignedByteArray bytes, int index) {
        bytes.set(index, buffer.get(currentPosition % buffer.length()));
        currentPosition++;

        return getStatus();
    }

    public int readByte(UnsignedByteArray bytes) {
        return readByte(bytes, 0);
    }

    public int writeByte(int b) {
        buffer.set(currentPosition % buffer.length(), b);
        currentPosition++;

        if (currentPosition == length + 1) {
            length++;
        }

        return getStatus();
    }

    public int writeByte(UnsignedByteArray bytes) {
        return writeByte(bytes.get(0));
    }

    public boolean seek(int index) {
        if (index <= length && length >= 0) {
            currentPosition = index;
            return true;
        }
        return false;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public int getDescriptorIndex() {
        return descriptorIndex;
    }

    public UnsignedByteArray getBuffer() {
        return buffer;
    }

    public int getLength() {
        return length;
    }

    public int getCurrentBlock() {
        return (currentPosition / buffer.length()) + 1;
    }

    public int getStatus() {

        if (currentPosition == buffer.length() * 3) // max length of file (3 block)
            return 4;
        else if (currentPosition == buffer.length() * 2 && currentPosition == length)
            return 3;
        else if (currentPosition == buffer.length() && currentPosition == length)
            return 2;
        else if (currentPosition == 0 && currentPosition == length)
            return 1;
        else if (currentPosition == buffer.length() * 2 && currentPosition < length)
            return -3;
        else if (currentPosition == buffer.length() && currentPosition < length)
            return -2;
        else if (currentPosition == 0 && currentPosition < length)
            return -1;
        else
            return 0;
    }
}
