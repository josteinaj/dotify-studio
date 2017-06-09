package com.googlecode.e2u;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.daisy.braille.pef.PEFBook;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class SaxPreview extends DefaultHandler {
	private static final String HTML_NS = "http://www.w3.org/1999/xhtml";
	private static final String VOLUME_HEADER = "Volym {0} ({1})";
	private final List<File> volumes;
	private final PEFBook book;
	private final XMLOutputFactory outFactory;
	private Locator locator;
	private XMLStreamWriter out;

	public SaxPreview(PEFBook book) {
		this.book = book;
		this.volumes = new ArrayList<>();
		this.outFactory = XMLOutputFactory.newInstance();
	}

	@Override
	public InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
		// TODO Auto-generated method stub
		return super.resolveEntity(publicId, systemId);
	}

	@Override
	public void notationDecl(String name, String publicId, String systemId) throws SAXException {
		// TODO Auto-generated method stub
		super.notationDecl(name, publicId, systemId);
	}

	@Override
	public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName)
			throws SAXException {
		// TODO Auto-generated method stub
		super.unparsedEntityDecl(name, publicId, systemId, notationName);
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		super.setDocumentLocator(locator);
		this.locator = locator;
	}

	@Override
	public void startDocument() throws SAXException {
		// TODO Auto-generated method stub
		super.startDocument();
	}

	@Override
	public void endDocument() throws SAXException {
		// TODO Auto-generated method stub
		super.endDocument();
	}

	@Override
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		// TODO Auto-generated method stub
		super.startPrefixMapping(prefix, uri);
	}

	@Override
	public void endPrefixMapping(String prefix) throws SAXException {
		// TODO Auto-generated method stub
		super.endPrefixMapping(prefix);
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		super.startElement(uri, localName, qName, attributes);
		try {
			if ("volume".equals(localName)) {
				File t1 = File.createTempFile("Preview", ".tmp");
				t1.deleteOnExit();
				volumes.add(t1);
				out = outFactory.createXMLStreamWriter(new OutputStreamWriter(new FileOutputStream(t1), "utf-8"));
				out.setDefaultNamespace(HTML_NS);
				writePreamble();
			} else if ("section".equals(localName)) {
				writeSectionPreamble();
			} else if ("page".equals(localName)) {
				writePagePreamble();
			}
		} catch (IOException | XMLStreamException e) {
			throw new SAXException(e);
		}

	}
	
	private void writePreamble() throws XMLStreamException {
		out.writeDTD("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "html");
		out.writeDefaultNamespace(HTML_NS);
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "head");
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "title");
		Iterable<String> it = book.getMetadata("identifier");
		String title = "";
		for (String s : it) {
			title = s;
			break;
		}
		out.writeCharacters("Visar " + title);
		out.writeEndElement();
		out.writeCharacters("\n");
		
		out.writeStartElement(HTML_NS, "meta");
		out.writeAttribute("http-equiv", "Content-Type");
		out.writeAttribute("content", "text/html; charset=UTF-8");
		out.writeEndElement();
		out.writeCharacters("\n");
		
		out.writeStartElement(HTML_NS, "meta");
		out.writeAttribute("http-equiv", "Content-Style-Type");
		out.writeAttribute("content", "text/css");
		out.writeEndElement();
		out.writeCharacters("\n");
		
		writeCssLink("styles/default/base.css");
		writeCssLink("styles/default/layout.css");
		writeCssLink("styles/default/theme.css");
		writeCssLink("styles/default/state.css");
		writeCssLink("chosen/chosen.css");
		
		out.writeStartElement(HTML_NS, "script");
		out.writeAttribute("src", "script/shortcuts.js");
		out.writeCharacters("");
		out.writeEndElement();
		out.writeCharacters("\n");
		
		out.writeStartElement(HTML_NS, "script");
		out.writeAttribute("src", "script/preview.js");
		out.writeCharacters("");
		out.writeEndElement();
		out.writeCharacters("\n");
		
		out.writeEndElement();
		out.writeCharacters("\n");
		
		out.writeStartElement(HTML_NS, "body");
		out.writeCharacters("\n");
		//TODO: add navigation
		
		out.writeStartElement(HTML_NS, "div");
		out.writeAttribute("class", "volume");
		//out.writeAttribute("id", "");
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "p");
		out.writeAttribute("class", "volume-header");
		out.writeCharacters(MessageFormat.format(VOLUME_HEADER, volumes.size(), book.getSheets(volumes.size())));
		
		out.writeEndElement();
		out.writeCharacters("\n");
		
	}
	
	private void writeCssLink(String value) throws XMLStreamException {
		out.writeStartElement(HTML_NS, "link");
		out.writeAttribute("rel", "stylesheet");
		out.writeAttribute("type", "text/css");
		out.writeAttribute("href", value);
		out.writeCharacters("");
		out.writeEndElement();
		out.writeCharacters("\n");
	}
	
	private void writePostamble() throws XMLStreamException {
		out.writeEndElement();
		out.writeCharacters("\n");
		out.writeEndElement();
		out.writeCharacters("\n");
		out.writeEndElement();
		out.writeEndDocument();
	}
	
	private void writeSectionPreamble() throws XMLStreamException {
		out.writeStartElement(HTML_NS, "div");
		out.writeAttribute("class", "section");
		//out.writeAttribute("id", "");
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "p");
		out.writeAttribute("class", "section-header");
		out.writeEndElement();
		out.writeCharacters("\n");
	}
	
	private void writeSectionPostamble() throws XMLStreamException {
		out.writeEndElement();
		out.writeCharacters("\n");
	}
	
	private void writePagePreamble() throws XMLStreamException {
		out.writeStartElement(HTML_NS, "div");
		//out.writeAttribute("id", "");
		out.writeAttribute("onmouseover", "setPage(1);"); //TODO: page number
		out.writeAttribute("class", "cont"); //TODO: first, even
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "p");
		out.writeAttribute("class", "page-header");
		//out.writeAttribute("id", "");
		out.writeCharacters("Vol 1, Avs 1 | Sida x"); //TODO: variables
		out.writeEndElement();
		out.writeCharacters("\n");
		
		out.writeStartElement(HTML_NS, "div");
		out.writeAttribute("class", "posrel");
		out.writeCharacters("\n");
	
	}
	
	private void writePagePostamble() throws XMLStreamException {
		out.writeEndElement();
		out.writeCharacters("\n");
		
		out.writeEndElement();
		out.writeCharacters("\n");
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		super.endElement(uri, localName, qName);
		try {
			if ("volume".equals(localName)) {
				System.out.println(volumes.size());
				writePostamble();
				out.close();
				out = null;
			} else if ("section".equals(localName)) {
				writeSectionPostamble();
			} else if ("page".equals(localName)) {
				writePagePostamble();
			}
		} catch (XMLStreamException e) {
			throw new SAXException(e);
		}
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub
		super.characters(ch, start, length);
	}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub
		super.ignorableWhitespace(ch, start, length);
	}

	@Override
	public void processingInstruction(String target, String data) throws SAXException {
		// TODO Auto-generated method stub
		super.processingInstruction(target, data);
	}

	@Override
	public void skippedEntity(String name) throws SAXException {
		// TODO Auto-generated method stub
		super.skippedEntity(name);
	}

	@Override
	public void warning(SAXParseException e) throws SAXException {
		// TODO Auto-generated method stub
		super.warning(e);
	}

	@Override
	public void error(SAXParseException e) throws SAXException {
		// TODO Auto-generated method stub
		super.error(e);
	}

	@Override
	public void fatalError(SAXParseException e) throws SAXException {
		// TODO Auto-generated method stub
		super.fatalError(e);
	}
	
	public List<File> getVolumes() {
		return Collections.unmodifiableList(volumes);
	}

}
