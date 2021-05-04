import array.UnsignedByteArray;

import java.util.ArrayList;
import java.util.List;

public class FileSystem {
    private IOSystem ioSystem;
    private Bitmap bitmap;
    private OpenFileTable[] openFileTables;
    private UnsignedByteArray buffer;
    private static final int MAX_OPEN_FILES = 4;
    private static final int DESCRIPTOR_SIZE = 4;
    private static final int FILENAME_SIZE = 4;
    static final int NOT_ALLOCATED_INDEX = 255;


    public FileSystem(int length, int bufferSize) {
        ioSystem = new IOSystem(length, bufferSize);
        bitmap = new Bitmap(ioSystem);
        openFileTables = new OpenFileTable[MAX_OPEN_FILES];
        buffer = new UnsignedByteArray(bufferSize);

        for (int i = 0; i < MAX_OPEN_FILES; i++) {
            openFileTables[i] = new OpenFileTable(bufferSize);
        }
        openFileTables[0].init(0, 0);

        setDescriptor(0, new Descriptor(0,
                new int[]{NOT_ALLOCATED_INDEX, NOT_ALLOCATED_INDEX, NOT_ALLOCATED_INDEX}));

    }

    public boolean create(UnsignedByteArray fName) {

        fName = fName.fillToLength(FILENAME_SIZE);

        if (searchDirectory(fName) != -1) {
            System.out.println("err: File already exists");
            return false;
        }

        int descriptorIndex = getFreeDescriptorIndex();

        if (descriptorIndex == -1) {
            System.out.println("err: Descriptor is already taken");
            return false;
        }

        allocDirectory();

        write(0, fName, fName.length());
        write(0, UnsignedByteArray.fromInt(descriptorIndex), Integer.BYTES);

        setDescriptor(descriptorIndex, new Descriptor(
                0, new int[]{NOT_ALLOCATED_INDEX, NOT_ALLOCATED_INDEX, NOT_ALLOCATED_INDEX}));

        return true;
    }

    public boolean destroy(UnsignedByteArray fName) {
        fName = fName.fillToLength(FILENAME_SIZE);
        lseek(0, 0);

        if (searchDirectory(fName) == -1) {
            System.out.println("err: File not created");
            return false;
        }
        read(0, buffer, FILENAME_SIZE + Integer.BYTES);

        int descriptorIndex = buffer.subArray(FILENAME_SIZE, FILENAME_SIZE + Integer.BYTES).toInt();
        for (int i = 1; i < MAX_OPEN_FILES; i++) {
            if (openFileTables[i].getDescriptorIndex() == descriptorIndex) {
                System.out.println("File opened!");
                return false;
            }
        }

        ioSystem.readBlock(0, buffer);

        Descriptor descriptor = getDescriptor(descriptorIndex);
        descriptor.setFileLength(-1);
        for (int i = 0; i < 3; i++) {
            int blockIndex = descriptor.getBlockIndex(i);
            if (blockIndex != NOT_ALLOCATED_INDEX) {
                bitmap.setBlockIndexFree(blockIndex);
                descriptor.setBlockIndex(i, NOT_ALLOCATED_INDEX);
            }
        }
        setDescriptor(descriptorIndex, descriptor);

        ioSystem.writeBlock(0, buffer);

        searchDirectory(fName);
        write(0, new UnsignedByteArray(FILENAME_SIZE + Integer.BYTES), FILENAME_SIZE + Integer.BYTES);

        return true;
    }

