kandidlib-xml
=================

A small Java library that turns StAX parsing from pain into pleasure.

The problem
---------
StAX parsing has several advantages:
* the pull principle makes program structure simple
* the stream principle saves memory
* the StAX principle makes things fast

So far the benefits. On the back side are
* lots of boiler plate code
* lots of boiler plate code
* lots of boiler plate code

The solution
---------

The class `XMLCursor` tames the rough sides of StAX by introducing a thin layer. A small example may help to get the idea. Consider the following XML:
```xml
<library>
	<books>
		<book title="The Cyberiad">
			<author name="Stanislaw Lem">
			<abstract>A series of humorous short stories from a mechanical universe</abstract>
		</book>
		<book title="Monday Begins on Saturday">
			<author name="Arkady Strugatsky">
			<author name="Boris Strugatsky">
		</book>
	</books>
</library>
```

Parsing is easily done with some lines of Java:
```java
InputStream in = ...;
XMLCursor root = new XMLCursor(in, "Memory source").required("library");
for (XMLCursor book : root.required("books").multiple("book")) {
	ArrayList<String> authors = new ArrayList<>();
	for (XMLCursor author : book.multiple("author"))
		authors.add(author.getAttribute("name"));
	XMLCursor abstract = book.optional("abstract");
	if (abstract != null)
		abstract.getText();
}
```

How to use it
----------
Add this library to your classpath.

Building the kandidlib-xml.jar
---------------------------
This library uses the [Gradle](http://gradle.org)-1.10 or later build system. Since I refuse to add the wrapper to the source code, you need to have it installed. Then
```sh
gradle jar
```
produces the jar.

Improving kandidlib-xml
-------------
Of course any IDE may be used to work on kandidlib-xml but support for gradle makes it more convenient.
