package com.googlecode.e2u;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
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
import org.daisy.dotify.common.text.ConditionalMapper;
import org.daisy.dotify.common.text.SimpleUCharReplacer;
import org.xml.sax.Locator;

import com.googlecode.e2u.l10n.L10nKeys;
import com.googlecode.e2u.l10n.Messages;

import shared.Settings;
import shared.Settings.Keys;


//TODO: row height calculation
public class StaxPreview {
	private static final String PEF_NS = "http://www.daisy.org/ns/2008/pef";
	private static final QName VOLUME = new QName(PEF_NS, "volume");
	private static final QName SECTION = new QName(PEF_NS, "section");
	private static final QName PAGE = new QName(PEF_NS, "page");
	private static final QName ROW = new QName(PEF_NS, "row");
	private static final String HTML_NS = "http://www.w3.org/1999/xhtml";
	
	private static final StringParser<Integer> toInt = (value) -> Integer.parseInt(value);
	private static final StringParser<Boolean> toBoolean = (value) -> Boolean.parseBoolean(value);
	
	@FunctionalInterface
	private static interface StringParser<T> {
		T toObject(String in);
	}
	
	static final ConditionalMapper NUMBER_PROCESSOR = ConditionalMapper.withTrigger('⠼')
													.map("⠚⠁⠃⠉⠙⠑⠋⠛⠓⠊", "0123456789")
													.putIgnorable('⠄')
													.putIgnorable('⠂')
													.build();
	
	private final List<File> volumes;
	private final PEFBook book;
	private final XMLOutputFactory outFactory;
	private final SimpleUCharReplacer cr;
	private int pageNumber;
	private boolean abort;
	private Locator locator;
	private XMLStreamWriter out;
	private boolean isProcessing;

