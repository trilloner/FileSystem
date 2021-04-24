import array.IntArray;
import array.UnsignedByteArray;

public class FileSystem {

    private IOSystem ioSystem;
    private OpenFileTable[] openFileTables;
    private UnsignedByteArray buffer;
    private static final int MAX_OPEN_FILES = 4;
    private static final int DESCRIPTOR_SIZE = 4;
    private static final int FILENAME_SIZE = 4;
    private static final int INT_SIZE = 4;
    private static final int NOT_ALLOCATED_INDEX = 255;

    private static final int[] MASK = createMask();
    private static final int[] MASK2 = createMask2();


    public FileSystem(int length, int bufferSize) {

        ioSystem = new IOSystem(length, bufferSize);
        openFileTables = new OpenFileTable[MAX_OPEN_FILES];
        buffer = new UnsignedByteArray(bufferSize);

        for (int i = 0; i < MAX_OPEN_FILES; i++) {
            openFileTables[i] = new OpenFileTable(bufferSize);
        }
        openFileTables[0].init(0, 0);
    }

    private static int[] createMask() {
        int[] mask = new int[32];
        mask[31] = 1;
        for (int i = 30; i >= 0; i--) {
            mask[i] = mask[i + 1] << 1;
        }
        return mask;
    }

    private static int[] createMask2() {
        int[] mask2 = new int[32];
        for (int i = 0; i < 32; i++) {
            mask2[i] = ~FileSystem.MASK[i];
        }
        return mask2;
    }

    public boolean create(UnsignedByteArray fName) {
        int descriptorIndex;

        fName = fName.fillToLength(FILENAME_SIZE);

        if (searchDirectory(fName) != -1) {
            System.out.println("Error: File already exists");
            return false;
        }

        descriptorIndex = getFreeDescriptorIndex();

        if (descriptorIndex == -1) {
            System.out.println("Error: Descriptor is already taken");
            return false;
        }

        int freeBlockIndex = readBitmapAndGetFreeBlockIndex();
        if (freeBlockIndex == NOT_ALLOCATED_INDEX) {
            System.out.println("Error: No free block");
            return false;
        }
        IntArray bufferAsIntArray = buffer.asIntArray();
        bufferAsIntArray.set(freeBlockIndex / 32, bufferAsIntArray.get(freeBlockIndex / 32) | MASK[freeBlockIndex % 32]);

        allocDirectory();

        write(0, fName, fName.length());
        write(0, UnsignedByteArray.fromInt(descriptorIndex), INT_SIZE);

        setDescriptor(descriptorIndex, new Descriptor(
                0, new int[] {freeBlockIndex, NOT_ALLOCATED_INDEX, NOT_ALLOCATED_INDEX}));

        return true;
    }

    public int open(UnsignedByteArray fName) {
        fName = fName.fillToLength(INT_SIZE);
        lseek(0, 0);
        int descriptorIndex;

        int directoryIndex = searchDirectory(fName);

        if (directoryIndex == openFileTables[0].getLength()) {
            System.out.println("Error: File not already exists");
            return -1;
        }

        read(0, buffer, FILENAME_SIZE + INT_SIZE);
        descriptorIndex = buffer.subArray(FILENAME_SIZE, FILENAME_SIZE + INT_SIZE).toInt();
        Descriptor descriptor = getDescriptor(descriptorIndex);

        for (int i = 1; i < MAX_OPEN_FILES; i++) {
            if (openFileTables[i].getDescriptorIndex() == descriptorIndex) {
                System.out.println("Error: File already opened");
                return -1;
            }
        }

        for (int i = 1; i < MAX_OPEN_FILES; i++) {
            if (openFileTables[i].getDescriptorIndex() == -1) {
                openFileTables[i].init(descriptorIndex, descriptor.getFileLength());
                return i;
            }
        }

        System.out.println("Error: OpenFileTable is full");
        return -1;
    }

    public int close(int index) {
        if (index > 3 || index < 0) {
            System.out.println("Index out of bound");
            return -1;
        }

        int descriptorIndex = openFileTables[index].getDescriptorIndex();
        int currentBlock = openFileTables[index].getCurrentBlock();
        int status = openFileTables[index].getStatus();

        if (descriptorIndex != -1) {
            Descriptor descriptor = getDescriptor(descriptorIndex);
            descriptor.setFileLength(openFileTables[index].getLength());
            setDescriptor(descriptorIndex, descriptor);

            if (status == 1) {
                openFileTables[index].init();
                return index;
            }

            if (status > 1 || status < -1) {
                currentBlock--;
            }

            ioSystem.writeBlock(descriptor.getBlockIndex(currentBlock), openFileTables[index].getBuffer());

            openFileTables[index].init();
            return index;
        } else {
            System.out.println("Error: File is not opened");
            return -1;
        }
    }

    private int searchDirectory(UnsignedByteArray dName) {
        lseek(0, 0);
        UnsignedByteArray temp = new UnsignedByteArray(8);

        while (openFileTables[0].getCurrentPosition() < openFileTables[0].getLength()) {
            read(0, temp, FILENAME_SIZE + INT_SIZE);

            if (dName.equals(temp.subArray(FILENAME_SIZE))) {
                lseek(0, openFileTables[0].getCurrentPosition() - FILENAME_SIZE - INT_SIZE);
                return openFileTables[0].getCurrentPosition();
            }
        }
        return -1;
    }

