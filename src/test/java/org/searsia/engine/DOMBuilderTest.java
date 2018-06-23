package org.searsia.engine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import org.junit.Assert;

import org.searsia.engine.DOMBuilder;

public class DOMBuilderTest {

    private static String readFile(String fileName) throws IOException {
        String s, result = "";
        BufferedReader reader = new BufferedReader(new FileReader("src/test/resources/" + fileName)); 
        try {
            while ((s = reader.readLine()) != null) {
                result += s;
            }
        }
        finally {
            reader.close();
        }
        return result;
    }
    
    @Test
    public void testXMLCreate() {
        DOMBuilder builder = new DOMBuilder();
        builder.newDocument();
        Element root = builder.createElement("rss");
        builder.setRoot(root);
        root.setAttribute("version", "2.0");
        Element message = builder.createTextElement("message", "hello, world!");
        root.appendChild(message);
        String actualXML = builder.toString();
        String expectedXML = "<rss version=\"2.0\"><message>hello, world!</message></rss>";
        Assert.assertEquals(expectedXML, actualXML);
    }

    /**
     *  Only run tests if files test.json/test.html/test.xml are present in resources
     */
    
    @Test
    public void testJsonFileIfExists() {
        String jsonString = null;
        try {
            jsonString = readFile("test.json");
        } catch (IOException e) { }
        if (jsonString != null) {
            if (jsonString.startsWith("[")) {
                jsonString = "{\"list\":" + jsonString + "}";
            }
            JSONObject json = new JSONObject(jsonString);
            DOMBuilder builder = new DOMBuilder().fromJSON(json);
            String xml = builder.toString();
            System.out.println(xml);
        }
    }

    @Test
    public void testHtmlFileIfExists() {
        String htmlString = null;
        try {
            htmlString = readFile("test.html");
        } catch (IOException e) { }
        if (htmlString != null) {
            org.jsoup.nodes.Document jsoup = Jsoup.parse(htmlString);
            DOMBuilder builder = new DOMBuilder().fromJsoup(jsoup);
            String xml = builder.toString();
            System.out.println(xml);
        }
    }

    @Test
    public void testXmlFileIfExists() {
        String xmlString = null;
        try {
            xmlString = readFile("test.xml");
        } catch (IOException e) { }
        if (xmlString != null) {
            DOMBuilder builder = new DOMBuilder().fromXMLString(xmlString);
            String xml = builder.toString();
            System.out.println(xml);
        }
    }
    
}
