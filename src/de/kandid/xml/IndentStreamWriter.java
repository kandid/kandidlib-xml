/*
 * (C) Copyright 2009-2014, by Dominikus Diesch.
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

package de.kandid.xml;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class IndentStreamWriter implements XMLStreamWriter, AutoCloseable {

	public IndentStreamWriter(OutputStream os) throws XMLStreamException, FactoryConfigurationError {
		this(XMLOutputFactory.newInstance().createXMLStreamWriter(os, "UTF-8"));
	}

   public IndentStreamWriter(XMLStreamWriter delegate) {
      _delegate = delegate;
   }

   public void close() throws XMLStreamException {
      _delegate.close();
   }

   public void flush() throws XMLStreamException {
      _delegate.flush();
   }

   public NamespaceContext getNamespaceContext() {
      return _delegate.getNamespaceContext();
   }

   public String getPrefix(String uri) throws XMLStreamException {
      return _delegate.getPrefix(uri);
   }

   public Object getProperty(String name) throws IllegalArgumentException {
      return _delegate.getProperty(name);
   }

   public void setDefaultNamespace(String uri) throws XMLStreamException {
      _delegate.setDefaultNamespace(uri);
   }

   public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
      _delegate.setNamespaceContext(context);
   }

   public void setPrefix(String prefix, String uri) throws XMLStreamException {
      _delegate.setPrefix(prefix, uri);
   }

   public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
      _delegate.writeAttribute(prefix, namespaceURI, localName, value);
   }

   public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
      _delegate.writeAttribute(namespaceURI, localName, value);
   }

   public void writeAttribute(String localName, String value) throws XMLStreamException {
      _delegate.writeAttribute(localName, value);
   }

   public void writeCData(String data) throws XMLStreamException {
      _delegate.writeCData(data);
      _needNL = false;
   }

   public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
      _delegate.writeCharacters(text, start, len);
      _needNL = false;
   }

   public void writeCharacters(String text) throws XMLStreamException {
      _delegate.writeCharacters(text);
      _needNL = false;
   }

   public void writeComment(String data) throws XMLStreamException {
      _delegate.writeComment(data);
      _needNL = false;
   }

   public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
      _delegate.writeDefaultNamespace(namespaceURI);
      _needNL = false;
   }

   public void writeDTD(String dtd) throws XMLStreamException {
      indent();
      _delegate.writeDTD(dtd);
      _needNL = true;
   }

   public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
      indent();
      _delegate.writeEmptyElement(prefix, localName, namespaceURI);
      _needNL = true;
   }

   public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
      indent();
      _delegate.writeEmptyElement(namespaceURI, localName);
      _needNL = true;
   }

   public void writeEmptyElement(String localName) throws XMLStreamException {
      indent();
      _delegate.writeEmptyElement(localName);
      _needNL = true;
   }

   public void writeEndDocument() throws XMLStreamException {
      _delegate.writeEndDocument();
   }

   public void writeEndElement() throws XMLStreamException {
      --_level;
      if (_needNL)
         indent();
      _delegate.writeEndElement();
      _needNL = true;
   }

   public void writeEntityRef(String name) throws XMLStreamException {
      _delegate.writeEntityRef(name);
      _needNL = false;
   }

   public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
      _delegate.writeNamespace(prefix, namespaceURI);
      _needNL = false;
   }

   public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
      indent();
      _delegate.writeProcessingInstruction(target, data);
      _needNL = true;
   }

   public void writeProcessingInstruction(String target) throws XMLStreamException {
      indent();
      _delegate.writeProcessingInstruction(target);
      _needNL = true;
   }

   public void writeStartDocument() throws XMLStreamException {
      _delegate.writeStartDocument();
      _needNL = true;
   }

   public void writeStartDocument(String encoding, String version) throws XMLStreamException {
      _delegate.writeStartDocument(encoding, version);
      _needNL = true;
   }

   public void writeStartDocument(String version) throws XMLStreamException {
      _delegate.writeStartDocument(version);
      _needNL = true;
   }

   public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
      indent();
      ++_level;
      _delegate.writeStartElement(prefix, localName, namespaceURI);
      _needNL = false;
   }

   public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
      indent();
      ++_level;
      _delegate.writeStartElement(namespaceURI, localName);
      _needNL = false;
   }

   public void writeStartElement(String localName) throws XMLStreamException {
      indent();
      ++_level;
      _delegate.writeStartElement(localName);
      _needNL = false;
   }

   public void writeTextElement(String localName, String text) throws XMLStreamException {
      _delegate.writeStartElement(localName);
      _delegate.writeCharacters(text);
      _delegate.writeEndElement();

   }

   public void attr(String localName, String value) throws XMLStreamException {
      writeAttribute(localName, value);
   }

   public void attr(String localName, int value) throws XMLStreamException {
      writeAttribute(localName, Integer.toString(value));
   }

   private static final SimpleDateFormat _date = new SimpleDateFormat("yyyy-MM-dd");
   public void attr(String localName, Date date) throws XMLStreamException {
      writeAttribute(localName, _date.format(date));
   }

   private void indent() throws XMLStreamException {
      int len = _level + 1;
      if (len > _indent.length)
         _indent = makeIndent(len * 2);
      _delegate.writeCharacters(_indent, 0, len);
   }

   private static char[] makeIndent(int len) {
      char[] ret = new char[len];
      Arrays.fill(ret, '\t');
      ret[0] = '\n';
      return ret;
   }

   private int _level;
   private boolean _needNL;
   private final XMLStreamWriter _delegate;
   private char[] _indent = makeIndent(4);
}
