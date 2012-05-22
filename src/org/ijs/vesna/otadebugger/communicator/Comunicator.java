/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.otadebugger.communicator;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import javax.net.ssl.SSLServerSocketFactory;
import org.apache.log4j.Logger;

/**
 *
 * @author Matevz
 */
public class Comunicator {
    //constants

    private static final String nl = "\n";
    private static final String CR_LF = "\r\n";
    private static final String GET = "GET ";
    private static final String POST = "POST ";
    private static final String LEN = "Length=";
    private static final String CRC = "CRC=";
    private static final String RESPONSE_END = "\r\nOK\r\n";
    private static final String JUNK_INPUT = "\r\nJUNK-INPUT\r\n";
    private static final String CORRUPTED_DATA = "\r\nCORRUPTED-DATA\r\n";
    private static final String ERROR = "\r\nERROR\r\n";
    private static final int SEMAPHORE = 1;
    private static final int MAX_PORTS = 20;
    private static final int MAX_RETRANSMISSIONS = 10;
    private static final String CLOSING_STRING = "closesslsocket\r\n";
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

    public Comunicator() {
    }

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

    public String sendGet(byte[] params) {
        String errorReport = "";
        if (semaphore.tryAcquire()) {
            try {
                for (int i = 0; i < MAX_RETRANSMISSIONS; i++) {
                    outputStream.write(GET.getBytes());
                    outputStream.write(params);
                    outputStream.write(CR_LF.getBytes());

                    receivedBuffer = "";
                    while (!receivedBuffer.contains(RESPONSE_END) && open) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ignore) {
                        }
                    }
                    receivedBuffer = receivedBuffer.replace(RESPONSE_END, "");
                    if (receivedBuffer.contains(JUNK_INPUT)
                            || receivedBuffer.contains(CORRUPTED_DATA)) {
                        receivedBuffer = receivedBuffer.replace(JUNK_INPUT, "");
                        receivedBuffer = receivedBuffer.replace(CORRUPTED_DATA, "");
                        String junkReport = receivedBuffer;
                        sendRecoveryRequest();
                        if (i == MAX_RETRANSMISSIONS - 1) {
                            receivedBuffer = junkReport
                                    + nl + "fatal error request did not go through, retransmissions: " + (i + 1) + nl;
                        } else {
                            receivedBuffer = junkReport
                                    + nl + "send with retransmissions: " + (i + 1) + nl;
                        }
                    } else if (receivedBuffer.contains(ERROR)) {
                        receivedBuffer = receivedBuffer.replace(ERROR, "");
                        errorReport = receivedBuffer
                                + nl + "send with retransmissions: " + (i + 1) + nl;
                    } else {
                        return receivedBuffer + nl;
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
        return receivedBuffer + errorReport + "\n";
    }

    public String sendPost(String uri, byte[] content) {
        String errorReport = "";
        if (semaphore.tryAcquire()) {
            try {
                for (int i = 0; i < MAX_RETRANSMISSIONS; i++) {
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
                    while (!receivedBuffer.contains(RESPONSE_END) && open) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ignore) {
                        }
                    }
                    receivedBuffer = receivedBuffer.replace(RESPONSE_END, "");
                    if (receivedBuffer.contains(JUNK_INPUT)
                            || receivedBuffer.contains(CORRUPTED_DATA)) {
                        receivedBuffer = receivedBuffer.replace(JUNK_INPUT, "");
                        receivedBuffer = receivedBuffer.replace(CORRUPTED_DATA, "");
                        String junkReport = receivedBuffer;
                        sendRecoveryRequest();
                        if (i == MAX_RETRANSMISSIONS - 1) {
                            receivedBuffer = junkReport
                                    + nl + "fatal error request did not go through, retransmissions: " + (i + 1) + nl;
                        } else {
                            receivedBuffer = junkReport
                                    + nl + "send with retransmissions: " + (i + 1) + nl;
                        }
                    } else if (receivedBuffer.contains(ERROR)) {
                        receivedBuffer = receivedBuffer.replace(ERROR, "");
                        errorReport = receivedBuffer
                                + nl + "send with retransmissions: " + (i + 1) + nl;
                    } else {
                        return receivedBuffer + nl;
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
        return receivedBuffer + errorReport + "\n";
    }

    private void sendRecoveryRequest() {
        try {
            String req = CR_LF;

            for (int i = 0; i < 5; i++) {
                outputStream.write(req.getBytes());
            }

            receivedBuffer = "";
            while (!receivedBuffer.contains(RESPONSE_END) && open) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignore) {
                }
            }
        } catch (Exception ex) {
            logger.error(ex);
        }
    }

    public String sslConnect(int port) {
        try {
            SslServer sslServer = new SslServer(port);
            (new Thread(sslServer)).start();
            return "SSL server listening on port " + port + nl;
        } catch (Exception ex) {
            return "SSL server setup failed\n";
        }
    }

    public String sslDisconnect() {
        try {
            outputStream.write(CLOSING_STRING.getBytes());
            return "SSL server closed\n";
        } catch (Exception ex) {
            return "Closing SSL server failed\n";
        }
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
    private String serialConnect(String portName) throws Exception {
        //make sure port is not currently in use
        String msg;
        portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if (portIdentifier.isCurrentlyOwned()) {
            msg = "##Error: Port is currently in use\n";
        } else {
            //create CommPort and identify available serial/parallel ports
            commPort = portIdentifier.open(this.getClass().getName(), 2000);
            serialPort = (SerialPort) commPort;//cast all to serial
            //set baudrate, 8N1 stopbits, no parity
            serialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
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
        String response;
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
                    logger.error(ex);
                }
            }
            //close output stream
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    logger.error(ex);
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
                    response = serialConnect(portName);
                    (new Thread(new SerialReader(inputStream))).start();
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
                    logger.error(ex);
                }
            }
            if (outputStream != null) //close output stream
            {
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    logger.error(ex);
                }
            }
            if (serialPort != null) {
                serialPort.close();
            }
            response = "##Serial Port: " + portName + " is now closed.\n";
        }
        return response;
    }

    public String endSerialConnection() {
        //when user closes, make sure to close open ports and open I/O streams
        String response = "";
        if (portIdentifier != null && portIdentifier.isCurrentlyOwned()) { //if port open, close port
            open = false;
            if (inputStream != null) //close input stream
            {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    logger.error(ex);
                }
            }
            if (outputStream != null) //close output stream
            {
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    logger.error(ex);
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

    public class SerialReader implements Runnable {

        InputStream in;

        public SerialReader(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int len;
            try {
                while ((len = this.in.read(buffer)) > -1 && open) {
                    receivedBuffer += new String(buffer, 0, len);
                }
                in.close();
            } catch (IOException e) {
                logger.error(e);
            }
        }
    }

    public class SslServer implements Runnable {

        //private InputStream inputStream;
        //private OutputStream outputStream;
        private int port;
        private boolean runServer = true;

        public SslServer(int port) {
            this.port = port;
        }

        @Override
        public void run() {
            try {
                System.setProperty("javax.net.ssl.keyStore", "mySrvKeystore");
                System.setProperty("javax.net.ssl.keyStorePassword", "123456");
                SSLServerSocketFactory ssf = null;
                ServerSocket ss = null;
                try {
                    ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                    ss = ssf.createServerSocket(port);
                    logger.debug("The SSL Server is listening on port " + ss.getLocalPort());

                    while (runServer) {
                        try {
                            Socket s = null;
                            try {
                                logger.debug("Waiting for client connection.");
                                s = ss.accept();
                                logger.debug("Client with IP "
                                        + s.getInetAddress().getHostAddress()
                                        + " successfully connected to the server.");

                                inputStream = s.getInputStream();

                                outputStream = s.getOutputStream();

                                open = true;

                                byte[] buffer = new byte[1024];
                                int len;
                                try {
                                    while ((len = inputStream.read(buffer)) > -1 && open) {
                                        String recBuff = new String(buffer, 0, len);
                                        if (recBuff.contains(CLOSING_STRING)) {
                                            runServer = false;
                                            break;
                                        }
                                        receivedBuffer += new String(buffer, 0, len) + "\r\nOK\r\n";
                                    }
                                } catch (IOException e) {
                                    logger.error(e);
                                }
                                logger.debug("The SSL Socket is now closed.");
                            } catch (Exception ex) {
                                logger.error("The SSL Socket was closed unexpectedly.");
                                logger.error(ex);
                            } finally {
                                open = false;
                                inputStream.close();
                                inputStream.close();
                                s.close();
                            }
                        } catch (Exception ex) {
                            logger.error(ex);
                        }
                    }
                    logger.debug("The SSL Server shutted down.");
                } catch (Exception ex) {
                    logger.error(ex);
                } finally {
                    ss.close();
                }
            } catch (Exception ex) {
                logger.error(ex);
            }
        }
    }
}
