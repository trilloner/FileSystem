import java.util.Arrays;

public class FileSystem {

    private IOSystem ioSystem;
    private OpenFileTable[] openFileTables;
    private byte[] buffer;
    private int[] descriptor;
    private static final int MAX_OPEN_FILES = 4;
    private static final int DESCRIPTOR_SIZE = 4;


    public FileSystem(int bufferSize) {

        ioSystem = new IOSystem();
        openFileTables = new OpenFileTable[MAX_OPEN_FILES];
        buffer = new byte[bufferSize];
        descriptor = new int[DESCRIPTOR_SIZE];

        for (int i = 0; i < MAX_OPEN_FILES; i++) {
            openFileTables[i] = new OpenFileTable(bufferSize);
        }
    }

    public int read(int index, byte[] memArea, int count) {
        int i = 0;
        if (index > 3 || index < 0) {
            System.out.println("Read: Out of bound exception.");
            return -1;
        }
        if (count < 0) {
            System.out.println("Read: Count cannot be negative.");
            return -1;
        }
        int status = openFileTables[index].getStatus();
        int descriptorIndex = openFileTables[index].getDescriptorIndex();
        descriptor = getDescriptor(descriptorIndex);
        while ((status <= 0) && count > 0 && (openFileTables[index].getCurrentPosition() < openFileTables[index].getLength())) {
            if (status < 0) {
                ioSystem.readBlock(descriptor[(-1) * status], openFileTables[index].getBuffer());
            }
            status = openFileTables[index].readByte(memArea, i);
            i++;
            count--;
        }
        return count;
    }

    //TODO add function logic
    private int[] getDescriptor(int descriptorIndex) {
        return new int[DESCRIPTOR_SIZE];
    }
}
