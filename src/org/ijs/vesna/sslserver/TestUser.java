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
public class TestUser implements SslDataListener {

    public TestUser() throws InterruptedException {
        // TODO code application logic here
        //SslServer sslServer = new SslServer(8888);
        //(new Thread(sslServer)).start();        
        //sslServer.addListener(this);
        //while (true) {
        //  sslServer.write("Test user writing!");
        //Thread.sleep(1000);
        //}
    }

    @Override
    public void notifySslDataListener(String str) {
        System.out.println("Test user reading: " + str);
    }
}
