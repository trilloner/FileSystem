import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Shell {
    private final FileSystem fileSystem;
    private final Scanner scanner;

    public Shell(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
        this.scanner = new Scanner(System.in);
    }

    public void run() {
        while (true) {
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] tokens = Arrays.stream(line.split(" ")).filter(s -> !s.equals("")).toArray(String[]::new);
                String command = tokens[0];
                List<String> args = Arrays.asList(tokens.clone()).subList(1, tokens.length);

                if (command.equals("ex")) {
                    break;
                }
                processCommand(command, args);
            }
        }

        scanner.close();
    }

    private void processCommand(String command, List<String> args) {
        switch (command) {
            case "cr":
                create(args);
                break;
            case "de":
                break;
            case "op":
                open(args);
                break;
            case "cl":
                close(args);
                break;
            case "rd":
                read(args);
                break;
            case "wr":
                write(args);
                break;
            case "sk":
                lseek(args);
                break;
            case "dr":
                break;
            case "in":
                break;
            case "sv":
                break;
            default:
                System.out.println("unknown command");
                break;
        }
    }

    public void create(List<String> args) {
        if (args.size() != 1) {
            System.out.println("error");
            return;
        }

        var filename = new UnsignedByteArray(args.get(0));
        if (fileSystem.create(filename)) {
            System.out.printf("file %s created\n", filename.toAsciiString());
        }
        else {
            System.out.println("error");
        }
    }

    public void open(List<String> args) {
        if (args.size() != 1) {
            System.out.println("error");
            return;
        }

        var filename = new UnsignedByteArray(args.get(0));
        int index = fileSystem.open(filename);
        if (index != -1) {
            System.out.printf("file %s opened, index=%d\n", filename.toAsciiString(), index);
        }
        else {
            System.out.println("error");
        }

    }

    public void close(List<String> args) {
        if (args.size() != 1) {
            System.out.println("error");
            return;
        }

        try {
            int index = Integer.parseInt(args.get(0));
            index = fileSystem.close(index);
            if (index != -1) {
                System.out.printf("file %d closed\n", index);
            }
            else {
                System.out.println("error");
            }
        }
        catch (NumberFormatException e) {
            System.out.println("error");
        }
    }

    public void write(List<String> args) {
        if (args.size() != 3) {
            System.out.println("error");
            return;
        }

        try {
            int index = Integer.parseInt(args.get(0));
            var chars = new UnsignedByteArray(args.get(1));
            int count = Integer.parseInt(args.get(2));
            count = fileSystem.write(index, chars, count);
            if (count != -1) {
                System.out.printf("%d bytes written\n", count);
            }
            else {
                System.out.println("error");
            }
        }
        catch (NumberFormatException e) {
            System.out.println("Error");
        }
    }

    public void read(List<String> args) {
        if (args.size() != 2) {
            System.out.println("error");
            return;
        }

        try {
            int index = Integer.parseInt(args.get(0));
            int count = Integer.parseInt(args.get(1));
            var chars = new UnsignedByteArray(count);
            count = fileSystem.read(index, chars, count);
            if (count != -1) {
                System.out.printf("%d bytes read: %s\n", count, chars.toAsciiString());
            }
            else {
                System.out.println("error");
            }
        }
        catch (NumberFormatException e) {
            System.out.println("Error");
        }
    }

    public void lseek(List<String> args) {
        if (args.size() != 2) {
            System.out.println("error");
            return;
        }

        try {
            int index = Integer.parseInt(args.get(0));
            int pos = Integer.parseInt(args.get(1));
            pos = fileSystem.lseek(index, pos);
            if (pos != -1) {
                System.out.printf("current position is %d\n", pos);
            }
            else {
                System.out.println("error");
            }
        }
        catch (NumberFormatException e) {
            System.out.println("Error");
        }
    }
}
