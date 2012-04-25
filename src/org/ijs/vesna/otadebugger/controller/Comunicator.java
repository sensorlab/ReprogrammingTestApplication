/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.otadebugger.controller;

import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import java.io.*;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import org.ijs.vesna.otadebugger.view.OtaDebuggerGui;
import org.ijs.vesna.sslserver.SslDataListener;

/**
 *
 * @author Matevz
 */
public class Comunicator implements SerialPortEventListener, SslDataListener {
    //constants
    private static final String CR_LF = "\r\n";
    private static final String GET = "GET ";
    private static final String POST = "POST ";
    private static final String LEN = "Length=";
    private static final String CRC = "Crc=";
    private static final String RESPONSE_END = "OK";
    private static final int SEMAPHORE = 1;
    
    //private static final Logger logger = Logger.getLogger(Comunicator.class);
    private InputStream inputStream;
    private OutputStream outputStream;
    private BufferedReader reader = null;
    private BufferedWriter writer = null;
    private String receivedBuffer = "";
    private final Semaphore semaphore = new Semaphore(SEMAPHORE, true);    

    public Comunicator(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;

        reader = new BufferedReader(new InputStreamReader(inputStream));
        writer = new BufferedWriter(new OutputStreamWriter(outputStream));
    }

    public String sendGet(String params) {
        if (semaphore.isFair()) {
            try {
                semaphore.acquire();
                String req = GET + params + CR_LF;

                writer.write(req);

                receivedBuffer = "";
                while (!receivedBuffer.equals(RESPONSE_END)) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignore) {
                    }
                }
            } catch (Exception ex) {
                //logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBuffer = "ERROR: Communication in progress";
        }
        return receivedBuffer;
    }

    public String sendPost(String uri, String content) {
        if (semaphore.isFair()) {
            try {
                semaphore.acquire();
                String req = POST + uri + CR_LF
                        + LEN + content.length() + CR_LF
                        + content + CR_LF;

                long crcValue = calculateCrc(req.getBytes());

                req += CRC + crcValue + CR_LF;

                writer.write(req);

                receivedBuffer = "";
                while (!receivedBuffer.equals(RESPONSE_END)) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignore) {
                    }
                }
            } catch (Exception ex) {
                //logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBuffer = "ERROR: Communication in progress";
        }
        return receivedBuffer;
    }

    public String sendBinaryGet(byte[] params) {
        if (semaphore.isFair()) {
            try {
                semaphore.acquire();

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
                //logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBuffer = "ERROR: Communication in progress";
        }
        return receivedBuffer;
    }

    public String sendBinaryPost(String uri, byte[] content) {
        if (semaphore.isFair()) {
            try {
                semaphore.acquire();
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
                //logger.error(ex);
            } finally {
                semaphore.release();
            }
        } else {
            receivedBuffer = "ERROR: Communication in progress";
        }
        return receivedBuffer;
    }

    private long calculateCrc(byte[] b) {
        Checksum checksum = new CRC32();
        checksum.update(b, 0, b.length);
        return checksum.getValue();
    }
    
    @Override
    public void serialEvent(SerialPortEvent spe) {
        try {   //read the input stream and store to buffer, count number of bytes read
            int available = inputStream.available();
            byte chunk[] = new byte[available];
            inputStream.read(chunk, 0, available);
            receivedBuffer += new String(chunk);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void notifySslDataListener(String str) {
        receivedBuffer += str;
    }   
}
