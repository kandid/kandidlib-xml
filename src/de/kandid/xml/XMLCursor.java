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

import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.COMMENT;
import static javax.xml.stream.XMLStreamConstants.DTD;
import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION;
import static javax.xml.stream.XMLStreamConstants.SPACE;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * XMLCursor represents an XML element and is used to iterate through its child elements.
 * Iterating is performed by the {@link #all()}, {@link #any()}, {@link #multiple(String)},
 * {@link #optional(String)} and {@link #required(String)} methods.
 * Each {@code XMLCursor} object remembers all of its attributes even while iterating
 * through child elements. However, the object will be reused if a sibling is read from
 * the stream and will therefore be useless for the previous element.<p/>
 * 
 * The test case and the <a href="https://github.com/kandid/kandidlib-xml">github</a>
 * page provide some examples how to use {@code XMLCursor}.
 */
public class XMLCursor implements AutoCloseable {

	/**
	 * An {@link XMLStreamException} that is specialized to accurately report a mismatch
	 * between expected and found element.
	 */
	public static class UnexpectedElementException extends XMLStreamException {

		/**
		 * Builds the exception.
		 * @param expected the local name of the expected element
		 * @param found the local name of the element actually found
		 * @param type the event type encounterd
		 * @param location the position in the XML stream
		 */
		public UnexpectedElementException(String expected, String found, int type, Location location) {
			super("Element " + expected + " expected, found " + found, location);
			_expected = expected;
			_found = found;
			_type = type;
		}
		public final String _expected;
		public final String _found;
		public final int _type;
	}

	/**
	 * An {@link XMLStreamException} that is specialized to indicate malformed representations
	 * of standard XML data types. It is thrown by the convenience methods to access attributes
	 * in {@link XMLCursor}
	 */
	public static class IllegalValueException extends XMLStreamException {

		/**
		 * Builds the exception.
		 * @param value the string that couldn't be converted
		 * @param destType the destination type to convert to
		 * @param location the position in the XML stream
		 */
		public IllegalValueException(String value, String destType, Location location) {
			super("Value '" + value + "' is not a legal " + destType, location);
		}
	}

	private static class Attribute {

		public Attribute(XMLStreamReader p, int i) {
			_name = p.getAttributeLocalName(i);
			_value = p.getAttributeValue(i);
		}

		@Override
		public String toString() {
			return _name + "=" + _value;
		}

		public final String _value;
		public final String _name;
	}

	/**
	 *
	 * @param in	the {@link InputStream} to read the XML document from
	 * @param systemID	the system ID of the document; may be {@code null}
	 * @throws XMLStreamException
	 */
	public XMLCursor(InputStream in, String systemID) throws XMLStreamException {
		this(XMLInputFactory.newInstance().createXMLStreamReader(systemID, in));
	}

	/**
	 * Constructs a new {@code XMLCursor} representing the document as a whole and pointing
	 * to nothing.
	 * @param base the stream to pull the XML events from
	 */
	public XMLCursor(XMLStreamReader base) {
		_base = base;
	}

	/**
	 * Constructor only used internally to create children.
	 * @param parent the parent
	 */
	protected XMLCursor(XMLCursor parent) {
		init(parent._base);
	}

	private XMLCursor init(XMLStreamReader base) {
		_pushBack = false;
		_base = base;
		_child = null;
		saveValues();
		return this;
	}

	/**
	 * Internally called to create a child. This may be useful if you intend to inherit
	 * from {@code XMLCursor} and want the children to be of the same class as the parent.
	 * @param parent the parent
	 * @return a new instance able to represent any of the children
	 */
	protected XMLCursor makeChildCursor(XMLCursor parent) {
		return new XMLCursor(parent);
	}

	/**
	 * Searches for an attribute with the given name in the element represented by
	 * this {@code XMLCursor}. If no such attribute can be found, {@code null} will
	 * be returned. This method may be called even after some of the advance methods
	 * have been called since all attributes are remembered.
	 * @param localName	the local name of the attribute to search for
	 * @return the attribute value if it is present; {@code null} otherwise
	 */
	public String getAttributeValue(String localName) {
		for (int i = 0; i < _nrAttrs; ++i) {
			if (_attrs[i]._name.equals(localName))
				return _attrs[i]._value;
		}
		return null;
	}

	/**
	 * Tells this {@code XMLCursor} that no more child elements are expected. If there are
	 * some, an {@link XMLStreamException} will be thrown.
	 * @throws XMLStreamException if there are more child elements
	 */
	public void done() throws XMLStreamException {
		int type = skipWhite();
		if (type != END_ELEMENT)
			throw new XMLStreamException(getLocation().toString() + ": End element expected but found " + type);
	}

	/**
	 * Skips all children until one with the given name is found. If this element has no
	 * such direct child element, an {@link XMLStreamException} will be thrown.
	 * @param localName the local name of the child to search for
	 * @return an {@code XMLCursor} representing the searched child
	 * @throws XMLStreamException if the element has no such child after the current position
	 */
	public XMLCursor skipTo(String localName) throws XMLStreamException {
		XMLStreamReader base = _base;
		for (;;) {
			any();
			if (_child == null)
				throw new XMLStreamException("No more subelements available", base.getLocation());
			if (_child._localName.equals(localName))
				return _child;
		}
	}

	/**
	 * Returns the next child element. If this child element does not have {@code localName},
	 * an {@link XMLStreamException} will be thrown.
	 * @param localName the required name of the next child element
	 * @return the child with name
	 * @throws UnexpectedElementException if the next child has another name as asserted
	 * @throws XMLStreamException if there are no more children at all
	 */
	public XMLCursor required(String localName) throws XMLStreamException, UnexpectedElementException {
		XMLStreamReader base = _base;
		any();
		if (_child == null)
			throw new XMLStreamException("No more subelements available", base.getLocation());
		if (!_child._localName.equals(localName))
			throw new UnexpectedElementException(localName, _child._localName, base.getEventType(), _base.getLocation());
		return _child;
	}

	/**
	 * Advances to the next child element and returns an {@code XMLCursor} for it iff
	 * that element has {@code localName}. If the next child has another name or there
	 * is no more child at all, {@code null} is returned. In that case this cursor
	 * still points to the same location.
	 * <code>localName</code> may be <code>null</code> to return any child.
	 * @param localName  the local name of the element or <code>null</code>
	 * @return an <code>XMLCursor</code> to the next child or <code>null</code> if the name does not match
	 * @throws XMLStreamException
	 */
	public XMLCursor optional(String localName) throws XMLStreamException {
		XMLCursor ret = any();
		if (ret == null)
			return null;
		if (localName == null || localName.equals(ret._localName))
			return ret;
		_pushBack = true;
		return null;
	}

	/**
	 * Returns an {@link Iterable} yielding all children with {@code localName} immediately
	 * following the current position. It is perfectly legal if there is no such child.
	 * @param localName the name of the children to iterate over
	 * @return the {@link Iterable}
	 */
	public Multiple multiple(String localName) {
		return new Multiple(localName);
	}

	/**
	 * Objects of this class will be instantiated by {@link XMLCursor#multiple(String)}.
	 * There should be rarely a reason to do this by yourself.
	 */
	public class Multiple extends All {

		public Multiple(String localName) {
			_wanted = localName;
		}

		@Override
		XMLCursor prefetchNext() throws XMLStreamException {
			return optional(_wanted);
		}

		private final String _wanted;
	}

	/**
	 * Returns the next child. If there are no more children, {@code null} will be returned.
	 * @return the next child or {@code null} if there is no more child
	 * @throws XMLStreamException
	 */
	public XMLCursor any() throws XMLStreamException {
		if (_base == null)
			return null;
		if (_pushBack) {
			_pushBack = false;
			return _child;
		}
		if (_child != null && _child._base != null)
			_child.finish();
		int type = skipWhite();
		if (type == END_ELEMENT || type == END_DOCUMENT) {
			_base = null;
			return _child = null;
		}
		return _spare = _child = _spare != null ? _spare.init(_base) : makeChildCursor(this);
	}

	/**
	 * Returns the characters of the text contained in this element. The current implementation
	 * requires that no other elements are present.
	 * @return the characters of the text child element(s)
	 * @throws XMLStreamException
	 */
	public String getText() throws XMLStreamException {
		StringBuffer ret = new StringBuffer();
		int type = next();
		for (;type == CHARACTERS; type = next())
			ret.append(_base.getText());
		if (type != END_ELEMENT)
			//TODO support all cases
			throw new XMLStreamException("Only END_ELEMENT in getText() currently supported", getLocation());
		_base = null;
		return ret.toString();
	}

	/**
	 * Closes the underlying {@link XMLStreamReader}.
	 */
	public void close() throws XMLStreamException {
		_base.close();
	}

	/**
	 * Objects of this class will be instantiated by {@link XMLCursor#all()}.
	 * There should be rarely a reason to do this by yourself.
	 */
	public class All implements Iterator<XMLCursor>, Iterable<XMLCursor> {
		public boolean hasNext() {
			if (_next == null)
				try {
					_next = prefetchNext();
				} catch (XMLStreamException e) {
					throw new RuntimeException(e);
				}
			return _next != null;
		}

		public XMLCursor next() {
			XMLCursor ret = _next;
			_next = null;
			return ret;
		}

		public void remove() {
			throw new UnsupportedOperationException("remove not supported");
		}

		XMLCursor prefetchNext() throws XMLStreamException {
			return any();
		}

		XMLCursor _next;

		@Override
		public Iterator<XMLCursor> iterator() {
			return this;
		}
	}

	/**
	 * Returns an {@link Iterable}, that enumerates all children elements
	 * @return an <code>Iterable</code> for all children
	 */
	public All all() {
		return new All();
	}

	/**
	 * Advances to the end of the element pointed to by this cursor.
	 * Any subelements started with calls to {@link #any()}, {@link #required(String)} or
	 * {@link #optional(String)} are finished as well. However, attributes that are saved
	 * in those childs are preserved until the next call to one of those methods.
	 * @throws XMLStreamException
	 * @see #done()
	 */
	public void finish() throws XMLStreamException {
		if (_child != null && _child._base != null)
			_child.finish();
		for (int level = 0; level >= 0; ) {
			int type = next();
			if (type == START_ELEMENT)
				++level;
			else if (type == END_ELEMENT)
				--level;
		}
		_base = null;
		_spare = _child;
	}

	/**
	 * Returns the local name of the element represented by this {@code XMLCursor}.
	 * @return the local name of the represented element
	 */
	public String getLocalName() {
		return _localName;
	}

	/**
	 * Returns the current parse position of the underlying StAX parser.
	 * <em>Note</em>: the returned position is <em>only</em> valid <em>immediately</em>
	 * after this cursor is created. This is because the underlying parser is common to
	 * all parent and child cursors and changes state with most invocations to one of
	 * those.
	 * @return the current position
	 */
	public Location getLocation() {
		return _base.getLocation();
	}

	/**
	 * Returns the current location as a string. Overridden to simplify debugging.
	 * @return the current location as a {@code String}
	 */
	@Override
	public String toString() {
		return getLocation().toString();
	}

	private int skipWhite() throws XMLStreamException {
		int type = next();
		while (type == SPACE || type == PROCESSING_INSTRUCTION || (type == CHARACTERS || type == COMMENT || type == DTD))
			type = next();
		return type;
	}

	private int next() throws XMLStreamException {
		int ret = _base.next();
		//      if (false) {
			//         Location l = _base.getLocation();
		//         System.out.println("Event "+ ret + " (" + l.getLineNumber() + ":" + l.getColumnNumber() + ")");
		//      }
		return ret;
	}

	/**
	 * Return a {@code boolean} attribute value.
	 * Looks for an attribute with the given name and tries to make a boolean out of it.
	 * If there is no such attribute the default value will be returned.
	 * If there is an attribute by this name but does not represent an XML booelan, an
	 * {@link IllegalValueException} will be thrown.
	 * @param localName the local name of the attribute to look for
	 * @param ifNot the default value if no attribute by this name can be found
	 * @return the converted attribute value
	 * @throws IllegalValueException if there is such an attribute but does not contain a valid XML boolean
	 */
	public boolean getBoolean(String localName, boolean ifNot) throws IllegalValueException {
		String value = getAttributeValue(localName);
		if (value == null)
			return ifNot;
		if ("true".equals(value))
			return true;
		if ("false".equals(value))
			return false;
		throw new IllegalValueException(value, "boolean", getLocation());
	}

	/**
	 * Return a {@code boolean} attribute value.
	 * Looks for an attribute with the given name and tries to make a boolean out of it.
	 * If there is no such attribute an exception will be thrown.
	 * If the attribute value does not represent an XML booelan, an
	 * {@link IllegalValueException} will be thrown.
	 * @param localName the local name of the attribute to look for
	 * @return the converted attribute value
	 * @throws IllegalValueException if the attribute does not contain a valid XML boolean
	 */
	public boolean getBoolean(String localName) throws IllegalValueException {
		String value = getAttributeValue(localName);
		if ("true".equals(value))
			return true;
		if ("false".equals(value))
			return false;
		throw new IllegalValueException(value, "boolean", getLocation());
	}

	/**
	 * Return an {@code int} attribute value.
	 * Looks for an attribute with the given name and tries to make an int out of it.
	 * If there is no such attribute an exception will be thrown.
	 * If the attribute value does not represent an XML integer, an
	 * {@link IllegalValueException} will be thrown.
	 * @param localName the local name of the attribute to look for
	 * @return the converted attribute value
	 * @throws IllegalValueException if the attribute does not contain a valid XML int
	 */
	public int getInt(String localName) throws IllegalValueException {
		String value = getAttributeValue(localName);
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalValueException(value, "int", getLocation());
		}
	}

	/**
	 * Return an {@code int} attribute value.
	 * Looks for an attribute with the given name and tries to make an int out of it.
	 * If there is no such attribute the default value will be returned.
	 * If there is an attribute by this name but does not represent an XML int, an
	 * {@link IllegalValueException} will be thrown.
	 * @param localName the local name of the attribute to look for
	 * @param ifNot the default value if no attribute by this name can be found
	 * @return the converted attribute value
	 * @throws IllegalValueException if there is such an attribute but does not contain a valid XML int
	 */
	public int getInt(String localName, int ifNot) throws IllegalValueException {
		String value = getAttributeValue(localName);
		if (value == null)
			return ifNot;
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalValueException(value, "int", getLocation());
		}
	}

	/**
	 * Return a {@code long} attribute value.
	 * Looks for an attribute with the given name and tries to make a long out of it.
	 * If there is no such attribute an exception will be thrown.
	 * If the attribute value does not represent an XML booelan, an
	 * {@link IllegalValueException} will be thrown.
	 * @param localName the local name of the attribute to look for
	 * @return the converted attribute value
	 * @throws IllegalValueException if the attribute does not contain a valid XML long
	 */
	public long getLong(String localName) throws IllegalValueException {
		String value = getAttributeValue(localName);
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new IllegalValueException(value, "long", getLocation());
		}
	}

	/**
	 * Return a {@code long} attribute value.
	 * Looks for an attribute with the given name and tries to make a long out of it.
	 * If there is no such attribute the default value will be returned.
	 * If there is an attribute by this name but does not represent an XML booelan, an
	 * {@link IllegalValueException} will be thrown.
	 * @param localName the local name of the attribute to look for
	 * @param ifNot the default value if no attribute by this name can be found
	 * @return the converted attribute value
	 * @throws IllegalValueException if there is such an attribute but does not contain a valid XML long
	 */
	public long getLong(String localName, long ifNot) throws IllegalValueException {
		String value = getAttributeValue(localName);
		if (value == null)
			return ifNot;
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new IllegalValueException(value, "long", getLocation());
		}
	}

	/**
	 * Looks for an attribute with the given name and returns it.
	 * If there is no such attribute an exception will be thrown.
	 * @param localName the local name of the attribute to look for
	 * @return the attribute value
	 * @throws XMLStreamException if the attribute is not present
	 */
	public String getString(String localName) throws XMLStreamException {
		String ret = getAttributeValue(localName);
		if (ret == null)
			throw new XMLStreamException("Required attribute '" + localName + "' not found", getLocation());
		return ret;
	}

	/**
	 * Looks for an attribute with the given name and returns it.
	 * If there is no such attribute the default value {@code ifNot} will be returned.
	 * @param localName the local name of the attribute to look for
	 * @return the attribute value or default value
	 */
	public String getString(String localName, String ifNot) {
		String ret = getAttributeValue(localName);
		if (ret == null)
			return ifNot;
		return ret;
	}

	public Date getDate(String localName) throws XMLStreamException {
		String value = getString(localName);
		try {
			return _date.parse(value);
		} catch (ParseException e) {
			throw new IllegalValueException(value, "date", getLocation());
		}
	}
	static final SimpleDateFormat _date = new SimpleDateFormat("yyyy-MM-dd");

	private void saveValues() {
		_localName = _base.getLocalName();
		_nrAttrs = _base.getAttributeCount();
		if (_attrs.length < _nrAttrs)
			_attrs = new Attribute[_nrAttrs];
		for (int i = 0; i < _nrAttrs; ++i)
			_attrs[i] = new Attribute(_base, i);
	}

	private boolean _pushBack;

	private Attribute[] _attrs = _none;

	private static final Attribute[] _none = new Attribute[0];

	private XMLStreamReader _base;

	private XMLCursor _child;

	private XMLCursor _spare;

	private int _nrAttrs;

	private String _localName;
}