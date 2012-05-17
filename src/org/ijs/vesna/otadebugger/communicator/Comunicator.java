/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.otadebugger.communicator;

import gnu.io.*;
import java.io.*;
import java.util.Enumeration;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import org.apache.log4j.Logger;
import org.ijs.vesna.sslserver.SslDataListener;

/**
 *
 * @author Matevz
 */
public class Comunicator implements SslDataListener {
    //constants

    private static final String CR_LF = "\r\n";
    private static final String GET = "GET ";
    private static final String POST = "POST ";
    private static final String LEN = "Length=";
    private static final String CRC = "CRC=";
    private static final String RESPONSE_END = "OK";
    private static final int SEMAPHORE = 1;
    private static final int MAX_PORTS = 20;
    private static final Logger logger = Logger.getLogger(Comunicator.class);
    private InputStream inputStream;
    private OutputStream outputStream;
    //private BufferedReader reader = null;
    //private BufferedWriter writer = null;
    private String receivedBuffer = "";
    private final Semaphore semaphore = new Semaphore(SEMAPHORE, true);
    private String[] tempPortList, portList; //list of ports for combobox dropdown
    private CommPort commPort;
    private SerialPort serialPort;
    private String portName = "";

    public String getPortName() {
        return portName;
    }
    private CommPortIdentifier portIdentifier = null;
    private boolean open = false;

    public boolean isOpen() {
        return open;
    }
    private int baudRate = 115200;

    public int getBaudRate() {
        return baudRate;
    }

    public Comunicator() {
    }

