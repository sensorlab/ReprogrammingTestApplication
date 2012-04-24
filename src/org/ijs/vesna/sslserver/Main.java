/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.sslserver;

import java.io.IOException;

/**
 *
 * @author Matevz
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        // TODO code application logic here
        SslServer sslServer = new SslServer(9999);
        (new Thread(sslServer)).start();
        TestUser usr = new TestUser();
        sslServer.addListener(usr);
        
        int c = 0;
        while (true) {
            if (c < 1000000) {
                sslServer.write("Test user writing!");                
            } else {
                sslServer.write("close");
                break;
            }
            c++;
            Thread.sleep(1000);
        }
    }
}
