/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import javax.swing.JTextArea;

/**
 *
 * @author Matevz
 */
public class FileInPackets implements Runnable {

    private static final int PACKET_SIZE = 512;
    private static final int TIMEOUT = 30000; // transmission timeout in ms
    private static final String CR_LF = "\r\n";
    private File otaImage;
    private long otaImageSize;
    private InputStream inputStream;
    private OutputStream outputStream;
    private JTextArea textWin;
    private String uri;
    private String recievedString;
    private boolean uploadingFirmware = false;
    private boolean breakTransmission = false;

    public FileInPackets(File otaImage, InputStream inputStream,
            OutputStream outputStream, JTextArea textWin, String uri) {
        this.otaImage = otaImage;
        otaImageSize = otaImage.length();
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.textWin = textWin;
        this.uri = uri;
        recievedString = "";
    }

    public boolean getBreakTransmission() {
        return breakTransmission;
    }

    public void setBreakTransmission(boolean breakTransmission) {
        this.breakTransmission = breakTransmission;
    }

    public boolean getUploadingFirmware() {
        return uploadingFirmware;
    }

    public void bufferAddChunk(byte[] chunk) {
        recievedString += new String(chunk);
    }

    public ArrayList<byte[]> getOtaPackets() {
        ArrayList<byte[]> otaPackets = new ArrayList<byte[]>();
        try {
            FileInputStream fin = new FileInputStream(otaImage);
            try {
                byte[] packetBuffer = new byte[PACKET_SIZE];
                int countBytes = 0;
                int b = 0;
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
                for (int i = 0; i < numOfFF; i++) {
                    byte[] lastPacket = otaPackets.get(otaPackets.size() - 1);
                    lastPacket[(lastPacket.length - 1) - i] = (byte) 255;
                    otaPackets.set(otaPackets.size() - 1, lastPacket);
                }
                addPacketsHeader(otaPackets);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                fin.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
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

    public synchronized void sendPackets() {
        uploadingFirmware = true;
        ArrayList<byte[]> packets = getOtaPackets();

        int transmissionCounter = 0;
        int retransmissionCounter = 0;
        for (int i = 0; i < packets.size(); i++) {
            if (retransmissionCounter >= 10 || breakTransmission) {
                break;
            }

            byte[] packet = packets.get(i);
            sendRequest(packet, i);

            boolean noResponse = true;
            long start = System.currentTimeMillis();
            while (noResponse && !breakTransmission) {
                long stop = System.currentTimeMillis();

                if (recievedString.contains("OK" + CR_LF) && !recievedString.contains("CORRUPTED-DATA") && !recievedString.contains("JUNK-INPUT")) {
                    transmissionCounter = i;
                    retransmissionCounter = 0;
                    recievedString = "";
                    noResponse = false;
                } else if (recievedString.contains("ERROR" + CR_LF + CR_LF + "OK" + CR_LF)) {
                    outputText("##Error - APPLICATION-ERROR -> retransmitting packet: " + i + "\n");
                    retransmissionCounter++;
                    i--;
                    recievedString = "";
                    noResponse = false;
                } else if (recievedString.contains("CORRUPTED-DATA" + CR_LF + CR_LF + "OK" + CR_LF)) {
                    outputText("##Error - CORRUPTED-DATA -> retransmitting packet: " + i + "\n");
                    retransmissionCounter++;
                    i--;
                    recievedString = "";
                    noResponse = false;
                } else if (recievedString.contains("JUNK-INPUT" + CR_LF + CR_LF + "OK" + CR_LF)) {
                    try {
                        outputStream.write("\r\n\r\n\r\n\r\n\r\n".getBytes());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    outputText("##Error - JUNK-INPUT -> retransmitting packet: " + i + "\n");
                    retransmissionCounter++;
                    i--;
                    recievedString = "";
                    noResponse = false;
                } else if ((stop - start) > TIMEOUT) {
                    try {
                        outputStream.write("\r\n\r\n\r\n\r\n\r\n".getBytes());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    outputText("##Timeout, retransmitting packet: " + i + "\n");
                    retransmissionCounter++;
                    i--;
                    recievedString = "";
                    noResponse = false;
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
        if (transmissionCounter == packets.size() - 1) {
            outputText("##Firmware successfully uploaded.\n");
        } else {
            outputText("##Fatal error transmitting firmware.\n");
        }
        uploadingFirmware = false;
    }

    public long calculateCrc(byte[] otaPacket) {
        Checksum checksum = new CRC32();
        checksum.update(otaPacket, 0, otaPacket.length);
        return checksum.getValue();
    }

    public long getOtaImageSize() {
        return otaImageSize;
    }

    private void sendRequest(byte[] packet, int packetNum) {
        String req = "POST " + uri + CR_LF;
        int dataLen = packet.length;
        String len = "Length=" + dataLen + CR_LF;
        byte[] byteReq = req.getBytes();

        Checksum checksum = new CRC32();
        checksum.update(byteReq, 0, byteReq.length);
        checksum.update(len.getBytes(), 0, len.length());
        checksum.update(packet, 0, packet.length);
        checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);

        long reqCrc = checksum.getValue();
        String strCrc = "CRC=" + reqCrc + CR_LF;
        try {
            if (outputStream != null) {
                outputStream.write(req.getBytes());
                outputText("\n<<" + req);
                outputStream.write(len.getBytes());
                outputText("<<" + len);
                outputStream.write(packet);
                outputStream.write(CR_LF.getBytes());
                outputText("##" + "Packet number: " + packetNum + "\n");
                outputStream.write(strCrc.getBytes());
                outputText("<<" + strCrc + "\n");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void outputText(String str) {
        textWin.append(str);
        textWin.setCaretPosition(textWin.getText().length());
    }

    @Override
    public void run() {
        sendPackets();
    }
}