	public StaxPreview(PEFBook book) {
		this.book = book;
		this.volumes = new ArrayList<>();
		this.outFactory = XMLOutputFactory.newInstance();
		this.pageNumber = 0;
		this.abort = false;
		this.isProcessing = false;
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
	
	private static class ParsingCancelledException extends Exception {
		
	}
	
	public void staxParse() throws MalformedURLException, XMLStreamException, IOException {
		isProcessing = true;
		try {
			XMLInputFactory inFactory = XMLInputFactory.newInstance();
			XMLEventReader input = inFactory.createXMLEventReader(book.getURI().toURL().openStream());
			XMLEvent event;
			int volNumber = 0;
			while (input.hasNext()) {
				event = input.nextEvent();
				if (abort) { throw new ParsingCancelledException(); }
				if (event.isStartElement() && event.asStartElement().getName().equals(VOLUME)) {
					volNumber++;
					parseVolume(event, input, volNumber);
				}
			}
		} catch (ParsingCancelledException e) {
			// nothing to do
		} finally {
			isProcessing = false;
		}
	}
	
	private void parseVolume(XMLEvent event, XMLEventReader input, int volNumber) throws XMLStreamException, IOException, ParsingCancelledException {
		File t1 = File.createTempFile("Preview", ".tmp");
		t1.deleteOnExit();
		volumes.add(t1);
		OutputStream outStream = new FileOutputStream(t1);
		try {
			out = outFactory.createXMLStreamWriter(new OutputStreamWriter(outStream, "utf-8"));
			out.setDefaultNamespace(HTML_NS);
			writePreamble(volNumber);
			Context props = parseProps(event, null);
			int sectionNumber = 0;
			while (input.hasNext()) {
				event = input.nextEvent();
				if (abort) { throw new ParsingCancelledException(); }
				if (event.isStartElement() && SECTION.equals(event.asStartElement().getName())) {
					sectionNumber++;
					parseSection(event, input, volNumber, sectionNumber, props);
				} else if (event.isEndElement() && VOLUME.equals(event.asEndElement().getName())) {
					break;
				}
			}
			writePostamble();
		} finally {
			outStream.close();
		}
	}
	
	private void parseSection(XMLEvent event, XMLEventReader input, int volumeNumber, int sectionNumber, Context inherit) throws XMLStreamException, IOException, ParsingCancelledException {
		if (pageNumber % 2 == 1) {
			pageNumber++;
		}
		writeSectionPreamble(volumeNumber, sectionNumber);
		Context props = parseProps(event, inherit);
		boolean firstPage = true;
		while (input.hasNext()) {
			event = input.nextEvent();
			if (abort) { throw new ParsingCancelledException(); }
			if (event.isStartElement() && PAGE.equals(event.asStartElement().getName())) {
				parsePage(event, input, sectionNumber, props, firstPage);
				firstPage = false;
			} else if (event.isEndElement() && SECTION.equals(event.asEndElement().getName())) {
				break;
			}
		}
		writeSectionPostamble();
	}
	
	private Context parseProps(XMLEvent event, Context defaults) {
		int rows = getAttribute(event, "rows", defaults==null?0:defaults.rows, toInt);
		int cols = getAttribute(event, "cols", defaults==null?0:defaults.cols, toInt);
		int rowgap = getAttribute(event, "rowgap", defaults==null?0:defaults.rowgap, toInt);
		boolean duplex = getAttribute(event, "duplex", defaults==null?true:defaults.duplex, toBoolean);
		return new Context(rows, cols, duplex, rowgap);
	}
	
	private void parsePage(XMLEvent event, XMLEventReader input, int sectionNumber, Context inherit, boolean firstPage) throws XMLStreamException, IOException, ParsingCancelledException {
		Context props = parseProps(event, inherit);
		pageNumber += props.duplex?1:2;
		writePagePreamble(pageNumber, sectionNumber, firstPage);
		ArrayList<Row> rows = new ArrayList<>();
		while (input.hasNext()) {
			event = input.nextEvent();
			if (abort) { throw new ParsingCancelledException(); }
			if (event.isStartElement() && ROW.equals(event.asStartElement().getName())) {
				rows.add(parseRow(event, input, props));
			} else if (event.isEndElement() && PAGE.equals(event.asEndElement().getName())) {
				break;
			}
		}
		writeRows(rows, true, props.cols, props.rows);
		writeRows(rows, false, props.cols, props.rows);
		writePagePostamble();
	}
	
	private void writeRows(List<Row> rows, boolean braille, int width, int height) throws XMLStreamException {
		out.writeStartElement(HTML_NS, "div");
		if (braille) {
			out.writeAttribute("class", "page");
		} else {
			out.writeAttribute("class", "text");
		}
		out.writeStartElement(HTML_NS, "table");
		for (Row r : rows) {
			writeRowPreamble(braille);
			String s;
			if (braille) {
				s = r.chars;
			} else {
				s = cr.replace(NUMBER_PROCESSOR.replace(r.chars)).toString();
			}
			out.writeCharacters(s);
			int fill = width-s.length();
			for (int i=0; i<fill; i++) {
				if (braille) {
					out.writeCharacters("\u2800");
				} else {
					out.writeEntityRef("nbsp");
				}
			}
			writeRowPostamble();
		}
		for (int i=0; i<height-rows.size(); i++) {
			writeRowPreamble(braille);
			out.writeEntityRef("nbsp");
			writeRowPostamble();			
		}
		out.writeEndElement();
		out.writeCharacters("\n");
		out.writeEndElement();
		out.writeCharacters("\n");
	}
	
	private void writeRowPreamble(boolean braille) throws XMLStreamException {
		out.writeStartElement(HTML_NS, "tr");
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "td");
		if (braille) {
			out.writeAttribute("class", "braille");
		} else {
			out.writeAttribute("class", "text");
		}
		out.writeAttribute("style", "height: 26px;"); //TODO: height calculation
	}
	
	private void writeRowPostamble() throws XMLStreamException {
		out.writeEndElement();
		out.writeCharacters("\n");
		out.writeEndElement();
		out.writeCharacters("\n");
	}
	
