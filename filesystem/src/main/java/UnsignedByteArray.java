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

    @Override
    public boolean equals(Object obj) {
        if (!(this.getClass() == obj.getClass())) {
            return false;
        }

        UnsignedByteArray other = (UnsignedByteArray) obj;

        if (this.length() != other.length()) {
            return false;
        }

        for (int i = 0; i < this.length(); i++) {
            if (this.get(i) != other.get(i)) {
                return false;
            }
        }

        return true;
    }

    public UnsignedByteArray subArray(int fromIndex, int toIndex) {
        UnsignedByteArray array = new UnsignedByteArray(toIndex - fromIndex);

        for (int i = fromIndex, j = 0; i < toIndex; i++, j++) {
            array.set(j, this.get(i));
        }

        return array;
    }

    public UnsignedByteArray subArray(int toIndex) {
        return subArray(0, toIndex);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("{");

        int i = 0;
        for (; i < this.length() - 1; i++) {
            stringBuilder.append(this.get(i)).append(", ");
        }
        stringBuilder.append(this.get(i)).append("}");

        return stringBuilder.toString();
    }
}
