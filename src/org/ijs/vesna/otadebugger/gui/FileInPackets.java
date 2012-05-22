package org.ijs.vesna.otadebugger.gui;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 *
 * @author Matevz
 */
public class FileInPackets {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(FileInPackets.class);
    
    private static final int PACKET_SIZE = 512;
    private File otaImage;
    private long otaImageSize;

    public ArrayList<byte[]> getOtaPackets(File file) {
        this.otaImage = file;
        otaImageSize = file.length();
        ArrayList<byte[]> otaPackets = new ArrayList<byte[]>();
        try {
            FileInputStream fin = new FileInputStream(otaImage);
            try {
                byte[] packetBuffer = new byte[PACKET_SIZE];
                int countBytes = 0;
                int b;
                while ((b = fin.read()) != -1) {
                    packetBuffer[countBytes++] = (byte) b;
                    if (countBytes == PACKET_SIZE) {
                        otaPackets.add(packetBuffer);
                        packetBuffer = new byte[PACKET_SIZE];
                        countBytes = 0;
                    }
                }
                otaPackets.add(packetBuffer);

                // fill the last buffer with FFs
                int packetsLen = otaPackets.size() * PACKET_SIZE;
                int numOfFF = packetsLen - (int) otaImageSize;
                if (numOfFF > 0) {
                    for (int i = 0; i < numOfFF; i++) {
                        byte[] lastPacket = otaPackets.get(otaPackets.size() - 1);
                        lastPacket[(lastPacket.length - 1) - i] = (byte) 255;
                        otaPackets.set(otaPackets.size() - 1, lastPacket);
                    }
                }
                addPacketsHeader(otaPackets);
            } catch (Exception ex) {
                logger.error(ex);
            } finally {
                fin.close();
            }
        } catch (Exception ex) {
            logger.error(ex);
        }
        return otaPackets;
    }

    public byte[] intToByteArray(int value) {
        return new byte[]{
                    (byte) (value >>> 24),
                    (byte) (value >>> 16),
                    (byte) (value >>> 8),
                    (byte) value};
    }

    public ArrayList<byte[]> addPacketsHeader(ArrayList<byte[]> packets) {
        for (int i = 0; i < packets.size(); i++) {
            ByteArrayOutputStream os = new ByteArrayOutputStream(PACKET_SIZE + 8);
            byte[] offset = intToByteArray(i);
            os.write(offset, 0, offset.length);
            int tmp = packets.get(i).length;
            os.write(packets.get(i), 0, packets.get(i).length);
            long numCrc = calculateCrc(os.toByteArray());
            byte[] crc = ByteBuffer.allocate(8).putLong(numCrc).array();
            os.write(crc, 4, crc.length - 4);
            packets.set(i, os.toByteArray());
        }

        return packets;
    }

    public long calculateCrc(byte[] otaPacket) {
        Checksum checksum = new CRC32();
        checksum.update(otaPacket, 0, otaPacket.length);
        return checksum.getValue();
    }

    public long getOtaImageSize() {
        return otaImageSize;
    }
}
