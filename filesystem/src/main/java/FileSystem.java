public class FileSystem {

    private IOSystem ioSystem;
    private OpenFileTable[] openFileTables;
    private UnsignedByteArray buffer;
    private int[] descriptor;
    private static final int MAX_OPEN_FILES = 4;
    private static final int DESCRIPTOR_SIZE = 4;
    private static final int FILENAME_SIZE = 4;
    private static final int INT_SIZE = 4;

    private static final int[] MASK = createMask();
    private static final int[] MASK2 = createMask2();


    public FileSystem(int length, int bufferSize) {

        ioSystem = new IOSystem(length, bufferSize);
        openFileTables = new OpenFileTable[MAX_OPEN_FILES];
        buffer = new UnsignedByteArray(bufferSize);
        descriptor = new int[DESCRIPTOR_SIZE];

        for (int i = 0; i < MAX_OPEN_FILES; i++) {
            openFileTables[i] = new OpenFileTable(bufferSize);
        }
        openFileTables[0].init(0, -1);
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

        if (searchDirectory(fName) != openFileTables[0].getLength()) {
            System.out.println("Error: File already exists");
            return false;
        }

        descriptorIndex = allocDescriptor();

        if (descriptorIndex == -1) {
            System.out.println("Error: Descriptor is already taken");
            return false;
        }

        allocDirectory();

        write(0, fName, fName.length());
        write(0, UnsignedByteArray.fromInt(descriptorIndex), INT_SIZE);

        setDescriptor(descriptorIndex);

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
        descriptor = getDescriptor(descriptorIndex);

        for (int i = 1; i < MAX_OPEN_FILES; i++) {
            if (openFileTables[i].getDescriptorIndex() == descriptorIndex) {
                System.out.println("Error: File already opened");
                return -1;
            }
        }

        for (int i = 1; i < MAX_OPEN_FILES; i++) {
            if (openFileTables[i].getDescriptorIndex() == -1) {
                openFileTables[i].init(descriptorIndex, descriptor[0]);
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

        int descriptorIndex, currentBlock, status;

        if (openFileTables[index].getDescriptorIndex() != -1) {
            descriptorIndex = openFileTables[index].getDescriptorIndex();
            descriptor[0] = openFileTables[index].getLength();

            setDescriptor(descriptorIndex);

            currentBlock = openFileTables[index].getCurrentBlock();
            status = openFileTables[index].getStatus();
            if (status == 1) {
                openFileTables[index].init();
                return index;
            } else if (status > 1 || status < -1) {
                currentBlock--;
                ioSystem.writeBlock(descriptor[currentBlock], openFileTables[index].getBuffer());
            } else {
                ioSystem.writeBlock(descriptor[currentBlock], openFileTables[index].getBuffer());
            }
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

    private int allocDescriptor() {
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

        if (openFileTables[0].getCurrentPosition() == buffer.length() * 3) {
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
        descriptor = getDescriptor(descriptorIndex);
        int bitmapIndex;
        int i = 0;

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

                int bufferIntIndex = (bitmapIndex / 32) * INT_SIZE;
                UnsignedByteArray bitmapPart = UnsignedByteArray.fromInt(
                        buffer.subArray(bufferIntIndex, bufferIntIndex + INT_SIZE).toInt() | MASK[bitmapIndex % 32]
                );

                for (int j = bufferIntIndex, k = 0; k < INT_SIZE; j++, k++) {
                    buffer.set(j, bitmapPart.get(k));
                }

                ioSystem.writeBlock(0, buffer);

                descriptor[status] = bitmapIndex;
                descriptor[0] = openFileTables[index].getCurrentPosition();
                setDescriptor(descriptorIndex);
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
        getDescriptor(descriptorIndex);

        int currentBlock = openFileTables[index].getDescriptorIndex();
        int status = openFileTables[index].getStatus();

        if (status == 0) {
            ioSystem.writeBlock(descriptor[currentBlock], openFileTables[index].getBuffer());
        } else {
            if (status != 1 && status != -1) {
                ioSystem.writeBlock(descriptor[currentBlock - 1], openFileTables[index].getBuffer());
            }
        }
        openFileTables[index].seek(pos);
        status = openFileTables[index].getStatus();
        currentBlock = openFileTables[index].getCurrentBlock();
        if (status <= 0) {
            ioSystem.readBlock(descriptor[currentBlock], openFileTables[index].getBuffer());
        }
        return pos;
    }


    private int[] getDescriptor(int descriptorIndex) {
        // TODO upgrade
        ioSystem.readBlock(1 + (descriptorIndex / DESCRIPTOR_SIZE), buffer);
        for (int i = 0; i < DESCRIPTOR_SIZE; i++) {
            descriptor[i] = buffer.get(i + (descriptorIndex % DESCRIPTOR_SIZE) * DESCRIPTOR_SIZE);
        }
        return descriptor;
    }

    private void setDescriptor(int descriptorIndex) {
        // TODO upgrade
        ioSystem.readBlock(1 + (descriptorIndex / DESCRIPTOR_SIZE), buffer);
        for (int i = 0; i < DESCRIPTOR_SIZE; i++) {
            buffer.set(i + (descriptorIndex % DESCRIPTOR_SIZE) * DESCRIPTOR_SIZE, descriptor[i]);
        }
        ioSystem.writeBlock(1 + (descriptorIndex / DESCRIPTOR_SIZE), buffer);
    }

    private int freeBitMap() {
        int tmp;
        ioSystem.readBlock(0, buffer);
        for (int i = 0; i < 2 * INT_SIZE; i += INT_SIZE) {
            for (int j = 0; j < 32; j++) {
                tmp = buffer.subArray(i, i + INT_SIZE).toInt() & MASK[j];
                if (tmp == 0) {
                    return i * 32 + j;
                }
            }
        }
        return -1;
    }
}
