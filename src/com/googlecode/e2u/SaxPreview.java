package com.googlecode.e2u;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import org.daisy.braille.api.table.BrailleConstants;
import org.daisy.braille.api.table.BrailleConverter;
import org.daisy.braille.api.table.Table;
import org.daisy.braille.consumer.table.TableCatalog;
import org.daisy.braille.impl.table.DefaultTableProvider;
import org.daisy.braille.pef.PEFBook;
import org.daisy.dotify.common.text.SimpleUCharReplacer;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import shared.Settings;
import shared.Settings.Keys;

public class SaxPreview {
	private static final String PEF_NS = "http://www.daisy.org/ns/2008/pef";
	private static final QName VOLUME = new QName(PEF_NS, "volume");
	private static final QName SECTION = new QName(PEF_NS, "section");
	private static final QName PAGE = new QName(PEF_NS, "page");
	private static final QName ROW = new QName(PEF_NS, "row");
	private static final String HTML_NS = "http://www.w3.org/1999/xhtml";
	private static final String VOLUME_HEADER = "Volym {0} ({1})";
	
	private static final StringParser<Integer> toInt = (value)->Integer.parseInt(value);
	
	@FunctionalInterface
	private static interface StringParser<T> {
		T toObject(String in);
	}
	
	private final List<File> volumes;
	private final PEFBook book;
	private final XMLOutputFactory outFactory;
	private final SimpleUCharReplacer cr;
	private Locator locator;
	private XMLStreamWriter out;

	public SaxPreview(PEFBook book) {
		this.book = book;
		this.volumes = new ArrayList<>();
		this.outFactory = XMLOutputFactory.newInstance();
		// Configures a char replacer instead of using braille converter directly in order to preserve 
		// the previous behavior of xpath function translate, in other words, keeping characters with  
		// no translation. This is not supported by the braille converter.
		this.cr = new SimpleUCharReplacer();
		BrailleConverter bc = getTable().newBrailleConverter();
		if (bc.supportsEightDot()) {
			String input = BrailleConstants.BRAILLE_PATTERNS_256;
			String tr = bc.toText(input);
			for (int i=0; i<tr.length(); i++) {
				cr.put((int)input.charAt(i), ""+tr.charAt(i));
			}
		} else {
			String input = BrailleConstants.BRAILLE_PATTERNS_64;
			String tr = bc.toText(input);
			for (int i=0; i<tr.length(); i++) {
				cr.put((int)input.charAt(i), ""+tr.charAt(i));
			}
		}
	}

	private static Table getTable() {
		String charset = Settings.getSettings().getString(Keys.charset);
		Table table = null;
		if (charset!=null) { 
			table = TableCatalog.newInstance().get(charset);
		}
		if (table==null) {
			table = TableCatalog.newInstance().get(DefaultTableProvider.class.getCanonicalName()+".TableType.EN_US");
			Settings.getSettings().getSetPref(Keys.charset, table.getIdentifier());
		}
		return table;
	}
	
	public void staxParse() throws MalformedURLException, XMLStreamException, IOException {
		XMLInputFactory inFactory = XMLInputFactory.newInstance();
		XMLEventReader input = inFactory.createXMLEventReader(book.getURI().toURL().openStream());
		XMLEvent event;
		while (input.hasNext()) {
			event = input.nextEvent();
			if (event.isStartElement() && event.asStartElement().getName().equals(VOLUME)) {
				parseVolume(event, input);
			}
		}
	}
	
	private void parseVolume(XMLEvent event, XMLEventReader input) throws XMLStreamException, IOException {
		File t1 = File.createTempFile("Preview", ".tmp");
		t1.deleteOnExit();
		volumes.add(t1);
		out = outFactory.createXMLStreamWriter(new OutputStreamWriter(new FileOutputStream(t1), "utf-8"));
		out.setDefaultNamespace(HTML_NS);
		writePreamble();
		int sectionNumber = 0;
		while (input.hasNext()) {
			event = input.nextEvent();
			if (event.isStartElement() && SECTION.equals(event.asStartElement().getName())) {
				sectionNumber++;
				parseSection(event, input, sectionNumber);
			} else if (event.isEndElement() && VOLUME.equals(event.asEndElement().getName())) {
				break;
			}
		}
		writePostamble();
	}
	
	private void parseSection(XMLEvent event, XMLEventReader input, int sectionNumber) throws XMLStreamException, IOException {
		writeSectionPreamble();
		while (input.hasNext()) {
			event = input.nextEvent();
			if (event.isStartElement() && PAGE.equals(event.asStartElement().getName())) {
				parsePage(event, input, sectionNumber);
			} else if (event.isEndElement() && SECTION.equals(event.asEndElement().getName())) {
				break;
			}
		}
		writeSectionPostamble();
	}
	
