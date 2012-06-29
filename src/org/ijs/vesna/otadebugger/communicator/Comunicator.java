 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.otadebugger.communicator;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
    private static final String NODES = "NODES";
    private static final String NODES_JUNK = "junk";
    private static final int SEMAPHORE = 1;
    private static final int MAX_PORTS = 20;
    private static final int MAX_RETRANSMISSIONS = 1;
    private static final int TIMEOUT = 60000; // transmission timeout in ms
    private static final int MASTER_TIMEOUT = 120000; // transmission timeout in ms
    private static final Logger logger = Logger.getLogger(Comunicator.class);
    private static final int MEGABYTE = 1024 * 1024;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String receivedBufferStr = "";
    private ByteBuffer receivedBuffer = ByteBuffer.allocate(MEGABYTE);
    private final Semaphore semaphore = new Semaphore(SEMAPHORE, true);
    private String[] tempPortList, portList; //list of ports for combobox dropdown
    private CommPort commPort;
    private SerialPort serialPort;
    private String portName = "";
    private CommPortIdentifier portIdentifier = null;
    private boolean open = false;
    private boolean sslServerRunning = false;
    private SslServer sslServer = null;

    public Comunicator() {
        try {
            byte[] bytes = new byte[MEGABYTE];
            receivedBuffer = ByteBuffer.allocate(MEGABYTE);
        } catch (Exception ex) {
            logger.error(ex);
        }
    }

    public void appendToTxtLog(String text) {

        String fileName = System.getProperty("user.dir") + System.getProperty("file.separator")
                + "logs" + System.getProperty("file.separator") + "request-response-txt.log";

        File file = new File(fileName);

        // Does the file already exist
        if (!file.exists()) {
            try {
                // Try creating the file
                file.createNewFile();
            } catch (IOException ioe) {
                // ignore
            }
        }

        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new FileWriter(fileName, true));
            bw.write(text);
            bw.newLine();
            bw.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException ioe2) {
                    // just ignore it
                }
            }
        }
    }

    public void appendToHexLog(String text) {

        String fileName = System.getProperty("user.dir") + System.getProperty("file.separator")
                + "logs" + System.getProperty("file.separator") + "request-response-hex.log";

        File file = new File(fileName);

        // Does the file already exist
        if (!file.exists()) {
            try {
                // Try creating the file
                file.createNewFile();
            } catch (IOException ioe) {
                // ignore
            }
        }

        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new FileWriter(fileName, true));
            bw.write(text);
            bw.newLine();
            bw.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException ioe2) {
                    // just ignore it
                }
            }
        }
    }

    private String getCurrentTime() {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        String timeString = df.format(new Date());
        return timeString + ": ";
    }

    public boolean isSslServerRunning() {
        return sslServerRunning;
    }

    public String getPortName() {
        return portName;
    }

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
                    appendToTxtLog(getCurrentTime() + "Request:\n" + GET + new String(params) + CR_LF);
                    appendToHexLog(getCurrentTime() + "Request:\n" + GET + new String(params) + CR_LF);

                    String tmpBuff = "tmp";
                    receivedBuffer.clear();
                    receivedBufferStr = ""; //TODO make it local

                    outputStream.write(GET.getBytes());
                    outputStream.write(params);
                    outputStream.write(CR_LF.getBytes());

                    long startMaster = System.currentTimeMillis();
                    long start = System.currentTimeMillis();
                    while (!receivedBufferStr.contains(RESPONSE_END) && open) {
                        if (!receivedBufferStr.equals(tmpBuff)) {
                            start = System.currentTimeMillis();
                        }
                        long stop = System.currentTimeMillis();
                        if ((stop - start) > TIMEOUT || (stop - startMaster) > MASTER_TIMEOUT) {
                            appendToTxtLog(getCurrentTime() + "Response:\n" + receivedBufferStr + nl + "Fatal Error: Response Timeout" + nl);
                            receivedBuffer.flip();
                            String hexStr = "";
                            for (int j = 0; j < receivedBuffer.limit(); j++) {
                                hexStr += Integer.toHexString(receivedBuffer.get(j)) + " ";
                            }
                            appendToHexLog(getCurrentTime() + "Response:\n" + hexStr + nl + "Fatal Error: Response Timeout" + nl);
                            return receivedBufferStr + nl + "Fatal Error: Response Timeout" + nl;
                        }
                        tmpBuff = receivedBufferStr;
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ignore) {
                        }
                    }
                    if (receivedBufferStr.contains(JUNK_INPUT)
                            || receivedBufferStr.contains(CORRUPTED_DATA)) {
                        receivedBufferStr = receivedBufferStr.replace(JUNK_INPUT, "");
                        receivedBufferStr = receivedBufferStr.replace(CORRUPTED_DATA, "");
                        String junkReport = receivedBufferStr;
                        sendRecoveryRequest();
                        if (i == MAX_RETRANSMISSIONS - 1) {
                            receivedBufferStr = junkReport
                                    + nl + "fatal error request did not go through, retransmissions: " + (i + 1) + nl;
                        } else {
                            receivedBufferStr = junkReport
                                    + nl + "send with retransmissions: " + (i + 1) + nl;
                        }
                    } else if (receivedBufferStr.contains(ERROR)) {
                        if (receivedBufferStr.contains(NODES) && receivedBufferStr.contains(NODES_JUNK)) {
                            //TODO send parser recoveri to the node
                            String nodeRequest = new String(params);
                            String xbitAddr = nodeRequest.substring(nodeRequest.indexOf('?') + 1, nodeRequest.indexOf('/'));
                            sendNodeRecoveryRequest(xbitAddr);
                        }
                        receivedBufferStr = receivedBufferStr.replace(ERROR, "");
                        errorReport = receivedBufferStr
                                + nl + "send with retransmissions: " + (i + 1) + nl;
                    } else {
                        appendToTxtLog(getCurrentTime() + "Response:\n" + receivedBufferStr + nl);
                        receivedBuffer.flip();
                        String hexStr = "";
                        for (int j = 0; j < receivedBuffer.limit(); j++) {
                            hexStr += Integer.toHexString(receivedBuffer.get(j)) + " ";
                        }
                        appendToHexLog(getCurrentTime() + "Response:\n" + hexStr + nl);
                        receivedBufferStr = receivedBufferStr.replace(RESPONSE_END, "");
                        return receivedBufferStr + nl;
                    }
                }
            } catch (Exception ex) {
                logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBufferStr = "ERROR: Communication in progress\n";
        }
        appendToTxtLog(getCurrentTime() + "Response:\n" + receivedBufferStr + nl + errorReport + nl);
        receivedBuffer.flip();
        String hexStr = "";
        for (int j = 0; j < receivedBuffer.limit(); j++) {
            hexStr += Integer.toHexString(receivedBuffer.get(j)) + " ";
        }
        appendToHexLog(getCurrentTime() + "Response:\n" + hexStr + nl + errorReport + nl);
        receivedBufferStr = receivedBufferStr.replace(RESPONSE_END, "");
        return receivedBufferStr + nl + errorReport + nl;
    }

    public String sendPost(byte[] params, byte[] content) {
        String errorReport = "";
        if (semaphore.tryAcquire()) {
            try {
                for (int i = 0; i < MAX_RETRANSMISSIONS; i++) {
                    String len = LEN + content.length + CR_LF;

                    Checksum checksum = new CRC32();
                    checksum.update(POST.getBytes(), 0, POST.getBytes().length);
                    checksum.update(params, 0, params.length);
                    checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);
                    checksum.update(len.getBytes(), 0, len.getBytes().length);
                    checksum.update(content, 0, content.length);
                    checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);

                    String reqEnd = CRC + checksum.getValue() + CR_LF;

                    appendToTxtLog(getCurrentTime() + "Request:\n" + POST + new String(params) + CR_LF + len + new String(content) + CR_LF + reqEnd);
                    appendToHexLog(getCurrentTime() + "Request:\n" + POST + new String(params) + CR_LF + len + new String(content) + CR_LF + reqEnd);

                    String tmpBuff = "tmp";
                    receivedBuffer.clear();
                    receivedBufferStr = "";

                    outputStream.write(POST.getBytes());
                    outputStream.write(params);
                    outputStream.write(CR_LF.getBytes());
                    outputStream.write(len.getBytes());
                    outputStream.write(content);
                    outputStream.write(CR_LF.getBytes());
                    outputStream.write(reqEnd.getBytes());

                    long startMaster = System.currentTimeMillis();
                    long start = System.currentTimeMillis();
                    while (!receivedBufferStr.contains(RESPONSE_END) && open) {
                        if (!receivedBufferStr.equals(tmpBuff)) {
                            start = System.currentTimeMillis();
                        }
                        long stop = System.currentTimeMillis();
                        if ((stop - start) > TIMEOUT || (stop - startMaster) > MASTER_TIMEOUT) {
                            appendToTxtLog(getCurrentTime() + "Response:\n" + receivedBufferStr + nl + "Fatal Error: Response Timeout" + nl);
                            receivedBuffer.flip();
                            String hexStr = "";
                            for (int j = 0; j < receivedBuffer.limit(); j++) {
                                hexStr += Integer.toHexString(receivedBuffer.get(j)) + " ";
                            }
                            appendToHexLog(getCurrentTime() + "Response:\n" + hexStr + nl + "Fatal Error: Response Timeout" + nl);
                            return receivedBufferStr + nl + "Fatal Error: Response Timeout" + nl + nl;
                        }
                        tmpBuff = receivedBufferStr;
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ignore) {
                        }
                    }
                    if (receivedBufferStr.contains(JUNK_INPUT)
                            || receivedBufferStr.contains(CORRUPTED_DATA)) {
                        receivedBufferStr = receivedBufferStr.replace(JUNK_INPUT, "");
                        receivedBufferStr = receivedBufferStr.replace(CORRUPTED_DATA, "");
                        String junkReport = receivedBufferStr;
                        sendRecoveryRequest();
                        if (i == MAX_RETRANSMISSIONS - 1) {
                            receivedBufferStr = junkReport
                                    + nl + "fatal error request did not go through, retransmissions: " + (i + 1) + nl;
                        } else {
                            receivedBufferStr = junkReport
                                    + nl + "send with retransmissions: " + (i + 1) + nl;
                        }
                    } else if (receivedBufferStr.contains(ERROR)) {
                        if (receivedBufferStr.contains(NODES) && receivedBufferStr.contains(NODES_JUNK)) {
                            String nodeRequest = new String(params);
                            String xbitAddr = nodeRequest.substring(nodeRequest.indexOf('?') + 1, nodeRequest.indexOf('/'));
                            sendNodeRecoveryRequest(xbitAddr);
                        }
                        receivedBufferStr = receivedBufferStr.replace(ERROR, "");
                        errorReport = receivedBufferStr
                                + nl + "send with retransmissions: " + (i + 1) + nl;
                    } else {
                        appendToTxtLog(getCurrentTime() + "Response:\n" + receivedBufferStr + nl);
                        receivedBuffer.flip();
                        String hexStr = "";
                        for (int j = 0; j < receivedBuffer.limit(); j++) {
                            hexStr += Integer.toHexString(receivedBuffer.get(j)) + " ";
                        }
                        appendToHexLog(getCurrentTime() + "Response:\n" + hexStr + nl);
                        receivedBufferStr = receivedBufferStr.replace(RESPONSE_END, "");
                        return receivedBufferStr + nl;
                    }
                }
            } catch (Exception ex) {
                logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBufferStr = "ERROR: Communication in progress\n";
        }
        receivedBuffer.flip();
        String hexStr = "";
        for (int j = 0; j < receivedBuffer.limit(); j++) {
            hexStr += Integer.toHexString(receivedBuffer.get(j)) + " ";
        }
        appendToHexLog(getCurrentTime() + "Response:\n" + hexStr + nl + errorReport + nl);
        receivedBufferStr = receivedBufferStr.replace(RESPONSE_END, "");
        return receivedBufferStr + errorReport + "\n";
    }

    private void sendRecoveryRequest() {
        try {
            String req = CR_LF;

            for (int i = 0; i < 5; i++) {
                outputStream.write(req.getBytes());
            }

            receivedBufferStr = "";
            while (!receivedBufferStr.contains(RESPONSE_END) && open) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignore) {
                }
            }
        } catch (Exception ex) {
            logger.error(ex);
        }
    }

    private void sendNodeRecoveryRequest(String xbitAddr) {
        try {
            String recoveryResource = "radio/noderesetparser?" + xbitAddr;
            byte[] params = recoveryResource.getBytes();
            byte[] content = "1".getBytes();
            String len = LEN + content.length + CR_LF;

            Checksum checksum = new CRC32();
            checksum.update(POST.getBytes(), 0, POST.getBytes().length);
            checksum.update(params, 0, params.length);
            checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);
            checksum.update(len.getBytes(), 0, len.getBytes().length);
            checksum.update(content, 0, content.length);
            checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);

            String reqEnd = CRC + checksum.getValue() + CR_LF;

            outputStream.write(POST.getBytes());
            outputStream.write(params);
            outputStream.write(CR_LF.getBytes());
            outputStream.write(len.getBytes());
            outputStream.write(content);
            outputStream.write(CR_LF.getBytes());
            outputStream.write(reqEnd.getBytes());

            receivedBufferStr = "";
            while (!receivedBufferStr.contains(RESPONSE_END) && open) {
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
            sslServer = new SslServer(port);
            (new Thread(sslServer)).start();
            return "\nSSL server listening on port " + port + nl;
        } catch (Exception ex) {
            return "\nSSL server setup failed" + nl;
        }
    }

    public String sslDisconnect() {
        try {
            sslServer.forceClose();
            return "\nSSL socket closed" + nl;
        } catch (Exception ex) {
            return "\nClosing SSL socket failed" + nl;
        }
    }

    public String sslConnectionRestart() {
        try {
            sslServer.forceRestart();
            return "\nSSL socket reseted" + nl;
        } catch (Exception ex) {
            return "\nRestarting SSL socket failed" + nl;
        }
    }

    public String setBaudRate(String userBaudRate) {
        String returnMsg = "";
        try {
            int tmpBaud = Integer.valueOf(userBaudRate).intValue();
            baudRate = tmpBaud;
            if (open) {
                returnMsg = "Baud rate set to " + baudRate + ".\n"
                        + "Please close and reopen serial port.\n";
            } else {
                returnMsg = "Baud rate set to " + baudRate + ".\n";
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
            msg = "Error: Port is currently in use\n";
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
            msg = "Serial port: " + portName + " is now opened.\n";
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
            response = "Must Close Port Before Changing Port.\n";
        } else if (portName.equals("Select Port")) {
            response = "Must Select Valid Port.\n";
        } else {
            this.portName = portName;
            response = "Port Selected: " + portName + ", Baud Rate: " + baudRate + ".\n";
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
                    response = "Must Select Valid Port.\n";
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
            response = "Serial Port: " + portName + " is now closed.\n";
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
            response = "Serial Port: " + portName + " is now closed.\n";
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
            Integer lockInt = new Integer(0);
            int len;
            int avail;
            try {
                while (open) {
                    /*
                     * we do not want to block, in order to be able to close the
                     * serial line
                     */
                    avail = this.in.available();
                    if (avail > 0) {
                        len = this.in.read(buffer, 0, avail);
                        receivedBufferStr += new String(buffer, 0, len);
                        receivedBuffer.put(buffer, 0, len);
                    }
                    // wait a little
                    synchronized (lockInt) {
                        try {
                            lockInt.wait(10);
                        } catch (InterruptedException e) {
                            // whatever...
                        }
                    }
                }
                in.close();
            } catch (IOException e) {
                logger.error(e);
            }
        }
    }

    public class SslServer implements Runnable {

        private int port;
        private boolean runServer = true;
        SSLServerSocketFactory ssf = null;
        ServerSocket ss = null;
        Socket s = null;

        public SslServer(int port) {
            this.port = port;
        }

        public void forceRestart() {
            try {
                open = false;

                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (s != null) {
                    s.close();
                }
            } catch (IOException e) {
                logger.error(e);
            }
        }

        public void forceClose() {
            try {
                open = false;
                sslServerRunning = false;
                runServer = false;
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (s != null) {
                    s.close();
                }
                if (ss != null) {
                    ss.close();
                }
            } catch (IOException e) {
                logger.error(e);
            }
        }

        @Override
        public void run() {
            try {
                System.setProperty("javax.net.ssl.keyStore", "mySrvKeystore");
                System.setProperty("javax.net.ssl.keyStorePassword", "123456");

                try {
                    ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                    ss = ssf.createServerSocket(port);
                    logger.debug("The SSL Server is listening on port " + ss.getLocalPort());

                    while (runServer) {
                        try {
                            sslServerRunning = true;
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
                                while (runServer) {
                                    len = inputStream.read(buffer);
                                    receivedBufferStr += new String(buffer, 0, len);
                                    receivedBuffer.put(buffer, 0, len);
                                }
                            } catch (IOException e) {
                                logger.debug("The SSL connection interrupted.");
                            }
                            logger.debug("The SSL Socket is now closed.");
                        } catch (Exception ex) {
                            logger.debug("The SSL socket was closed unexpectedly or was forced to close.");
                        } finally {
                            open = false;
                            sslServerRunning = false;
                            if (inputStream != null) {
                                inputStream.close();
                            }
                            if (outputStream != null) {
                                outputStream.close();
                            }
                            if (s != null) {
                                s.close();
                            }
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