    public int open(UnsignedByteArray fName) {
        fName = fName.fillToLength(FILENAME_SIZE);
        lseek(0, 0);

        int directoryIndex = searchDirectory(fName);

        if (directoryIndex == -1) {
            System.out.println("err: File not already exists");
            return -1;
        }

        read(0, buffer, FILENAME_SIZE + Integer.BYTES);
        int descriptorIndex = buffer.subArray(FILENAME_SIZE, FILENAME_SIZE + Integer.BYTES).toInt();
        Descriptor descriptor = getDescriptor(descriptorIndex);

        for (int i = 1; i < MAX_OPEN_FILES; i++) {
            if (openFileTables[i].getDescriptorIndex() == descriptorIndex) {
                System.out.println("err: File already opened");
                return -1;
            }
        }

        for (int i = 1; i < MAX_OPEN_FILES; i++) {
            if (openFileTables[i].getDescriptorIndex() == -1) {
                openFileTables[i].init(descriptorIndex, descriptor.getFileLength());
                return i;
            }
        }

        System.out.println("err: OpenFileTable is full");
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
            System.out.println("err: File is not opened");
            return -1;
        }
    }

    private int searchDirectory(UnsignedByteArray fName) {
        lseek(0, 0);
        UnsignedByteArray temp = new UnsignedByteArray(8);

        while (openFileTables[0].getCurrentPosition() < openFileTables[0].getLength()) {
            read(0, temp, FILENAME_SIZE + Integer.BYTES);

            if (fName.equals(temp.subArray(FILENAME_SIZE))) {
                lseek(0, openFileTables[0].getCurrentPosition() - FILENAME_SIZE - Integer.BYTES);
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
        int tempCount = count;
        int status = openFileTables[index].getStatus();
        int descriptorIndex = openFileTables[index].getDescriptorIndex();
        Descriptor descriptor = getDescriptor(descriptorIndex);

        while ((status <= 0) && tempCount > 0
                && (openFileTables[index].getCurrentPosition() < openFileTables[index].getLength())) {
            if (status < 0) {
                ioSystem.readBlock(descriptor.getBlockIndex(-1 * status - 1), openFileTables[index].getBuffer());
            }
            status = openFileTables[index].readByte(memArea, i);
            i++;
            tempCount--;
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

        int tempCount = count;

        int status = openFileTables[index].getStatus();
        int descriptorIndex = openFileTables[index].getDescriptorIndex();
        Descriptor descriptor = getDescriptor(descriptorIndex);
        int freeBlockIndex;
        int i = 0;

        while (status != 4 && tempCount > 0) {
            if (status < 0) {
                if (status < -1) {
                    ioSystem.writeBlock(descriptor.getBlockIndex(-1 * status - 2), openFileTables[index].getBuffer());
                }
                ioSystem.readBlock(descriptor.getBlockIndex(-1 * status - 1), openFileTables[index].getBuffer());
            } else if (status > 0) {
                if (status > 1) {
                    ioSystem.writeBlock(descriptor.getBlockIndex(status - 2), openFileTables[index].getBuffer());
                }
                freeBlockIndex = bitmap.getFreeBlockIndex();
                if (freeBlockIndex == NOT_ALLOCATED_INDEX) {
                    return tempCount;
                }

                bitmap.setBlockIndexTaken(freeBlockIndex);

                descriptor.setBlockIndex(status - 1, freeBlockIndex);
                descriptor.setFileLength(openFileTables[index].getCurrentPosition());
                setDescriptor(descriptorIndex, descriptor);
                openFileTables[index].initBuffer();
            }
            if (memArea.length() <= i) {
                status = openFileTables[index].writeByte(memArea.get(memArea.length() - 1));
            } else {
                status = openFileTables[index].writeByte(memArea.get(i));
            }
            tempCount--;
            i++;
        }
        return tempCount == 0 ? count : count - tempCount;
    }

    public int lseek(int index, int pos) {
        if (index > (MAX_OPEN_FILES - 1) || index < 0) {
            System.out.println("lseek: file index is out of range");
            return -1;
        }
        if (openFileTables[index].getDescriptorIndex() == -1) {
            System.out.println("lseek: file is not opened");
            return -1;
        }
        if (openFileTables[index].getLength() + 1 < pos || pos < 0 || pos == buffer.length() * 3) {
            System.out.println("position is out of range");
            return -1;
        }

        int descriptorIndex = openFileTables[index].getDescriptorIndex();
        Descriptor descriptor = getDescriptor(descriptorIndex);

        int currentBlock = openFileTables[index].getCurrentBlock();
        int status = openFileTables[index].getStatus();

        if (status == 0) {
            ioSystem.writeBlock(descriptor.getBlockIndex(currentBlock), openFileTables[index].getBuffer());
        } else if (status != 1 && status != -1) {
            ioSystem.writeBlock(descriptor.getBlockIndex(currentBlock - 1), openFileTables[index].getBuffer());
        }

        openFileTables[index].seek(pos);
        status = openFileTables[index].getStatus();
        currentBlock = openFileTables[index].getCurrentBlock();
        if (status <= 0) {
            ioSystem.readBlock(descriptor.getBlockIndex(currentBlock), openFileTables[index].getBuffer());
        }
        return pos;
    }

    public List<Pair<String, Integer>> directory() {
        var fileInfos = new ArrayList<Pair<String, Integer>>();

        lseek(0, 0);
        var memArea = new UnsignedByteArray(FILENAME_SIZE + DESCRIPTOR_SIZE);
        while (openFileTables[0].getCurrentPosition() < openFileTables[0].getLength()) {
            read(0, memArea, FILENAME_SIZE + DESCRIPTOR_SIZE);

            if (!memArea.all(value -> value == 0)) {
                String name = memArea.subArray(FILENAME_SIZE).toAsciiString();
                int descriptorIndex = memArea.subArray(FILENAME_SIZE, FILENAME_SIZE + DESCRIPTOR_SIZE).toInt();
                fileInfos.add(new Pair<>(name, getDescriptor(descriptorIndex).getFileLength()));
            }
        }

        return fileInfos;
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
        buffer.setSubArray(descriptorIndexInBuffer, descriptor.asUnsignedByteArray());

        ioSystem.writeBlock(blockIndex, buffer);
    }
}
