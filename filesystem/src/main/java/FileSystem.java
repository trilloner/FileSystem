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
        while ((status <= 0) && count > 0
                && (openFileTables[index].getCurrentPosition() < openFileTables[index].getLength())) {
            if (status < 0) {
                ioSystem.readBlock(descriptor[(-1) * status], openFileTables[index].getBuffer());
            }
            status = openFileTables[index].readByte(memArea, i);
            i++;
            count--;
        }
        return count;
    }

    public int write(int index, byte[] memArea, int count) {
        if (index > 3 || index < 0) {
            System.out.println("Write: Out of bound exception.");
            return -1;
        }
        if (count < 0) {
            System.out.println("Write: Cannot write negative amount.");
            return -1;
        }
        if (openFileTables[index].getDescriptorIndex() == -1) {
            System.out.println("Write: Cannot open the file.");
            return -1;
        }

        int status = openFileTables[index].getStatus();
        int descriptorIndex = openFileTables[index].getDescriptorIndex();
        descriptor = getDescriptor(descriptorIndex);
        int bitmapIndex;

        while (status != 4 && count > 0) {
            if (status < 0) {
                if (status < 1) {
                    ioSystem.writeBlock(descriptor[(-1) * status - 1], openFileTables[index].getBuffer());
                }
                ioSystem.readBlock(descriptor[(-1) * status], openFileTables[index].getBuffer());
            } else if (status > 0) {
                if (status > 1 && status != 5) {
                    ioSystem.writeBlock(descriptor[status - 1], openFileTables[index].getBuffer());
                }
                bitmapIndex = freeBitMap();
                if (bitmapIndex == -1) {
                    return count;
                }
                ioSystem.readBlock(0, buffer);
                buffer[bitmapIndex / 32] = buffer[bitmapIndex / 32] ; // TODO upgrade
                ioSystem.writeBlock(0, buffer);
                descriptor[status] = bitmapIndex;
                descriptor[0] = openFileTables[index].getCurrentPosition();
                setDescriptor(descriptorIndex);
                openFileTables[index].initBuffer();
            }
            status = openFileTables[index].writeByte(memArea);
            count--;
        }
        return count;
    }


    private int[] getDescriptor(int descriptorIndex) {
        ioSystem.readBlock(1 + (descriptorIndex / DESCRIPTOR_SIZE), buffer);
        for (int i = 0; i < DESCRIPTOR_SIZE; i++) {
            descriptor[i] = buffer[i + (descriptorIndex % DESCRIPTOR_SIZE) * DESCRIPTOR_SIZE];
        }
        return descriptor;
    }

    private void setDescriptor(int descriptorIndex) {
        ioSystem.readBlock(1 + (descriptorIndex / DESCRIPTOR_SIZE), buffer);
        for (int i = 0; i < DESCRIPTOR_SIZE; i++)
            buffer[i + (descriptorIndex % DESCRIPTOR_SIZE) * DESCRIPTOR_SIZE] = (byte) descriptor[i];
        ioSystem.writeBlock(1 + (descriptorIndex / 4), buffer);
    }

    private int freeBitMap() {
        int tmp;
        ioSystem.readBlock(0, buffer);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 32; j++) {
                tmp = buffer[i] ;// TODO upgrade
                if (tmp == 0) {
                    return i * 32 + j;
                }
            }
        }
        return -1;
    }
}
