import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        OpenFileTable oft = new OpenFileTable(64);
        var b = new UnsignedByteArray(20) {{set(0, 255);}};
        System.out.println(b.get(0));
        oft.readByte(b);
        System.out.println(b.get(0));

    }
}
