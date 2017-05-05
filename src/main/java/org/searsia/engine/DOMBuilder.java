/*
 * Jsoup2DOM Copyright Walter Kasper
 * Json2DOC  Copyright Searsia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.searsia.engine;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Returns a W3C DOM for a Jsoup parsed document.
 * 
 * @author <a href="mailto:kasper@dfki.de">Walter Kasper</a>
 * 
 * Returns a W3C DOM for a Json document
 * 
 * @author Djoerd Hiemstra
 * 
 */
public class DOMBuilder {

  /**
   * Returns a W3C DOM that exposes the content as the supplied XML string. 
   * @param xmlString The XML string to parse.
   * @return A W3C Document.
   * @throws   
   */
  public static Document string2DOM(String xmlString) {

    Document document = null;

    try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        factory.setAttribute("http://javax.xml.XMLConstants/feature/secure-processing", true);
        factory.setAttribute("http://xml.org/sax/features/namespaces", false);
        factory.setAttribute("http://xml.org/sax/features/validation", false);
        factory.setAttribute("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        factory.setAttribute("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder docBuilder = factory.newDocumentBuilder();
        document  =  docBuilder.parse(new InputSource(new StringReader(xmlString)));
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
    return document;
  }

  
  public static String DOM2String(Document document) {
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer transformer;
      try {
          transformer = tf.newTransformer();
          transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
          transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
          transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
          transformer.setOutputProperty(OutputKeys.METHOD, "xml");
          transformer.setOutputProperty(OutputKeys.INDENT, "yes");
          StringWriter writer = new StringWriter();
          transformer.transform(new DOMSource(document), new StreamResult(writer));
          String output = writer.getBuffer().toString();
          return output;
      } catch (Exception e) {
          return "";  
      }
  }


  /**
   * Returns a W3C DOM that exposes the same content as the supplied Jsoup document into a W3C DOM.
   * @param jsoupDocument The Jsoup document to convert.
   * @return A W3C Document.
   */
  public static Document jsoup2DOM(org.jsoup.nodes.Document jsoupDocument) {
    
    Document document = null;
    
    try {
      
      /* Obtain the document builder for the configured XML parser. */
      DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
      
      /* Create a document to contain the content. */
      document = docBuilder.newDocument();
      createDOMfromJsoup(jsoupDocument, document, document, new HashMap<String,String>());
      
    } catch (ParserConfigurationException pce) {
      throw new RuntimeException(pce);
    }
    
    return document;
  }

  
  /**
   * Returns a W3C DOM that exposes the same content as the supplied Jsoup document into a W3C DOM.
   * @param jsoupDocument The Jsoup document to convert.
   * @return A W3C Document.
   */
  public static Document json2DOM(JSONObject jsonDocument) {
    
    Document document = null;

    try {

      /* Obtain the document builder for the configured XML parser. */
      DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
     
      /* Create a document to contain the content. */
      document = docBuilder.newDocument();
      org.w3c.dom.Element _e = document.createElement("root");
      document.appendChild(_e);
      createDOMfromJSONObject(jsonDocument, _e, document);
    } catch (ParserConfigurationException pce) {
      throw new RuntimeException(pce);
    }      
    return document;
  }
  

  /**
   * The internal helper that copies content from the specified Jsoup <tt>Node</tt> into a W3C {@link Node}.
   * @param node The Jsoup node containing the content to copy to the specified W3C {@link Node}.
   * @param out The W3C {@link Node} that receives the DOM content.
   */
  private static void createDOMfromJsoup(org.jsoup.nodes.Node node, Node out, Document doc, Map<String,String> ns) {
    if (node instanceof org.jsoup.nodes.Document) {
      
      org.jsoup.nodes.Document d = ((org.jsoup.nodes.Document) node);
      for (org.jsoup.nodes.Node n : d.childNodes()) {
        createDOMfromJsoup(n, out,doc,ns);
      }
      
    } else if (node instanceof org.jsoup.nodes.Element) {
      
      org.jsoup.nodes.Element e = ((org.jsoup.nodes.Element) node);
      org.w3c.dom.Element _e = doc.createElement(correctXML(e.tagName()));
      out.appendChild(_e);
      org.jsoup.nodes.Attributes atts = e.attributes();
      
      for(org.jsoup.nodes.Attribute a : atts){
        String attName = a.getKey();
        //omit xhtml namespace
        if (attName.equals("xmlns")) {
          continue;
        }
        String attPrefix = getNSPrefix(attName);
        if (attPrefix != null) {
          if (attPrefix.equals("xmlns")) {
            ns.put(getLocalName(attName), a.getValue());
          }
          else if (!attPrefix.equals("xml")) {
            String namespace = ns.get(attPrefix);
            if (namespace == null) {
              //fix attribute names looking like qnames
              attName = attName.replace(':','_');
            }
          }
        }
        try {
          _e.setAttribute(attName, a.getValue());
        } catch (DOMException domExcept) {
           continue;
        }
      }
      
      for (org.jsoup.nodes.Node n : e.childNodes()) {
        createDOMfromJsoup(n, _e, doc,ns);
      }
      
    } else if (node instanceof org.jsoup.nodes.TextNode) {
      
      org.jsoup.nodes.TextNode t = ((org.jsoup.nodes.TextNode) node);
      if (!(out instanceof Document)) {
        out.appendChild(doc.createTextNode(t.text()));
      }
    }
  }
  
  // some hacks for handling namespace in jsoup2DOM conversion
  private static String getNSPrefix(String name) {
    if (name != null) {
      int pos = name.indexOf(':');
      if (pos > 0) {
        return name.substring(0,pos);
      }
    }
    return null;
  }
  
  private static String getLocalName(String name) {
    if (name != null) {
      int pos = name.lastIndexOf(':');
      if (pos > 0) {
        return name.substring(pos+1);
      }
    }
    return name;
  }

  /**
   * The internal helpers that copy content from the specified JSON Object into a W3C {@link Node}.
   * @param json The JSON object containing the content to copy to the specified W3C {@link Node}.
   * @param out The W3C {@link Node} that receives the DOM content.
   */
  private static void createDOMfromJSONObject(JSONObject json, Node out, Document doc) {
    String [] names = JSONObject.getNames(json);
    if (names != null) {
      for (String name : names) {
        Object object = json.get(name);
        if (object instanceof JSONArray) {
          createDOMfromJSONArray((JSONArray) object, out, doc, name);
        } else if (object instanceof JSONObject) {
          org.w3c.dom.Element _e = doc.createElement(correctXML(name));
          out.appendChild(_e);
          createDOMfromJSONObject((JSONObject) object, _e, doc);
        } else {
          createDOMfromJSONPrimitive(object, out, doc, name);
        }
      }
    }
  }

  private static void createDOMfromJSONArray(JSONArray json, Node out, Document doc, String name) {
    for (Object o: json) {
      if (o instanceof JSONArray) {
        org.w3c.dom.Element _e = doc.createElement(correctXML(name));
        out.appendChild(_e);
        createDOMfromJSONArray((JSONArray) o, _e, doc, "list");
      } else if (o instanceof JSONObject) {
        org.w3c.dom.Element _e = doc.createElement(correctXML(name));
        out.appendChild(_e);
        createDOMfromJSONObject((JSONObject) o, _e, doc);
      } else {
        createDOMfromJSONPrimitive(o, out, doc, name);          
      }
    }
  }

  private static void createDOMfromJSONPrimitive(Object object, Node out, Document doc, String name) {
    org.w3c.dom.Element _e = doc.createElement(correctXML(name));
    out.appendChild(_e);
    if (object instanceof String) {
      _e.appendChild(doc.createTextNode((String) object));
    } else if (object instanceof Boolean) {
      _e.appendChild(doc.createTextNode(object.toString()));
    } else if (object instanceof Integer) {
      _e.appendChild(doc.createTextNode(Integer.toString((Integer) object)));
    } else if (object instanceof Double) {
      _e.appendChild(doc.createTextNode(Double.toString((Double) object)));
    }
  }
  
  /**
   * Element names can contain letters, digits, hyphens, underscores, and periods
   * Element names must start with a letter or underscore
   * @param name
   * @return
   */
  private static String correctXML(String name) {
    name = name.replaceAll("[^A-Z0-9a-z\\-_\\.]|^([^A-Za-z_])", "_$1");
    return name;
  }
  
}  