	private void parsePage(XMLEvent event, XMLEventReader input, int sectionNumber) throws XMLStreamException, IOException {
		writePagePreamble(sectionNumber);
		ArrayList<Row> rows = new ArrayList<>();
		Row current;
		while (input.hasNext()) {
			event = input.nextEvent();
			if (event.isStartElement() && ROW.equals(event.asStartElement().getName())) {
				rows.add(parseRow(event, input));
			} else if (event.isEndElement() && PAGE.equals(event.asEndElement().getName())) {
				break;
			}
		}
		// TODO: write text
		writeRows(rows, true);
		writeRows(rows, false);
		writePagePostamble();
	}
	
	private void writeRows(List<Row> rows, boolean braille) throws XMLStreamException {
		if (!rows.isEmpty()) {
			out.writeStartElement(HTML_NS, "div");
			if (braille) {
				out.writeAttribute("class", "page");
			} else {
				out.writeAttribute("class", "text");
			}
			out.writeStartElement(HTML_NS, "table");
			for (Row r : rows) {
				out.writeStartElement(HTML_NS, "tr");
				out.writeCharacters("\n");
				out.writeStartElement(HTML_NS, "td");
				if (braille) {
					out.writeAttribute("class", "braille");
				} else {
					out.writeAttribute("class", "text");
				}
				out.writeAttribute("style", "height: 26px;"); //TODO: height calculation
				if (braille) {
					out.writeCharacters(r.chars);
				} else {
					out.writeCharacters(cr.replace(r.chars).toString());
				}
				out.writeEndElement();
				out.writeCharacters("\n");
				out.writeEndElement();
				out.writeCharacters("\n");
			}
			out.writeEndElement();
			out.writeCharacters("\n");
			out.writeEndElement();
			out.writeCharacters("\n");
		}
	}
	
	private Row parseRow(XMLEvent event, XMLEventReader input) throws XMLStreamException {
		StringBuilder chars = new StringBuilder();
		int rowgap = getAttribute(event, "rowgap", 0, toInt); //TODO: inherit
		while (input.hasNext()) {
			event = input.nextEvent();
			if (event.isCharacters()) {
				chars.append(event.asCharacters().getData());
			} else if (event.isEndElement() && ROW.equals(event.asEndElement().getName())) {
				return new Row(chars.toString(), rowgap);
			}
		}
		return null;
	}
	
	private static <T> T getAttribute(XMLEvent event, String name, T def, StringParser<T> val) {
		 Attribute attr = event.asStartElement().getAttributeByName(new QName(name));
		 if (attr!=null) {
			 return val.toObject(attr.getValue());
		 } else {
			 return def;
		 }
	}
	
	private static class Row {
		private final String chars;
		private final int rowgap;
		
		private Row(String chars, int rowgap) {
			this.chars = chars;
			this.rowgap = rowgap;
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
	
	private void writeScripts() throws XMLStreamException {
		out.writeStartElement(HTML_NS, "script");
		out.writeAttribute("src", "chosen/jquery-1.6.4.min.js");
		out.writeAttribute("type", "text/javascript");
		out.writeCharacters("");
		out.writeEndElement();
		out.writeStartElement(HTML_NS, "script");
		out.writeAttribute("src", "chosen/chosen.jquery.js");
		out.writeAttribute("type", "text/javascript");
		out.writeCharacters("");
		out.writeEndElement();
		out.writeStartElement(HTML_NS, "script");
		out.writeAttribute("type", "text/javascript");
		out.writeCharacters(
			"\n	var config = {\n"+
			"	  '.chosen-select'           : {},\n"+
			"	  '.chosen-select-deselect'  : {allow_single_deselect:true},\n"+
			"	  '.chosen-select-no-single' : {disable_search_threshold:10},\n"+
			"	  '.chosen-select-no-results': {no_results_text:'Oops, nothing found!'},\n"+
			"	  '.chosen-select-width'     : {width:\"95%\"}\n"+
			"	}\n"+
			"	for (var selector in config) {\n"+
			"	  $(selector).chosen(config[selector]);\n"+
			"	}\n");
		out.writeEndElement();		
	}
	
	private void writePostamble() throws XMLStreamException {
		out.writeEndElement();
		out.writeCharacters("\n");
		writeScripts();
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
	
	private void writePagePreamble(int sectionNumber) throws XMLStreamException {
		out.writeStartElement(HTML_NS, "div");
		//out.writeAttribute("id", "");
		out.writeAttribute("onmouseover", "setPage(1);"); //TODO: page number
		out.writeAttribute("class", "cont"); //TODO: first, even
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "p");
		out.writeAttribute("class", "page-header");
		//out.writeAttribute("id", "");
		out.writeCharacters("Vol 1, Avs " + sectionNumber + " | Sida x"); //TODO: variables
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
	
	public List<File> getVolumes() {
		return Collections.unmodifiableList(volumes);
	}

}
