package server.monitoring;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionLoggingConfigurationTest {
    @Test
    void everyFileAppenderHasRotationRetentionAndSizeCaps() throws Exception {
        Document document = productionConfiguration();

        assertEquals(0, document.getElementsByTagName("File").getLength(),
                "plain file appenders can grow without a bound");
        NodeList rollingFiles = document.getElementsByTagName("RollingFile");
        assertEquals(7, rollingFiles.getLength());
        for (int i = 0; i < rollingFiles.getLength(); i++) {
            Element appender = (Element) rollingFiles.item(i);
            assertTrue(hasDescendant(appender, "TimeBasedTriggeringPolicy"), appender.getAttribute("name"));
            assertTrue(hasDescendant(appender, "SizeBasedTriggeringPolicy"), appender.getAttribute("name"));
            assertTrue(hasDescendant(appender, "Delete"), appender.getAttribute("name"));
            assertTrue(hasDescendant(appender, "IfLastModified"), appender.getAttribute("name"));
            assertTrue(hasDescendant(appender, "IfAccumulatedFileSize"), appender.getAttribute("name"));
        }
    }

    @Test
    void defaultsToProductionLevelsAndAsynchronousGeneralFileOutput() throws Exception {
        Document document = productionConfiguration();

        assertEquals("${sys:cosmic.log.rootLevel:-info}", property(document, "root-level"));
        assertEquals("${sys:cosmic.log.consoleLevel:-info}", property(document, "console-level"));
        assertEquals("${sys:cosmic.log.packetLevel:-warn}", property(document, "packet-level"));
        assertEquals(1, document.getElementsByTagName("Async").getLength());
        assertEquals("warn", loggerLevel(document, "org.jdbi"));
        assertEquals("warn", loggerLevel(document, "io.netty"));
        assertEquals("warn", loggerLevel(document, "com.zaxxer.hikari"));
    }

    @Test
    void log4jCanStartEveryConfiguredAppender() throws Exception {
        LoggerContext context = new LoggerContext("production-logging-test");
        try (InputStream input = ProductionLoggingConfigurationTest.class.getClassLoader()
                .getResourceAsStream("log4j2.xml")) {
            assertNotNull(input);
            XmlConfiguration configuration = new XmlConfiguration(context, new ConfigurationSource(input));
            configuration.start();
            try {
                assertEquals(10, configuration.getAppenders().size());
                configuration.getAppenders().forEach((name, appender) ->
                        assertTrue(appender.isStarted(), name));
            } finally {
                configuration.stop();
            }
        } finally {
            context.stop();
        }
    }

    private static Document productionConfiguration() throws Exception {
        try (InputStream input = ProductionLoggingConfigurationTest.class.getClassLoader()
                .getResourceAsStream("log4j2.xml")) {
            assertNotNull(input);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private static boolean hasDescendant(Element element, String name) {
        return element.getElementsByTagName(name).getLength() > 0;
    }

    private static String property(Document document, String name) {
        NodeList properties = document.getElementsByTagName("Property");
        for (int i = 0; i < properties.getLength(); i++) {
            Element property = (Element) properties.item(i);
            if (name.equals(property.getAttribute("name"))) {
                return property.getTextContent();
            }
        }
        return "";
    }

    private static String loggerLevel(Document document, String name) {
        NodeList loggers = document.getElementsByTagName("Logger");
        for (int i = 0; i < loggers.getLength(); i++) {
            Node node = loggers.item(i);
            if (node instanceof Element logger && name.equals(logger.getAttribute("name"))) {
                return logger.getAttribute("level");
            }
        }
        return "";
    }
}
