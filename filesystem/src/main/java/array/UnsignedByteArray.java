package array;

import java.nio.ByteBuffer;

public class UnsignedByteArray {
    private final byte[] array;

    public UnsignedByteArray(int length) {
        this.array = new byte[length];
    }

    public UnsignedByteArray(String asciiString) {
        this(asciiString.length());
        for (int i = 0; i < asciiString.length(); i++) {
            this.set(i, asciiString.charAt(i));
        }
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

    public IntArray asIntArray() {
        return new IntArray(this);
    }

    public UnsignedByteArray fillToLength(int length, int fillValue) {
        if (length <= this.length()) {
            throw new IllegalArgumentException("Array length >= provided length");
        }

        UnsignedByteArray array = new UnsignedByteArray(length);

        for (int i = 0; i < this.length(); i++) {
            array.set(i, this.get(i));
        }

        for (int i = this.length(); i < array.length(); i++) {
            array.set(i, fillValue);
        }

        return array;
    }

    public UnsignedByteArray fillToLength(int length) {
        return this.fillToLength(length, 0);
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (obj == null || this.getClass() != obj.getClass()) {
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

    public static UnsignedByteArray fromInt(int value) {
        UnsignedByteArray array = new UnsignedByteArray(4);

        for (int i = 3; i >= 0; i--) {
            array.set(i, value);
            value >>= 8;
        }

        return array;
    }

    public Integer toInt() {
        if (length() == 0 || length() > 4) {
            return null;
        }

        byte[] array = new byte[4];
        for (int i = 0; i < this.length(); i++) {
            array[i + 4 - this.length()] = this.array[i];
        }
        return ByteBuffer.wrap(array).getInt();
    }

    public String toAsciiString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < this.length(); i++) {
            stringBuilder.append((char)this.get(i));
        }
        return stringBuilder.toString();
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
