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

import javax.xml.stream.XMLStreamException;

import junit.framework.TestCase;

public class XMLCursorTest extends TestCase {

   public XMLCursorTest(String name) {
      super(name);
   }

   public void testRequired() throws Exception {
      XMLCursor root = getTestXML("XMLCursor.simpleParsing.xml").required("root");
      for (int i = 0; i < 2; ++i) {
         XMLCursor outer = root.required("outer");
         for (int nr = 0; nr < 3; ++nr) {
            XMLCursor inner = outer.required("inner");
            assertEquals(nr, Integer.parseInt(inner.getAttributeValue("nr")));
         }
      }
   }

   public void testValueConservation() throws Exception {
      XMLCursor root = getTestXML("XMLCursor.simpleParsing.xml").required("root");
      for (int i = 0; i < 2; ++i) {
         XMLCursor outer = root.required("outer");
         for (int nr = 0; nr < 3; ++nr) {
            XMLCursor inner = outer.required("inner");
            assertEquals("outer", outer.getLocalName());
            assertEquals(nr, Integer.parseInt(inner.getAttributeValue("nr")));
            assertEquals(i, Integer.parseInt(outer.getAttributeValue("outerNr")));
         }
      }
   }

   public void testProceedOnOuterLevel() throws Exception {
      XMLCursor root = getTestXML("XMLCursor.simpleParsing.xml").required("root");
      for (int i = 0; i < 2; ++i) {
         XMLCursor outer = root.required("outer");
         for (int nr = 0; nr < 2; ++nr) {
            XMLCursor inner = outer.required("inner");
            assertEquals(nr, Integer.parseInt(inner.getAttributeValue("nr")));
         }
      }
   }

   public void testOptional() throws Exception {
      XMLCursor root = getTestXML("XMLCursor.simpleParsing.xml").required("root");
      assertNull(root.optional("inner"));
      XMLCursor outer = root.optional("outer");
      assertNotNull(outer);
      assertEquals(0, Integer.parseInt(outer.getAttributeValue("outerNr")));
      outer = root.optional("outer");
      assertNotNull(outer);
      assertEquals(1, Integer.parseInt(outer.getAttributeValue("outerNr")));
      int nr = 0;
      for (XMLCursor inner = outer.optional("inner"); inner != null; inner = outer.optional("inner")) {
         assertEquals(nr++, Integer.parseInt(inner.getAttributeValue("nr")));
         assertEquals(1, Integer.parseInt(outer.getAttributeValue("outerNr")));
      }
   }

   public void testMultiple() throws Exception {
      XMLCursor root = getTestXML("XMLCursor.simpleParsing.xml").required("root");
      int nrOuter = 0;
      for (XMLCursor outer : root.multiple("outer")) {
         ++nrOuter;
         int nrInner = 0;
         for (XMLCursor inner : outer.multiple("inner")) {
            ++nrInner;
            inner.getLocation();
         }
         assertEquals(3, nrInner);
      }
      assertEquals(2, nrOuter);

   }

   public void testAll() throws Exception {
      XMLCursor root = getTestXML("XMLCursor.simpleParsing.xml").required("root");
      int nrOuter = 0;
      for (XMLCursor outer : root.all()) {
         ++nrOuter;
         int nrInner = 0;
         for (XMLCursor inner : outer.all()) {
            ++nrInner;
            inner.getLocation();
         }
         assertEquals(3, nrInner);
      }
      assertEquals(2, nrOuter);
   }

   public void testSkipTo() throws Exception {
      XMLCursor root = getTestXML("XMLCursor.skipTo.xml").required("root");
      final XMLCursor outer1 = root.required("outer");
      assertEquals("a", outer1.skipTo("a").getLocalName());
      assertEquals("c", outer1.skipTo("c").getLocalName());
      final XMLCursor outer2 = root.required("outer");
      assertEquals("e", outer2.skipTo("e").getLocalName());
       try {
          outer2.skipTo("e");
          fail();
       } catch (XMLStreamException ex) {
       }
   }

   private XMLCursor getTestXML(String name) throws XMLStreamException {
      return new XMLCursor(getClass().getResourceAsStream(name), name);
   }
}
