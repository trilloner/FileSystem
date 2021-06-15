public class Main {
    public static void main(String[] args) {
        var shell = new Shell(new FileSystem(64,64));
        shell.run();
    }
}
