package org.searsia.engine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.w3c.dom.Document;

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
            Document doc = DOMBuilder.json2DOM(json);
            String xml = DOMBuilder.DOM2String(doc);
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
            Document doc = DOMBuilder.jsoup2DOM(jsoup);
            String xml = DOMBuilder.DOM2String(doc);
            System.out.println(xml);
        }
    }

    
}