    public String sendGet(String params) {
        if (semaphore.tryAcquire()) {
            try {
                String req = GET + params + CR_LF;

                outputStream.write(req.getBytes());

                receivedBuffer = "";
                while (!receivedBuffer.contains(RESPONSE_END) && open) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignore) {
                    }
                }
            } catch (Exception ex) {
                logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBuffer = "##ERROR: Communication in progress\n";
        }
        return receivedBuffer;
    }

    public String sendPost(String uri, String content) {
        if (semaphore.tryAcquire()) {
            try {
                String req = POST + uri + CR_LF
                        + LEN + content.length() + CR_LF
                        + content + CR_LF;

                long crcValue = calculateCrc(req.getBytes());

                req += CRC + crcValue + CR_LF;

                outputStream.write(req.getBytes());

                receivedBuffer = "";
                while (!receivedBuffer.contains(RESPONSE_END) && open) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignore) {
                    }
                }
            } catch (Exception ex) {
                logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBuffer = "##ERROR: Communication in progress\n";
        }
        return receivedBuffer + "\n";
    }

    public String sendBinaryGet(byte[] params) {
        if (semaphore.tryAcquire()) {
            try {
                //writer.write(GET);
                outputStream.write(params, 0, params.length);
                //writer.write(CR_LF);

                receivedBuffer = "";
                while (!receivedBuffer.equals(RESPONSE_END)) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignore) {
                    }
                }
            } catch (Exception ex) {
                logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBuffer = "##ERROR: Communication in progress\n";
        }
        return receivedBuffer;
    }

    public String sendBinaryPost(String uri, byte[] content) {
        if (semaphore.tryAcquire()) {
            try {
                String reqBegin = POST + uri + CR_LF
                        + LEN + content.length + CR_LF;

                Checksum checksum = new CRC32();
                checksum.update(reqBegin.getBytes(), 0, reqBegin.getBytes().length);
                checksum.update(content, 0, content.length);
                checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);

                String reqEnd = CRC + checksum.getValue() + CR_LF;

                outputStream.write(reqBegin.getBytes());
                outputStream.write(content);
                outputStream.write(CR_LF.getBytes());
                outputStream.write(reqEnd.getBytes());

                receivedBuffer = "";
                while (!receivedBuffer.equals(RESPONSE_END)) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignore) {
                    }
                }
            } catch (Exception ex) {
                logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBuffer = "##ERROR: Communication in progress\n";
        }
        return receivedBuffer;
    }

    public String sendRecoveryRequest() {
        if (semaphore.tryAcquire()) {
            try {
                String req = CR_LF;

                for (int i = 0; i < 5; i++) {
                    //writer.write(req);
                }

                receivedBuffer = "";
                while (!receivedBuffer.equals(RESPONSE_END)) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignore) {
                    }
                }
            } catch (Exception ex) {
                logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBuffer = "##ERROR: Communication in progress\n";
        }
        return receivedBuffer;
    }

    public String setBaudRate(String userBaudRate) {
        String returnMsg = "";
        try {
            int tmpBaud = Integer.valueOf(userBaudRate).intValue();
            baudRate = tmpBaud;
            if (open) {
                returnMsg = "##Baud rate set to " + baudRate + ".\n"
                        + "##Please close and reopen serial port.\n";
            } else {
                returnMsg = "##Baud rate set to " + baudRate + ".\n";
            }
        } catch (Exception ex) {
            logger.error(ex);
        }
        return returnMsg;
    }

    //open serial port
    private String connect(String portName) throws Exception {
        //make sure port is not currently in use
        String msg = "";
        portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if (portIdentifier.isCurrentlyOwned()) {
            msg = "##Error: Port is currently in use\n";
        } else {
            //create CommPort and identify available serial/parallel ports
            commPort = portIdentifier.open(this.getClass().getName(), 2000);
            serialPort = (SerialPort) commPort;//cast all to serial
            //set baudrate, 8N1 stopbits, no parity
            serialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            //start I/O streams
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            msg = "##Serial port: " + portName + " is now opened.\n";
            open = true;
        }
        return msg;
    }

    public String[] getPorts() {
        Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();
        tempPortList = new String[MAX_PORTS]; //create array of 20 ports
        int numports = 0;
        int i;
        tempPortList[0] = "Select Port";
        //fill up a temporary list of length MAX_PORTS with the portnames
        while (portEnum.hasMoreElements()) {
            portIdentifier = (CommPortIdentifier) portEnum.nextElement();
            numports++;
            tempPortList[numports] = portIdentifier.getName();
        }
        //make the actual port list only as long as necessary
        portList = new String[numports];
        for (i = 0; i < numports; i++) {
            portList[i] = tempPortList[i];
        }
        return portList;
    }

    public String setPortName(String portName) {
        String response = "";
        if (open) { //if port open, make user close port before changing port
            response = "##Must Close Port Before Changing Port.\n";
        } else if (portName.equals("Select Port")) {
            response = "##Must Select Valid Port.\n";
        } else {
            this.portName = portName;
            response = "##Port Selected: " + portName + ", Baud Rate: " + baudRate + ".\n";
        }
        return response;
    }

    public String openSerialConnection() {
        String response = "";
        if (portIdentifier.isCurrentlyOwned()) {
            //close input stream
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            //close output stream
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            //close serial port                   
            if (serialPort != null) {
                serialPort.close();
            }
            open = false;
        } else {//else port is closed, so open it
            try {
                if (!portName.equals("")) {
                    response = connect(portName);
                    (new Thread(new SerialReader(inputStream))).start();
                    response = "##Opening Port: " + portName + ", Baud Rate: " + baudRate + ".\n";
                } else {
                    response = "##Must Select Valid Port.\n";
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return response;
    }

    public String closeSerialConnection() {
        //when user closes, make sure to close open ports and open I/O streams
        String response = "";
        if (portIdentifier.isCurrentlyOwned()) { //if port open, close port
            open = false;
            if (inputStream != null) //close input stream
            {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (outputStream != null) //close output stream
            {
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (serialPort != null) {
                serialPort.close();
            }
            response = "##Serial Port: " + portName + " is now closed.\n";
            portName = "";
            baudRate = 115200;            
        }
        return response;
    }

    private long calculateCrc(byte[] b) {
        Checksum checksum = new CRC32();
        checksum.update(b, 0, b.length);
        return checksum.getValue();
    }

    public class SerialReader implements Runnable {

        InputStream in;

        public SerialReader(InputStream in) {
            this.in = in;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int len = -1;
            try {
                while ((len = this.in.read(buffer)) > -1 && open) {
                    receivedBuffer += new String(buffer, 0, len);
                }
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void notifySslDataListener(String str) {
        receivedBuffer += str;
    }
}
