/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.debugger;

import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import org.apache.log4j.Logger;

/**
 *
 * @author Matevz
 */
public class Comunicator implements Runnable {
    
    //private static final Logger logger = Logger.getLogger(Comunicator.class);
    private InputStream inputStream;
    private OutputStream outputStream;
    private BufferedReader reader = null;
    private BufferedWriter writer = null;
    //constants
    private static final String CR_LF = "\r\n";
    private static final String GET = "GET ";
    private static final String POST = "POST ";
    private static final String LEN = "Length=";
    private static final String CRC = "Crc=";
    private static final String RESPONSE_END = "OK";
    
    public Comunicator(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        
        reader = new BufferedReader(new InputStreamReader(inputStream));
        writer = new BufferedWriter(new OutputStreamWriter(outputStream));
    }
    
    public String sendGet(String params) {
        String req = GET + params + CR_LF;
        
        String res = "";
        try {
            writer.write(req);
            
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.equals(RESPONSE_END)) {
                    break;
                } else {
                    res += line;
                }
            }
        } catch (IOException ex) {
            //logger.error(ex);
        }
        return res;
    }
    
    public String sendPost(String uri, String content) {
        String req = POST + uri + CR_LF
                + LEN + content.length() + CR_LF
                + content + CR_LF;
        
        long crcValue = calculateCrc(req.getBytes());
        
        req += CRC + crcValue + CR_LF;
        
        String res = "";
        try {
            writer.write(req);
            
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.equals(RESPONSE_END)) {
                    break;
                } else {
                    res += line;
                }
            }
        } catch (IOException ex) {
            //logger.error(ex);
        }
        return res;
    }
    
    public String sendBinaryGet(byte[] params) {
        String res = "";
        try {
            writer.write(GET);
            outputStream.write(params, 0, params.length);
            writer.write(CR_LF);
            
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.equals(RESPONSE_END)) {
                    break;
                } else {
                    res += line;
                }
            }
        } catch (IOException ex) {
            //logger.error(ex);
        }
        return res;
    }
    
    public String sendBinaryPost(String uri, byte[] content) {
        String reqBegin = POST + uri + CR_LF
                + LEN + content.length + CR_LF;
        
        Checksum checksum = new CRC32();
        checksum.update(reqBegin.getBytes(), 0, reqBegin.getBytes().length);
        checksum.update(content, 0, content.length);
        checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);
        
        String reqEnd = CRC + checksum.getValue() + CR_LF;
        
        String res = "";
        try {
            outputStream.write(reqBegin.getBytes());
            outputStream.write(content);
            outputStream.write(CR_LF.getBytes());
            outputStream.write(reqEnd.getBytes());
            
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.equals(RESPONSE_END)) {
                    break;
                } else {
                    res += line;
                }
            }
        } catch (IOException ex) {
            //logger.error(ex);
        }
        return res;
    }
    
    public long calculateCrc(byte[] b) {
        Checksum checksum = new CRC32();
        checksum.update(b, 0, b.length);
        return checksum.getValue();
    }

    @Override
    public void run() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
