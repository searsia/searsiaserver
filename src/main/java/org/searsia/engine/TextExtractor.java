/*
 * Copyright 2016 Searsia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.searsia.engine;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.searsia.Hit;

/**
 * Manage XPath queries and extract the hit fields.
 * 
 * @author Dolf Trieschnigg 
 * @author Djoerd Hiemstra
 */
public class TextExtractor {

	private String field;
	private String xpath;
	private XPathExpression compiledXpath;


	public TextExtractor(String field, String xpath) throws XPathExpressionException {
		this.field = field;
		this.xpath = xpath;
		
		XPathFactory xpFactory = XPathFactory.newInstance();
		XPath xp = xpFactory.newXPath();
		compiledXpath = xp.compile(this.xpath);
	}


	/**
	 * Modifies hit by adding result for the text extractor
	 * @param item An XML context element
	 * @param hit An updated hit
	 * @throws XPathExpressionException
	 */
	public void extract(Node item, Hit hit) throws XPathExpressionException {
        String resultString = ""; // TODO: StringBuilder
		try {
            NodeList nodeList = (NodeList) this.compiledXpath.evaluate(item, XPathConstants.NODESET);
            if (nodeList != null) {
                for (int i=0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    if (!resultString.equals("")) {
                        resultString += " ";
                    }
                    resultString += node.getTextContent();
                }
            }
		} catch (XPathExpressionException e) { // just the STRING result does not work :-(
            resultString = (String) this.compiledXpath.evaluate(item, XPathConstants.STRING);
		}
		if (!resultString.equals("")) {
			hit.put(this.field, processMatch(resultString));
		}
	}

	private String processMatch(String s) {
		s = s.replaceAll("(?i)</?span[^>]*>|</?b>|</?i>|</?em>|</?strong>", "");  // No HTML, please: spans removed 
		s = s.replaceAll("<[^>]+>|\ufffd", " ");  // all other tags or unicode replace character replaced by a space 
    	s = s.trim();  // TODO multiple spaces, \\s ?
		return s;
	}
	
	/**
	 * Get the field for the text extractor
	 * @return field
	 */
	public String getField() {
		return field;
	}

	/**
	 * Get the XPath query for the text extractor
	 * @return XPath query
	 */
	public String getPath() {
		return xpath;
	}
	
    @Override
	public boolean equals(Object o) {
		TextExtractor e = (TextExtractor) o;
		if (!getField().equals(e.getField())) return false;
		return getPath().equals(e.getPath());
	}
}
