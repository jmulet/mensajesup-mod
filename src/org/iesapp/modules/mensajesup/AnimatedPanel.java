/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.iesapp.modules.mensajesup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.Timer;

/**
 *
 * @author Josep
 */
  
public class AnimatedPanel extends JDialog{
    
    public static final byte LETTER = 0; 
    
    public AnimatedPanel(boolean modal,int timerInterval, int picture){
        super((JFrame) null, modal);
       
        String url ="/org/iesapp/framework/icons/animated-gifs-letters-02.gif";
        switch(picture)
        {
            case 0: url="/org/iesapp/framework/icons/animated-gifs-letters-02.gif";break;
        }
        
        Icon icon = new javax.swing.ImageIcon(getClass().getResource(url));
        JLabel label = new JLabel(icon); // NOI18N
  
        this.setUndecorated(true);
        this.setLocationRelativeTo(null);
        this.setAlwaysOnTop(true);
        this.setLocationRelativeTo(null);     
        
        this.getContentPane().add(label);
        this.pack();
        
        Timer timer = new Timer(timerInterval, new ActionListener ()
        {
            @Override
                public void actionPerformed(ActionEvent e)
                {
                    tasksTimer();
                }


         });
        if(timerInterval>0)
        {
            timer.start();
        }
    }
    
     private void tasksTimer() {
           this.dispose();
     }
     
    
}