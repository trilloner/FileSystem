package array;

public class IntArray {
    private final UnsignedByteArray underlyingArray;

    IntArray(UnsignedByteArray underlyingArray) {
        this.underlyingArray = underlyingArray;
    }

    public void set(int index, int value) {
        int startIndex = index * 4, endIndex = startIndex + 4;
        if (endIndex > this.underlyingArray.length()) {
            throw new IndexOutOfBoundsException();
        }

        UnsignedByteArray arrayValue = UnsignedByteArray.fromInt(value);
        for (int i = startIndex, j = 0; i < endIndex; i++, j++) {
            underlyingArray.set(i, arrayValue.get(j));
        }
    }

    public int get(int index) {
        int startIndex = index * 4, endIndex = startIndex + 4;
        if (endIndex > this.underlyingArray.length()) {
            throw new IndexOutOfBoundsException();
        }

        return this.underlyingArray.subArray(startIndex, endIndex).toInt();
    }

    public int length() {
        return this.underlyingArray.length() / 4;
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
