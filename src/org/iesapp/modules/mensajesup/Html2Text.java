/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.iesapp.modules.mensajesup;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.*;
import javax.swing.text.html.HTML.Tag;
import javax.swing.text.html.parser.*;

public class Html2Text extends HTMLEditorKit.ParserCallback {
 private StringBuffer s;

 public Html2Text() {}

 public void parse(Reader in) throws IOException {
   s = new StringBuffer();
   ParserDelegator delegator = new ParserDelegator();
   // the third parameter is TRUE to ignore charset directive
   delegator.parse(in, this, Boolean.TRUE);
 }

 @Override
 public void handleStartTag(Tag t, MutableAttributeSet a, int pos) {
        super.handleStartTag(t, a, pos);
        if(t.breaksFlow())
        {
            s.append(" ");
        }
 }
 
 @Override
 public void handleText(char[] text, int pos) {
   s.append(text);
 }

 public String getText() {
   return s.toString();
 }

 public static void main (String[] args) {
   try {
   

     StringReader in = new StringReader("<b>sdsdsd</b><br>golor");
     Html2Text parser = new Html2Text();
     parser.parse(in);
     in.close();
     System.out.println(parser.getText());
   }
   catch (Exception e) {
     e.printStackTrace();
   }
 }
}