import array.UnsignedByteArray;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

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


    public boolean save(String filename) {
        for (int i = 1; i < openFileTables.length; i++) {
            if (openFileTables[i].getDescriptorIndex() != -1) {
                close(i);
            }
        }

        lseek(0, 0);

        try (var writer = new FileWriter(filename)) {
            writer.write(String.format("%d\n", openFileTables[0].getLength()));

            for (int i = 0; i < ioSystem.getLength(); i++) {
                ioSystem.readBlock(i, buffer);
                for (int j = 0; j < buffer.length(); j++) {
                    writer.write(String.format("%-3d ", buffer.get(j)));
                }
                writer.write("\n");
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean init(String filename) {
        try (var reader = new Scanner(new File(filename))) {
            int directoryLength = 0;
            if (reader.hasNextLine()) {
                directoryLength = Integer.parseInt(reader.nextLine());
            }

            int i = 0;
            while (reader.hasNextLine()) {
                Integer[] block = Arrays.stream(reader.nextLine().split(" "))
                        .filter(s -> !s.equals("")).map(Integer::parseInt).toArray(Integer[]::new);
                for (int j = 0; j < block.length; j++) {
                    buffer.set(j, block[j]);
                }
                ioSystem.writeBlock(i, buffer);
                i++;
            }

            bitmap.refresh();
            openFileTables[0].init(0, directoryLength);

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
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

        if (!allocDirectory()) {
            System.out.println("err: Directory is full");
            return false;
        }

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

        Descriptor descriptor = getDescriptor(descriptorIndex);
        descriptor.setFileLength(0);
        for (int i = 0; i < 3; i++) {
            int blockIndex = descriptor.getBlockIndex(i);
            if (blockIndex != NOT_ALLOCATED_INDEX) {
                bitmap.setBlockIndexFree(blockIndex);
            }
            descriptor.setBlockIndex(i, 0);
        }
        setDescriptor(descriptorIndex, descriptor);

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
                if (descriptor.getFileLength() != 0){
                    ioSystem.readBlock(descriptor.getBlockIndex(0), openFileTables[i].getBuffer());
                }
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

            if (openFileTables[index].isWritten()) {
                ioSystem.writeBlock(descriptor.getBlockIndex(currentBlock), openFileTables[index].getBuffer());
            }

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

    private boolean allocDirectory() {
        lseek(0, 0);
        UnsignedByteArray temp = new UnsignedByteArray(FILENAME_SIZE);
        searchDirectory(temp);

        return openFileTables[0].getCurrentPosition() < buffer.length() * 3;
    }

    public int read(int index, UnsignedByteArray memArea, int count) {
        if (index > 3 || index < 0) {
            System.out.println("Read: Out of bound exception.");
            return -1;
        }
        if (count < 0) {
            System.out.println("Read: Count cannot be negative.");
            return -1;
        }

        int i = 0;
        int status = openFileTables[index].getStatus();
        int descriptorIndex = openFileTables[index].getDescriptorIndex();
        Descriptor descriptor = getDescriptor(descriptorIndex);

        while ((status <= 0) && i < count
                && (openFileTables[index].getCurrentPosition() < openFileTables[index].getLength())) {
            if (status < 0) {
                if (openFileTables[index].isWritten()) {
                    ioSystem.writeBlock(descriptor.getBlockIndex(openFileTables[index].getCurrentBlock()), openFileTables[index].getBuffer());
                    openFileTables[index].setWritten(false);
                }
                ioSystem.readBlock(descriptor.getBlockIndex(-1 * status - 1), openFileTables[index].getBuffer());
                openFileTables[index].setRead(true);
            }
            else if (!openFileTables[index].isRead()) {
                ioSystem.readBlock(descriptor.getBlockIndex(openFileTables[index].getCurrentBlock()), openFileTables[index].getBuffer());
                openFileTables[index].setRead(true);
            }

            status = openFileTables[index].readByte(memArea, i);
            i++;
        }

        return i;
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

        while (status != 4 && i < count) {
            if (status < 0) {
                if (status < -1 && openFileTables[index].isWritten()) {
                    ioSystem.writeBlock(descriptor.getBlockIndex(-1 * status - 2), openFileTables[index].getBuffer());
                    openFileTables[index].setWritten(false);
                }

                ioSystem.readBlock(descriptor.getBlockIndex(-1 * status - 1), openFileTables[index].getBuffer());
                openFileTables[index].setRead(true);
            } else if (status > 0) {
                if (status > 1 && openFileTables[index].isWritten()) {
                    ioSystem.writeBlock(descriptor.getBlockIndex(status - 2), openFileTables[index].getBuffer());
                    openFileTables[index].setWritten(false);
                }

                freeBlockIndex = bitmap.getFreeBlockIndex();
                if (freeBlockIndex == NOT_ALLOCATED_INDEX) {
                    return i;
                }

                bitmap.setBlockIndexTaken(freeBlockIndex);

                descriptor.setBlockIndex(status - 1, freeBlockIndex);
                descriptor.setFileLength(openFileTables[index].getCurrentPosition());
                setDescriptor(descriptorIndex, descriptor);
                openFileTables[index].setRead(false);
            }
            if (!openFileTables[index].isRead()) {
                ioSystem.readBlock(descriptor.getBlockIndex(openFileTables[index].getCurrentBlock()), openFileTables[index].getBuffer());
                openFileTables[index].setRead(true);
            }

            if (memArea.length() <= i) {
                status = openFileTables[index].writeByte(memArea.get(memArea.length() - 1));
            } else {
                status = openFileTables[index].writeByte(memArea.get(i));
            }
            openFileTables[index].setWritten(true);
            i++;
        }

        return i;
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

        int prevBlock = openFileTables[index].getCurrentBlock();
        int prevStatus = openFileTables[index].getStatus();

        openFileTables[index].seek(pos);

        int status = openFileTables[index].getStatus();

        if (status <= 0 && openFileTables[index].isWritten()) {
            if (prevStatus == 0) {
                ioSystem.writeBlock(descriptor.getBlockIndex(prevBlock), openFileTables[index].getBuffer());
            } else if (prevStatus != 1 && prevStatus != -1) {
                ioSystem.writeBlock(descriptor.getBlockIndex(prevBlock - 1), openFileTables[index].getBuffer());
            }

            openFileTables[index].setWritten(false);
            openFileTables[index].setRead(false);
        }

        return openFileTables[index].getCurrentPosition();
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
