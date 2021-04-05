public class Main {
    public static void main(String[] args) {
        OpenFileTable oft = new OpenFileTable(64);
        byte[] b = new byte[]{12};
        System.out.println(b[0]);
        oft.readByte(b);
        System.out.println(b[0]);
    }
}
