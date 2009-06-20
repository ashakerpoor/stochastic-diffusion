package org.catacomb.serial.xml;


public final class StringEncoder {



    static String xmlEscape(String sin) {
        String s = sin;
        s = s.replaceAll("&", "&amp;");
        s = s.replaceAll("\"", "\\\\\"");
        s = s.replaceAll("\n", "\\\\n\\\\\n");
        return s;
    }


    static String xmlUnescape(String sin) {
        String s = sin;
        s = s.replaceAll("&amp;", "&");
        s = s.replaceAll("\\\\\"", "\"");
        s = s.replaceAll("\n\n", "\n");
        return s;
    }

}
