/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.otadebugger;

import org.ijs.vesna.otadebugger.view.OtaDebuggerGui;

/**
 *
 * @author Matevz
 */
public class Main {
    
    public Main() {
        OtaDebuggerGui view = new OtaDebuggerGui();
        view.setVisible(true);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Main main = new Main();
    }
}
