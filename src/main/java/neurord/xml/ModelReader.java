package neurord.xml;

import java.io.File;
import java.io.Writer;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Properties;
import java.net.URL;
import java.net.URLDecoder;
import java.net.MalformedURLException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.UnmarshallerHandler;
import javax.xml.bind.helpers.DefaultValidationEventHandler;
import javax.xml.namespace.QName;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.InputSource;
import org.xml.sax.Attributes;
import org.xml.sax.XMLReader;
import org.xml.sax.XMLFilter;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.AttributesImpl;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;

import neurord.model.SDRun;
import neurord.util.Settings;
import neurord.util.Logging;

public class ModelReader<T> {
    public static final Logger log = LogManager.getLogger(ModelReader.class);

    public static final String NEURORD_NS = "http://stochdiff.textensor.org";

    public static class XMLUnmarshallingFailure extends RuntimeException {
    }

    public static class NamespaceFiller extends XMLFilterImpl {
        boolean sdrun_seen = false;
        boolean ns_warning = false;
        boolean failed = false;
        boolean conversion_hint = false;

        SAXParseException exception = null;

        final ArrayDeque<String> names = new ArrayDeque<>();
        final HashMap<String, String>[] overrides;

        public NamespaceFiller(HashMap<String, String> ...overrides) {
            this.overrides = overrides;

            boolean first = true;
            for (HashMap<String,String> map: overrides)
                if (map != null)
                    for (Map.Entry<String,String> entry: map.entrySet()) {
                        if (first) {
                            log.debug("Overrides (higher priority first):");
                            first = false;
                        }
                        log.debug("{} = {}", entry.getKey(), entry.getValue());
                    }
            if (first)
                log.debug("No overrides");
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
            throws SAXException
        {
            this.names.push(qName);

            /* support for namespace-less elements */
            if (!this.sdrun_seen && localName.equals("SDRun"))
                this.sdrun_seen = true;
            if (this.sdrun_seen && uri.equals("")) {
                if (!this.ns_warning) {
                    this.ns_warning = true;
                    log.info("{}: namespace not specified, assuming {}",
                             this.location(false), NEURORD_NS);
                }
                uri = NEURORD_NS;
            }
            if (this.exception != null && this.exception.getMessage().contains("cvc-complex-type.2.4.a"))
                /* clear the exception if it seems to be the appropriate type */
                this.exception = null;

            /* rename MaxElementSide to maxElementSide */
            if (this.sdrun_seen && uri.equals(NEURORD_NS) && localName.equals("MaxElementSide"))
                localName = "maxElementSide";

            /* rename dt on OutputSet to outputInterval */
            if (this.sdrun_seen && uri.equals(NEURORD_NS) && localName.equals("OutputSet") &&
                atts.getIndex("dt") >= 0) {

                log.info("Renaming attribute dt to outputInterval");
                AttributesImpl filtered = new AttributesImpl(atts);
                filtered.setLocalName(atts.getIndex("dt"), "outputInterval");
                atts = filtered;
            }

            /* provide a helpful error message for files of the old type */
            if (this.sdrun_seen &&
                (localName.equals("reactionSchemeFile") ||
                 localName.equals("morphologyFile") ||
                 localName.equals("stimulationFile") ||
                 localName.equals("initialConditionsFile") ||
                 localName.equals("outputSchemeFile"))) {
                log.warn("<{}> is not supported any more. Replace with <xi:include href=\"...\" />",
                         localName);
                this.conversion_hint = true;
            }

            super.startElement(uri, localName, qName, atts);
        }

        @Override
        public void endElement(String uri, String localName, String qName)
            throws SAXException
        {
            this.names.pop();
            if (this.sdrun_seen && uri.equals(""))
                uri = NEURORD_NS;
            super.endElement(uri, localName, qName);
        }

        @Override
        public void characters(char[] ch, int start, int length)
            throws SAXException
        {
            String loc = this.location(true);

            for (HashMap<String,String> map: this.overrides)
                if (map != null) {
                    String override = map.get(loc);
                    if (override != null) {
                        String s = new String(ch, start, length).trim();
                        Level level = s.equals(override) ? Level.INFO : Logging.NOTICE;
                        log.log(level,
                                "Overriding {}: {} → {}", loc, s, override);

                        ch = override.toCharArray();
                        start = 0;
                        length = ch.length;
                        break;
                    }
                }

            super.characters(ch, start, length);
        }

        void log_error(SAXParseException e) {
            String id = e.getSystemId();
            String path;
            try {
                String dec = URLDecoder.decode(id, "UTF-8");
                URL url = new URL(dec);
                path = url.getPath();
            } catch(Exception error){
                path = id;
            }

            log.error("{}:{}:{}: {}: {}",
                      path, e.getLineNumber(), e.getColumnNumber(),
                      this.location(false),
                      e.getMessage());
        }

        String location(boolean dots) {
            StringBuilder sb = new StringBuilder();
            String[] names = this.names.toArray(new String[]{});
            for (int i = this.names.size() - 1; i >= 0; i--)
                sb.append((dots ? (sb.length() > 0 ? "." : "") : "/") + names[i]);
            return sb.toString();
        }

        @Override
        public void error(SAXParseException e)
            throws SAXException
        {
            if (this.exception != null) {
                log_error(this.exception);
                this.failed = true;
            }
            this.exception = e;

            super.error(e);
        }

        public boolean failed() {
            if (this.exception != null)
                log_error(this.exception);

            return this.failed || this.exception != null;
        }
    }

