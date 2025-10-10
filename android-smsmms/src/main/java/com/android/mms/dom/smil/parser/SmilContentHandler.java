/*
 * Copyright 2014 Jacob Klinker
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

package com.android.mms.dom.smil.parser;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.smil.SMILDocument;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import timber.log.Timber;

import com.android.mms.logs.LogTag;
import com.android.mms.dom.smil.SmilDocumentImpl;

public class SmilContentHandler extends DefaultHandler {
    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

    private SMILDocument mSmilDocument;
    private Node mCurrentNode;

    /**
     * Resets this handler.
     *
     */
    public void reset() {
        mSmilDocument = new SmilDocumentImpl();
        mCurrentNode = mSmilDocument;
    }

    /**
     * Returns the SMILDocument.
     * @return The SMILDocument instance
     */
    public SMILDocument getSmilDocument() {
        return mSmilDocument;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if (LOCAL_LOGV) {
            Timber.v("SmilContentHandler.startElement. Creating element " + localName);
        }
        Element element = mSmilDocument.createElement(localName);
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                if (LOCAL_LOGV) {
                    Timber.v("Attribute " + i +
                        " lname = " + attributes.getLocalName(i) +
                        " value = " + attributes.getValue(i));
                }
                element.setAttribute(attributes.getLocalName(i),
                        attributes.getValue(i));
            }
        }
        if (LOCAL_LOGV) {
            Timber.v("Appending " + localName + " to " + mCurrentNode.getNodeName());
        }
        mCurrentNode.appendChild(element);

        mCurrentNode = element;
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (LOCAL_LOGV) {
            Timber.v("SmilContentHandler.endElement. localName " + localName);
        }
        mCurrentNode = mCurrentNode.getParentNode();
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (LOCAL_LOGV) {
            Timber.v("SmilContentHandler.characters. ch = " + new String(ch, start, length));
        }
    }
}
