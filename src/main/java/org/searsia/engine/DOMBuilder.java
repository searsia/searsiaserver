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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

// http://www.mkyong.com/java/how-to-create-xml-file-in-java-dom/



/**
 * Returns a W3C DOM for a Jsoup parsed document or a Json parsed document.
 * 
 * @author Walter Kasper
 * @author Djoerd Hiemstra
 * 
 */
public class DOMBuilder {

    private Document document;
  
    public DOMBuilder() {
        this.document = null;
    }

    public DOMBuilder(Document document) {
        this.document = document;
    }

    /**
     * Creates a W3C DOM from the supplied XML string. 
     * @param xmlString The XML string to parse.
     * @throws RuntimeException if not well-formed
     */
    public DOMBuilder fromXMLString(String xmlString) {
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
            this.document  =  docBuilder.parse(new InputSource(new StringReader(xmlString)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * Creates a W3C DOM from the supplied Jsoup Document.
     * @param jsonDocument The Jsoup document to convert.
     */
    public DOMBuilder fromJsoup(org.jsoup.nodes.Document jsoupDocument) {
        this.document = jsoup2DOM(jsoupDocument);
        return this;
    }

    /**
     * Creates a W3C DOM from the supplied JSON Object.
     * @param jsonDocument The JSON Object to convert.
     */
    public DOMBuilder fromJSON(JSONObject jsonDocument) {
        this.document = json2DomParseOption(jsonDocument, false);
        return this;
    }

    /**
     * Creates a W3C DOM from the supplied JSON Object.
     * Additionally parses HTML strings into XML.
     * @param jsonDocument The JSON Object to convert.
     */
    public DOMBuilder fromJSONandHTML(JSONObject jsonDocument) {
        this.document = json2DomParseOption(jsonDocument, true);
        return this;
    }
   
    /**
     * Creates an empty W3C DOM .
     */
    public DOMBuilder newDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = factory.newDocumentBuilder();
            this.document = docBuilder.newDocument();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }
    
    public void setRoot(Element rootElement) {
        this.document.appendChild(rootElement);
    }
    
    public Element createElement(String elementName) {
        return this.document.createElement(elementName);
    }
    
    public Text createTextNode(String string) {
        return this.document.createTextNode(string);
    }
    
    public Element createTextElement(String elementName, String string) {
        Element element = this.document.createElement(elementName);
        element.appendChild(this.document.createTextNode(string));
        return element;
    }
        
    public Element getDocumentElement() {
        return this.document.getDocumentElement();
    }
    
    public Document getDocument() {
        return this.document;
    }
    
    /**
     * Returns a W3C DOM from the supplied Jsoup document.
     * @param jsoupDocument The Jsoup document to convert.
     * @return Document
     */
    private Document jsoup2DOM(org.jsoup.nodes.Document jsoupDocument) {
        Document doc = null;
        try {
            /* Obtain the document builder for the configured XML parser. */
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            
            /* Create a document to contain the content. */
            doc = docBuilder.newDocument();
            createDOMfromJsoup(jsoupDocument, doc, doc, new HashMap<String,String>());
        } catch (ParserConfigurationException pce) {
            throw new RuntimeException(pce);
        }
        return doc;
    }
   
    private Document json2DomParseOption(JSONObject jsonDocument, boolean parseHTML) {
        Document doc = null;
        try {
            /* Obtain the document builder for the configured XML parser. */
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
           
            /* Create a document to contain the content. */
            doc = docBuilder.newDocument();
            org.w3c.dom.Element _e = doc.createElement("root");
            doc.appendChild(_e);
            createDOMfromJSONObject(jsonDocument, _e, doc, parseHTML);
        } catch (ParserConfigurationException pce) {
            throw new RuntimeException(pce);
        }
        return doc;
    }

    /**
     * The internal helper that copies content from the specified Jsoup <tt>Node</tt> into a W3C {@link Node}.
     * @param node The Jsoup node containing the content to copy to the specified W3C {@link Node}.
     * @param out The W3C {@link Node} that receives the DOM content.
     */
    private void createDOMfromJsoup(org.jsoup.nodes.Node node, Node out, Document doc, Map<String,String> ns) {
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
    private String getNSPrefix(String name) {
      if (name != null) {
        int pos = name.indexOf(':');
        if (pos > 0) {
          return name.substring(0,pos);
        }
      }
      return null;
    }
    
    private String getLocalName(String name) {
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
    private void createDOMfromJSONObject(JSONObject json, Node out, Document doc, boolean parseHTML) {
      String [] names = JSONObject.getNames(json);
      if (names != null) {
        for (String name : names) {
          Object object = json.get(name);
          if (object instanceof JSONArray) {
            createDOMfromJSONArray((JSONArray) object, out, doc, name, parseHTML);
          } else if (object instanceof JSONObject) {
            org.w3c.dom.Element _e = doc.createElement(correctXML(name));
            out.appendChild(_e);
            createDOMfromJSONObject((JSONObject) object, _e, doc, parseHTML);
          } else {
            createDOMfromJSONPrimitive(object, out, doc, name, parseHTML);
          }
        }
      }
    }

    private void createDOMfromJSONArray(JSONArray json, Node out, Document doc, String name, boolean parseHTML) {
      for (Object o: json) {
        if (o instanceof JSONArray) {
          org.w3c.dom.Element _e = doc.createElement(correctXML(name));
          out.appendChild(_e);
          createDOMfromJSONArray((JSONArray) o, _e, doc, "list", parseHTML);
        } else if (o instanceof JSONObject) {
          org.w3c.dom.Element _e = doc.createElement(correctXML(name));
          out.appendChild(_e);
          createDOMfromJSONObject((JSONObject) o, _e, doc, parseHTML);
        } else {
          createDOMfromJSONPrimitive(o, out, doc, name, parseHTML);
        }
      }
    }

    private void createDOMfromJSONPrimitive(Object object, Node out, Document doc, String name, boolean parseHTML) {
      org.w3c.dom.Element _e = doc.createElement(correctXML(name));
      out.appendChild(_e);
      if (object instanceof String && parseHTML) {
          org.jsoup.nodes.Document jsoupDoc = org.jsoup.Jsoup.parse((String) object);
          Document xmlDoc = jsoup2DOM(jsoupDoc);
          _e.appendChild(doc.importNode(xmlDoc.getDocumentElement(), true));
      } else if (object instanceof String) {
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
     * XML Element names can contain letters, digits, hyphens, underscores, and periods
     * Element names must start with a letter or underscore
     * @param name XML element name
     * @return correct XML element name
     */
    private String correctXML(String name) {
      name = name.replaceAll("[^A-Z0-9a-z\\-_\\.]|^([^A-Za-z_])", "_$1");
      return name;
    }  
  
    /**
     * Returns an XML string for a W3C Document 
     * @param document A W3C Document
     * @return XML string
     */
    @Override
    public String toString() {
        return toString(0);
    }
        
    
    public String toString(Integer indent) {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer;
        try {
            transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            if (indent > 0) {
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", indent.toString());                
            }
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(this.document), new StreamResult(writer));
            String output = writer.getBuffer().toString();
            return output;
        } catch (Exception e) {
            return "";  
        }
    }

}