    JAXBContext jc;

    public ModelReader(Class<T> klass) {
        try {
            this.jc = JAXBContext.newInstance(klass);
        } catch(JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        Settings.getProperty("neurord.sdrun.*", "Override values in the XML tree", "none");
    }

    protected HashMap<String, String> propertyOverrides() {
        HashMap<String, String> overrides = new HashMap<>();
        Properties props = Settings.getProperties();
        for (String key : props.stringPropertyNames())
            if (key.startsWith("neurord.sdrun") || key.startsWith("neurord.SDRun"))
                overrides.put("SDRun" + key.substring(13), props.getProperty(key));
        return overrides;
    }

    protected T unmarshall(File filename, InputSource xml, HashMap<String,String> extra_overrides)
        throws Exception
    {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setXIncludeAware(true);
        spf.setNamespaceAware(true);
        spf.setFeature("http://apache.org/xml/features/xinclude/fixup-base-uris", false);

        StreamSource schemaSource = new StreamSource(this.getClass().getResourceAsStream("/sdrun.xsd"));

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(schemaSource);
        spf.setSchema(schema);

        NamespaceFiller filter = new NamespaceFiller(this.propertyOverrides(), extra_overrides);
        XMLReader xr = spf.newSAXParser().getXMLReader();
        filter.setParent(xr);

        Unmarshaller u = jc.createUnmarshaller();
        UnmarshallerHandler uh = u.getUnmarshallerHandler();
        u.setSchema(schema);
        u.setEventHandler(new DefaultValidationEventHandler());

        filter.setContentHandler(uh);
        try {
            filter.parse(xml);
        } catch(SAXParseException e) {
            filter.log_error(e);
            throw new XMLUnmarshallingFailure();
        }

        T result = (T) uh.getResult();
        if (result == null || filter.failed())
            throw new XMLUnmarshallingFailure();

        if (filter.conversion_hint)
            log.log(Logging.NOTICE,
                    "Use the following command to convert old style files to the new format:\n" +
                    "sed '1d; 2i <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\\n<SDRun xmlns:xi=\"http://www.w3.org/2001/XInclude\" xmlns=\"{}\">\n" +
                    "s#<(reactionScheme|morphology|stimulation|initialConditions|outputScheme)File>\\s*(\\w+)\\s*</.*>#<xi:include href=\"\\2.xml\" />#' -r -i.bak \"{}\"",
                    NEURORD_NS,
                    filename != null ? filename : "...");

        return result;
    }

    public T unmarshall(File filename, HashMap<String,String> extra_overrides)
        throws Exception
    {
        log.debug("Unmarshalling file {}", filename);

        InputSource source = new InputSource(filename.toString());
        return unmarshall(filename, source, extra_overrides);
    }

    public T unmarshall(String xml, HashMap<String,String> extra_overrides)
        throws Exception
    {
        log.debug("Unmarshalling string");

        InputSource source = new InputSource(new StringReader(xml));
        return unmarshall(null, source, extra_overrides);
    }

    public Marshaller getMarshaller(T object)
        throws Exception
    {
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        return marshaller;
    }

    public void marshall(T object, Writer out)
        throws Exception
    {
        this.getMarshaller(object).marshal(object, out);
    }

    public void marshall(T object, OutputStream out)
        throws Exception
    {
        this.getMarshaller(object).marshal(object, out);
    }

    public void marshall(T object, String filename)
        throws Exception
    {
        OutputStream out = new FileOutputStream(filename);
        this.getMarshaller(object).marshal(object, out);
    }

    public String marshall(T object)
        throws Exception
    {
        StringWriter out = new StringWriter();
        this.marshall(object, out);
        return out.toString();
    }

    public static void main(String... args) throws Exception {
        ModelReader<SDRun> loader = new ModelReader(SDRun.class);

        SDRun sdrun = loader.unmarshall(args[0], null);

        loader.marshall(sdrun, System.out);
    }
}
