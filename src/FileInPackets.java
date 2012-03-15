/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.*;
import java.util.ArrayList;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import javax.swing.JTextArea;

/**
 *
 * @author Matevz
 */
public class FileInPackets implements Runnable {

    private static final int PACKET_SIZE = 512;
    private static final int SECOND = 1000;
    private static final String CR_LF = "\r\n";
    private File otaImage;
    private long otaImageSize;
    private InputStream inputStream;
    private OutputStream outputStream;
    private JTextArea textWin;
    private String uri;

    public FileInPackets(File otaImage, InputStream inputStream,
            OutputStream outputStream, JTextArea textWin, String uri) {
        this.otaImage = otaImage;
        otaImageSize = otaImage.length();
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.textWin = textWin;
        this.uri = uri;
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

    public void sendPackets() {
        ArrayList<byte[]> packets = getOtaPackets();

        int transmissionCounter = 0;
        int retransmissionCounter = 0;
        for (int i = 0; i < packets.size(); i++) {
            if (retransmissionCounter >= 10) {
                break;
            }

            byte[] packet = packets.get(i);
            sendRequest(packet, i);
            String recievedStr = "";
            int rbyte = 0;   //recieved byte       
            try {
                long start = System.currentTimeMillis();
                while (inputStream != null && ((rbyte = inputStream.read()) != -1)) {
                    if (rbyte != 0) {
                        recievedStr += (char) rbyte;
                        long stop = System.currentTimeMillis();
                        if (recievedStr.contains("error" + CR_LF + "OK" + CR_LF) || (stop - start) > 60 * SECOND) {
                            textWin.append(">>" + recievedStr.replace(CR_LF, "\n"));
                            textWin.append("##Retransmitting packet: " + i + "\n");
                            retransmissionCounter++;
                            i--;
                            break;
                        } else if (recievedStr.contains("OK" + CR_LF) && !recievedStr.contains("error")) {
                            textWin.append(">>" + recievedStr.replace(CR_LF, "\n"));
                            transmissionCounter = i;
                            break;
                        }
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        if (transmissionCounter == packets.size()) {
            textWin.append("##Firmware successfully uploaded.\n");
        } else {
            textWin.append("##Fatal error transmitting firmware.\n");
        }
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
        String len = "Length=" + packet.length + CR_LF;
        byte[] byteReq = req.getBytes();
        Long crc = calculateCrc(packet);

        Checksum checksum = new CRC32();
        checksum.update(byteReq, 0, byteReq.length);
        checksum.update(packet, 0, packet.length);
        checksum.update(crc.intValue());
        checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);

        long reqCrc = checksum.getValue();
        String strCrc = "CRC=" + reqCrc + CR_LF;
        try {
            if (outputStream != null) {
                outputStream.write(req.getBytes());
                textWin.append("<<" + req + "\n");
                outputStream.write(len.getBytes());
                textWin.append("<<" + len + "\n");
                outputStream.write(packet);
                textWin.append("<<" + "Packet number: " + packetNum + "\n");
                outputStream.write(crc.byteValue());
                textWin.append("<<" + crc + "\n");
                outputStream.write(CR_LF.getBytes());
                outputStream.write(strCrc.getBytes());
                textWin.append("<<" + strCrc + "\n");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        sendPackets();
    }
}