	private Row parseRow(XMLEvent event, XMLEventReader input, Context inherit) throws XMLStreamException, ParsingCancelledException {
		Context props = parseProps(event, inherit);
		StringBuilder chars = new StringBuilder();
		while (input.hasNext()) {
			event = input.nextEvent();
			if (abort) { throw new ParsingCancelledException(); }
			if (event.isCharacters()) {
				chars.append(event.asCharacters().getData());
			} else if (event.isEndElement() && ROW.equals(event.asEndElement().getName())) {
				return new Row(chars.toString(), props.rowgap);
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
	
	private static class Context {
		private final int rows;
		private final int cols;
		private final boolean duplex;
		protected final  int rowgap;

		Context(int rows, int cols, boolean duplex, int rowgap) {
			this.rows = rows;
			this.cols = cols;
			this.duplex = duplex;
			this.rowgap = rowgap;
		}
	}

	private void writePreamble(int volNumber) throws XMLStreamException {
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
		out.writeAttribute("class", "preview");
		out.writeCharacters("\n");
		
		out.writeStartElement(HTML_NS, "div");
		out.writeAttribute("id", "view");
		writeCloseForm();
		writeNavigation(volNumber);
		out.writeEndElement();

		writeAbout();

		out.writeStartElement(HTML_NS, "div");
		out.writeAttribute("class", "volume");
		out.writeAttribute("id", toSectionId(volNumber, 0));
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "p");
		out.writeAttribute("class", "volume-header");
		out.writeCharacters(Messages.getString(L10nKeys.XSLT_VOLUME_LABEL) + " " + volumes.size() + " (" + book.getSheets(volumes.size()) + " " + Messages.getString(L10nKeys.XSLT_SHEETS_LABEL) + ")");
		
		out.writeEndElement();
		out.writeCharacters("\n");
		
	}
	
	private void writeCloseForm() throws XMLStreamException {
		out.writeStartElement(HTML_NS, "form");
		out.writeAttribute("action", "close.html");
		out.writeAttribute("method", "get");
		out.writeCharacters("\n");
		
		out.writeStartElement(HTML_NS, "p");
		//<p><span id="item-emboss"><a href="/">Skriv ut</a></span>
		//<span><a href="view.html">Förhandsgranska</a></span>
		//<span><a href="index.html?method=meta">Om boken</a></span>
		//<input id="connected" type="submit" value="" title="Avsluta"></input>
		//<input id="notConnected" type="submit" value="" title="Avsluta" disabled="disabled"></input>
		//</p>
		out.writeStartElement(HTML_NS, "span");
		out.writeAttribute("id", "item-emboss");
		out.writeStartElement(HTML_NS, "a");
		out.writeAttribute("href", "/");
		out.writeCharacters("Skriv ut");
		out.writeEndElement();
		out.writeEndElement();
		
		out.writeStartElement(HTML_NS, "span");
		out.writeStartElement(HTML_NS, "a");
		out.writeAttribute("href", "view.html");
		out.writeCharacters(Messages.getString(L10nKeys.PREVIEW_VIEW));
		out.writeEndElement();
		out.writeEndElement();

		out.writeStartElement(HTML_NS, "span");
		out.writeStartElement(HTML_NS, "a");
		out.writeAttribute("href", "index.html?method=meta");
		out.writeCharacters(Messages.getString(L10nKeys.XSLT_ABOUT_LABEL));
		out.writeEndElement();
		out.writeEndElement();
		
		out.writeStartElement(HTML_NS, "input");
		out.writeAttribute("id", "connected");
		out.writeAttribute("type", "submit");
		out.writeAttribute("value", "");
		out.writeAttribute("title", "Avsluta"); //TODO: localize
		out.writeCharacters("");
		out.writeEndElement();
		
		out.writeStartElement(HTML_NS, "input");
		out.writeAttribute("id", "notConnected");
		out.writeAttribute("type", "submit");
		out.writeAttribute("value", "");
		out.writeAttribute("title", "Avsluta"); //TODO: localize
		out.writeAttribute("disabled", "disabled");
		out.writeCharacters("");
		out.writeEndElement();
	
		out.writeEndElement();
	
		out.writeEndElement();
		out.writeCharacters("\n");
	}
	
	private void writeNavigation(int volNumber) throws XMLStreamException {
		out.writeStartElement(HTML_NS, "div");
		out.writeAttribute("id", "top-nav");
		out.writeCharacters("\n");
		
		
		out.writeStartElement(HTML_NS, "p");
		out.writeStartElement(HTML_NS, "span");
		
		out.writeStartElement(HTML_NS, "a");
		out.writeAttribute("href", "#");
		out.writeAttribute("onclick", "toggleViews();return false;");
		out.writeAttribute("accesskey", "V"); //TODO: is this correct?
		out.writeCharacters(Messages.getString(L10nKeys.XSLT_TOGGLE_VIEW));
		out.writeEndElement();
		
		out.writeEndElement();
		
		out.writeStartElement(HTML_NS, "span");
		out.writeStartElement(HTML_NS, "select");
		out.writeAttribute("onchange", "location = this.options[this.selectedIndex].value;");
		out.writeAttribute("id", "volume-select");
		out.writeAttribute("class", "chosen-select");
		
		for (int i=1; i<=book.getVolumes(); i++) {
			out.writeCharacters("\n");
			out.writeStartElement(HTML_NS, "option");
			out.writeAttribute("value", "view.html?book.xml&volume="+(i));
			out.writeAttribute("title", "("+book.getSheets(i) + " " + Messages.getString(L10nKeys.XSLT_SHEETS_LABEL) + ")");
			if (i==volNumber) {
				out.writeAttribute("selected", "selected");
			}
			out.writeCharacters(Messages.getString(L10nKeys.XSLT_VOLUME_LABEL) + " " + i);
			out.writeEndElement();
			for (int j=1; j<=book.getSectionsInVolume(i); j++) {
				out.writeCharacters("\n");
				out.writeStartElement(HTML_NS, "option");
				out.writeAttribute("value", "view.html?book.xml&volume="+(i)+"#"+toSectionId(i, j));
				out.writeAttribute("title", "("+book.getSheets(i, j) + " " + Messages.getString(L10nKeys.XSLT_SHEETS_LABEL) + ")");
				out.writeEntityRef("nbsp");
				out.writeEntityRef("nbsp");
				out.writeEntityRef("nbsp");
				out.writeCharacters(Messages.getString(L10nKeys.XSLT_SECTION_LABEL) + " " + j);
				out.writeEndElement();
			}
		}
				
		out.writeEndElement();
		out.writeEndElement();
		
		out.writeStartElement(HTML_NS, "span");
		out.writeCharacters(Messages.getString(L10nKeys.XSLT_GO_TO_PAGE_LABEL));
		out.writeStartElement(HTML_NS, "input");
		out.writeAttribute("id", "gotoPage");
		out.writeAttribute("type", "text");
		out.writeAttribute("size", "4");
		out.writeAttribute("onkeyup", "if (event.keyCode==13) {gotoPage();}");
		out.writeAttribute("value", "1");
		out.writeCharacters("");
		out.writeEndElement();
		out.writeEndElement();
		
		out.writeEndElement();
		
		out.writeEndElement();
		out.writeCharacters("\n");
	}
	
	private static String toSectionId(int volume, int section) {
		return "sectionId-"+volume+"-"+section;
	}
	
	private void writeAbout() throws XMLStreamException {
		out.writeStartElement(HTML_NS, "div");
		out.writeAttribute("id", "about");
		out.writeCharacters("\n");
		
		out.writeStartElement(HTML_NS, "p");
		out.writeAttribute("id", "close-bar");

		out.writeStartElement(HTML_NS, "input");
		out.writeAttribute("type", "button");
		out.writeAttribute("onclick", "document.getElementById('about').style.visibility='hidden';");
		out.writeAttribute("value", "X");
		out.writeCharacters("");
		out.writeEndElement();

		out.writeEndElement();
		out.writeCharacters("\n");

		out.writeStartElement(HTML_NS, "div");
		out.writeAttribute("id", "about-content");
		out.writeCharacters("\n");
		
		out.writeStartElement(HTML_NS, "p");
		for (String s : orEmpty(book.getMetadata("identifier"))) {
			out.writeCharacters(s);
			break;
		}
		
		for (String s : orEmpty(book.getMetadata("source"))) {
			out.writeCharacters(" ("+s+")");
			break;
		}

		out.writeEndElement();
		out.writeCharacters("\n");
		
		out.writeStartElement(HTML_NS, "p");
		out.writeStartElement(HTML_NS, "strong");
		for (String s : orEmpty(book.getTitle())) { //TODO: unknown title
			out.writeCharacters(s);
			break;
		}
		out.writeEndElement();
		out.writeStartElement(HTML_NS, "br");
		out.writeEndElement();
		out.writeStartElement(HTML_NS, "strong");
		boolean first = true;
		for (String s : orEmpty(book.getAuthors())) { //TODO: unknown author
			out.writeCharacters(s);
			if (first) {
				first = false;
			} else {
				out.writeCharacters("; ");
			}
		}
		out.writeEndElement();
		out.writeCharacters("");
		out.writeEndElement();
		out.writeCharacters("\n");

		for (String s : orEmpty(book.getMetadata("date"))) {
			out.writeStartElement(HTML_NS, "p");
			out.writeCharacters(s);
			out.writeEndElement();
			out.writeCharacters("\n");
		}
		
		out.writeStartElement(HTML_NS, "p");
		out.writeCharacters(Messages.getString(L10nKeys.XSLT_SHOWING_PAGES) + ": " + 
							book.getFirstPage(volumes.size()) + "-" + book.getLastPage(volumes.size()));

		out.writeEndElement();
		out.writeCharacters("\n");

		out.writeEndElement();
		out.writeCharacters("\n");

		out.writeEndElement();
		out.writeCharacters("\n");		
	}
	
	private static <T> Iterable<T> orEmpty(Iterable<T> s) {
		return s==null?Collections.emptyList():s;
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
	
	private void writeSectionPreamble(int volumeNumber, int sectionNumber) throws XMLStreamException {
		out.writeStartElement(HTML_NS, "div");
		out.writeAttribute("class", "section");
		out.writeAttribute("id", toSectionId(volumeNumber, sectionNumber));
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "p");
		out.writeAttribute("class", "section-header");
		out.writeCharacters(Messages.getString(L10nKeys.XSLT_SECTION_LABEL) + " " + sectionNumber 
				+ " (" + book.getSheets(volumeNumber, sectionNumber) 
				+ " " + Messages.getString(L10nKeys.XSLT_SHEETS_LABEL) + ")");
		out.writeEndElement();
		out.writeCharacters("\n");
	}
	
	private void writeSectionPostamble() throws XMLStreamException {
		out.writeEndElement();
		out.writeCharacters("\n");
	}
	
	private void writePagePreamble(int pageNumber, int sectionNumber, boolean firstPage) throws XMLStreamException {
		out.writeStartElement(HTML_NS, "div");
		//out.writeAttribute("id", "");
		out.writeAttribute("onmouseover", "setPage("+pageNumber+");");
		out.writeAttribute("class", "cont " + (firstPage?"first":pageNumber%2==0?"even":"odd"));
		out.writeCharacters("\n");
		out.writeStartElement(HTML_NS, "p");
		out.writeAttribute("class", "page-header");
		out.writeAttribute("id", "pagenum"+pageNumber);
		out.writeCharacters(Messages.getString(L10nKeys.XSLT_VOLUME_LABEL) + 
							" " + volumes.size() + 
							", " +
							Messages.getString(L10nKeys.XSLT_SECTION_LABEL) +
							" " + sectionNumber + 
							" | " + 
							Messages.getString(L10nKeys.XSLT_PAGE_LABEL) + 
							" " + pageNumber);
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
	
	public int getSize() {
		return volumes.size();
	}
	
	public List<File> getVolumes() {
		return Collections.unmodifiableList(volumes);
	}
	
	/**
	 * Stops processing and deletes temporary files.
	 */
	public void abort() {
		abort = true;
		while (isProcessing) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		for (File f : volumes) {
			//delete files
			f.delete();
		}
	}

}