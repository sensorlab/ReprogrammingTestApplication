/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.sslserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import javax.net.ssl.SSLServerSocketFactory;

/**
 *
 * @author Matevz
 */
public class SslServer implements Runnable {

    private SslDataListener sslDataListener;
    private BufferedReader reader = null;
    private BufferedWriter writer = null;
    private int port;
    private boolean socketOpened = false;
    private boolean runServer = true;
    private ArrayList<SslDataListener> SslServerListenersRegister = new ArrayList<SslDataListener>();

    public SslServer(int port) {
        this.port = port;
    }

    public void addSslServerListener(SslDataListener listener) {
        SslServerListenersRegister.add(listener);
    }

    public void removeSslServerListener(SslDataListener listener) {
        SslServerListenersRegister.remove(listener);
    }

    public void notifySslServerListeners(String line) {
        for (int i = 0; i < SslServerListenersRegister.size(); i++) {
            ((SslDataListener) SslServerListenersRegister.get(i)).notifySslDataListener(line);
        }
    }

    public synchronized void write(String str) {
        if (writer != null && socketOpened) {
            try {
                writer.write(str + "\r\n");
                writer.flush();
            } catch (IOException ex) {
                System.out.println(ex);
            }
        }
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
                System.out.println("The SSL Server is listening on port " + ss.getLocalPort());

                while (runServer) {
                    try {
                        Socket s = null;
                        try {
                            System.out.println("Waiting for client connection.");
                            s = ss.accept();
                            System.out.println("Client with IP "
                                    + s.getInetAddress().getHostAddress()
                                    + " successfully connected to the server.");

                            reader = new BufferedReader(new InputStreamReader(s.getInputStream()));

                            writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));

                            socketOpened = true;

                            String line = null;
                            while ((line = reader.readLine()) != null) {
                                if (!line.equals("close")) {
                                    notifySslServerListeners(line);
                                } else {
                                    runServer = false;
                                    break;
                                }
                            }
                            System.out.println("The SSL Socket is now closed.");
                        } catch (Exception ex) {
                            System.out.println("The SSL Socket was closed unexpectedly.");
                            System.out.println(ex);
                        } finally {
                            socketOpened = false;
                            reader.close();
                            writer.close();
                            s.close();
                        }
                    } catch (Exception ex) {
                        System.out.println(ex);
                    }
                }
                System.out.println("The SSL Server shutted down.");
            } catch (Exception ex) {
                System.out.println(ex);
            } finally {
                ss.close();
            }
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }
}