    private int getFreeDescriptorIndex() {
        for (int i = 0; i < 6; i++) {
            ioSystem.readBlock(i + 1, buffer);
            for (int j = 0; j < buffer.length() / DESCRIPTOR_SIZE; j++) {
                if (buffer.get(j * DESCRIPTOR_SIZE + 1) == 0)
                    return (i * buffer.length() / DESCRIPTOR_SIZE) + j;
            }
        }
        return -1;
    }

    private int allocDirectory() {
        lseek(0, 0);
        UnsignedByteArray temp = new UnsignedByteArray(FILENAME_SIZE);
        searchDirectory(temp);

        if (openFileTables[0].getCurrentPosition() == buffer.length()) {
            return -1;
        }
        return openFileTables[0].getCurrentPosition();
    }

    public int read(int index, UnsignedByteArray memArea, int count) {
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
        Descriptor descriptor = getDescriptor(descriptorIndex);

        while ((status <= 0) && count > 0
                && (openFileTables[index].getCurrentPosition() < openFileTables[index].getLength())) {
            if (status < 0) {
                ioSystem.readBlock(descriptor.getBlockIndex(-1 * status - 1), openFileTables[index].getBuffer());
            }
            status = openFileTables[index].readByte(memArea, i);
            i++;
            count--;
        }
        return count;
    }

    public int write(int index, UnsignedByteArray memArea, int count) {
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
        Descriptor descriptor = getDescriptor(descriptorIndex);
        int freeBlockIndex;
        int i = 0;

        while (status != 4 && count > 0) {
            if (status < 0) {
                ioSystem.writeBlock(descriptor.getBlockIndex(-1 * status - 2), openFileTables[index].getBuffer());
                ioSystem.readBlock(descriptor.getBlockIndex(-1 * status - 1), openFileTables[index].getBuffer());
            } else if (status > 0) {
                if (status > 1 && status != 5) {
                    ioSystem.writeBlock(descriptor.getBlockIndex(status - 2), openFileTables[index].getBuffer());
                }
                freeBlockIndex = readBitmapAndGetFreeBlockIndex();
                if (freeBlockIndex == NOT_ALLOCATED_INDEX) {
                    return count;
                }

                IntArray bufferAsIntArray = buffer.asIntArray();
                bufferAsIntArray.set(freeBlockIndex / 32, bufferAsIntArray.get(freeBlockIndex / 32) | MASK[freeBlockIndex % 32]);

                ioSystem.writeBlock(0, buffer);

                descriptor.setBlockIndex(status - 1, freeBlockIndex);
                descriptor.setFileLength(openFileTables[index].getCurrentPosition());
                setDescriptor(descriptorIndex, descriptor);
                openFileTables[index].initBuffer();
            }
            status = openFileTables[index].writeByte(memArea.get(i));
            count--;
            i++;
        }
        return count;
    }

    public int lseek(int index, int pos) {
        if (index > (MAX_OPEN_FILES - 1) || index < 0) {
            System.out.println("Lseek: Index out of bound.");
            return -1;
        }
        if (openFileTables[index].getDescriptorIndex() == -1) {
            System.out.println("Lseek: File not opened.");
            return -1;
        }
        if (openFileTables[index].getLength() + 1 < pos || pos < 0 || pos == buffer.length() * 3) {
            System.out.println("Lseek: Out of index.");
            return -1;
        }

        int descriptorIndex = openFileTables[index].getDescriptorIndex();
        Descriptor descriptor = getDescriptor(descriptorIndex);

        int currentBlock = openFileTables[index].getDescriptorIndex();
        int status = openFileTables[index].getStatus();

        if (status == 0) {
            ioSystem.writeBlock(descriptor.getBlockIndex(currentBlock), openFileTables[index].getBuffer());
        } else {
            if (status != 1 && status != -1) {
                ioSystem.writeBlock(descriptor.getBlockIndex(currentBlock - 1), openFileTables[index].getBuffer());
            }
        }
        openFileTables[index].seek(pos);
        status = openFileTables[index].getStatus();
        currentBlock = openFileTables[index].getCurrentBlock();
        if (status <= 0) {
            ioSystem.readBlock(descriptor.getBlockIndex(currentBlock), openFileTables[index].getBuffer());
        }
        return pos;
    }


    private Descriptor getDescriptor(int descriptorIndex) {
        ioSystem.readBlock(1 + descriptorIndex * DESCRIPTOR_SIZE / buffer.length(), buffer);

        int descriptorIndexInBuffer = descriptorIndex * DESCRIPTOR_SIZE % buffer.length();
        return new Descriptor(buffer.subArray(descriptorIndexInBuffer, descriptorIndexInBuffer + DESCRIPTOR_SIZE));
    }

    private void setDescriptor(int descriptorIndex, Descriptor descriptor) {
        int blockIndex = 1 + descriptorIndex * DESCRIPTOR_SIZE / buffer.length();
        ioSystem.readBlock(blockIndex, buffer);

        int descriptorIndexInBuffer = descriptorIndex * DESCRIPTOR_SIZE % buffer.length();
        buffer.set(descriptorIndexInBuffer, descriptor.getFileLength());
        for (int i = descriptorIndexInBuffer + 1; i < DESCRIPTOR_SIZE; i++) {
            buffer.set(i, descriptor.getBlockIndex(i - 1));
        }

        ioSystem.writeBlock(blockIndex, buffer);
    }

    private int readBitmapAndGetFreeBlockIndex() {
        ioSystem.readBlock(0, buffer);
        IntArray bufferAsIntArray = buffer.asIntArray();
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 32; j++) {
                if ((bufferAsIntArray.get(i) & MASK[j]) == 0) {
                    return i * 32 + j;
                }
            }
        }
        return NOT_ALLOCATED_INDEX;
    }
}
