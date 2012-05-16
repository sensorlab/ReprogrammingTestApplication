/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.otadebugger.communicator;

import gnu.io.*;
import java.io.*;
import java.util.Enumeration;
import java.util.TooManyListenersException;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import javax.swing.SwingWorker;
import org.apache.log4j.Logger;
import org.ijs.vesna.otadebugger.gui.OtaDebuggerGui;
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
    private static final String CRC = "Crc=";
    private static final String RESPONSE_END = "OK";
    private static final int SEMAPHORE = 1;
    private static final int MAX_PORTS = 20;
    private static final Logger logger = Logger.getLogger(Comunicator.class);
    private InputStream inputStream;
    private OutputStream outputStream;
    private BufferedReader reader = null;
    private BufferedWriter writer = null;
    private String receivedBuffer = "";
    private final Semaphore semaphore = new Semaphore(SEMAPHORE, true);
    private String[] tempPortList, portList; //list of ports for combobox dropdown
    private CommPort commPort;
    private SerialPort serialPort;
    private String portName;
    
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
                while (!receivedBuffer.contains(RESPONSE_END) || open) {
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
            receivedBuffer = "ERROR: Communication in progress\n";
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
                while (!receivedBuffer.contains(RESPONSE_END) || open) {
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
            receivedBuffer = "ERROR: Communication in progress\n";
        }
        return receivedBuffer + "\n";
    }
    
    public String sendBinaryGet(byte[] params) {
        if (semaphore.tryAcquire()) {
            try {
                writer.write(GET);
                outputStream.write(params, 0, params.length);
                writer.write(CR_LF);
                
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
            receivedBuffer = "ERROR: Communication in progress\n";
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
            receivedBuffer = "ERROR: Communication in progress\n";
        }
        return receivedBuffer;
    }
    
    public String sendRecoveryRequest() {
        if (semaphore.tryAcquire()) {
            try {
                String req = CR_LF;
                
                for (int i = 0; i < 5; i++) {
                    writer.write(req);
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
            receivedBuffer = "ERROR: Communication in progress\n";
        }
        return receivedBuffer;
    }
    
    public String setBaudRate(String userBaudRate) {
        String returnMsg;
        //only change baud when port is closed
        boolean reopen = false;
        if (portIdentifier.isCurrentlyOwned()) {//if port open, prompt user to close
            returnMsg = "##Close Port " + portName + ".\n";
            reopen = true;
            //JOptionPane.showMessageDialog(this, "Must Close Port First.", "Error", JOptionPane.ERROR_MESSAGE);
        }
        String newbaud = userBaudRate;//get text from user
        //do simple check to make sure baudrate is valid
        if (newbaud.equals("")) {
            returnMsg = "##Must Enter Valid Baud Rate.\n";
            //JOptionPane.showMessageDialog(this, "Must Enter Valid Baud Rate.");
        } else {//convert string to int. when user re-opens port, it will be new baudrate
            baudRate = Integer.valueOf(newbaud).intValue();
            returnMsg = "##Baud rate changed to " + baudRate + ".\n";
            if (reopen == true) {
                try {
                    //connect(portName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                returnMsg = "##Opening Port: " + portName + ", Baud Rate: " + baudRate + ".\n";
            }
        }
        return returnMsg;
    }

    //open serial port
    private void connect(String portName) throws Exception {
        //make sure port is not currently in use
        portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if (portIdentifier.isCurrentlyOwned()) {
            //outputText("##Error: Port is currently in use");
        } else {
            //create CommPort and identify available serial/parallel ports
            commPort = portIdentifier.open(this.getClass().getName(), 2000);
            serialPort = (SerialPort) commPort;//cast all to serial
            //set baudrate, 8N1 stopbits, no parity
            serialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            //start I/O streams
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            open = true;
        }
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
    
    public void setPortName(String portName) {
        this.portName = portName;
    }
    
    public boolean openSerialConnection() {
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
            System.out.println("closing serial port.");
            serialPort.removeEventListener();
            if (serialPort != null) {
                serialPort.close();
            }
            System.out.println("closed serial port.");
            
            open = false;
        } else {//else port is closed, so open it
            try {
                connect(portName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //try {
            //serialPort.addEventListener(this);
            //} catch (TooManyListenersException ex) {
            //ex.printStackTrace();
            //}
            serialPort.notifyOnDataAvailable(true);
        }
        reader = new BufferedReader(new InputStreamReader(inputStream));
        writer = new BufferedWriter(new OutputStreamWriter(outputStream));
        (new Thread(new SerialReader(inputStream))).start();
        System.out.println("end of toggle function");
        return open;
    }
    
    public boolean closeSerialConnection() {
        //when user closes, make sure to close open ports and open I/O streams       
        if (portIdentifier.isCurrentlyOwned()) { //if port open, close port            
            if (inputStream != null) //close input stream
            {
                try {
                    inputStream.close();
                    reader.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (outputStream != null) //close output stream
            {
                try {
                    outputStream.close();
                    writer.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            serialPort.removeEventListener();
            if (serialPort != null) {
                serialPort.close();
            }
            open = false;
        }
        return open;
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
                while ((len = this.in.read(buffer)) > -1 || open) {
                    receivedBuffer += new String(buffer, 0, len);
                }
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
