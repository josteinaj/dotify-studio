package com.googlecode.e2u;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.daisy.braille.api.table.BrailleConstants;
import org.daisy.braille.api.table.BrailleConverter;
import org.daisy.braille.api.table.Table;
import org.daisy.braille.consumer.table.TableCatalog;
import org.daisy.braille.impl.table.DefaultTableProvider;

import com.googlecode.ajui.Context;
import com.googlecode.e2u.BookReader.BookReaderResult;
import com.googlecode.e2u.l10n.L10nKeys;
import com.googlecode.e2u.l10n.Messages;

import shared.Settings;
import shared.Settings.Keys;

public class PreviewController2 {
	private static final Logger logger = Logger.getLogger(PreviewController2.class.getCanonicalName());
	private final BookReader r;
	private final Settings settings;
	private PreviewRenderer2 renderer;
	private boolean saxonNotAvailable;
	private String brailleFont, textFont, charset;
	private long lastUpdated;

	/**
	 * 
	 * @param r
	 * 
	 */
	public PreviewController2(final BookReader r, Settings settings) {
		this.settings = settings;
		this.r = r;
		update(false);
		brailleFont = settings.getString(Keys.brailleFont);
		textFont = settings.getString(Keys.textFont);
		charset = settings.getString(Keys.charset);
	}
	
	private void update(boolean force) {
		saxonNotAvailable = false;
		if (!force && lastUpdated+10000>System.currentTimeMillis()) {
			return;
		}
		lastUpdated = System.currentTimeMillis();
		try {
			BookReaderResult brr = r.getResult();
			Map<String, String> params = buildParams(settings, "view.html", settings.getString(Keys.charset), "book.xml", null);
			if (renderer!=null) {
				// abort rendering and delete files
				renderer.abort();
			}
			// set up new renderer
			renderer = new PreviewRenderer2(brr.getBook());
		} catch (IllegalArgumentException iae) { 
			saxonNotAvailable = true;
		}
	}
	
	private boolean settingsChanged() {
		String brailleFont = settings.getString(Keys.brailleFont);
		String textFont = settings.getString(Keys.textFont);
		String charset = settings.getString(Keys.charset);
		boolean changed = 
			(this.brailleFont!=null && !this.brailleFont.equals(brailleFont)) ||
			(this.textFont!=null && !this.textFont.equals(textFont)) ||
			(this.charset!=null && !this.charset.equals(charset));
		this.brailleFont = brailleFont;
		this.textFont = textFont;
		this.charset = charset;
		return changed;
	}
	
	private boolean fileChanged() {
		if (r.getResult().getBookFile()==null) {
			return false;
		} else {
			return lastUpdated<r.getResult().getBookFile().lastModified();
		}
	}

	public Reader getReader(int vol) {
		if (saxonNotAvailable) {
			return new StringReader("Failed to read");
		}
		try {
			boolean fileChanged = fileChanged();
			if (settingsChanged() || fileChanged) {
				update(fileChanged);
			}
			return new InputStreamReader(new FileInputStream(renderer.getFile(vol)), "UTF-8");
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			return new StringReader("Failed to read");
		}
	}

	private static Map<String, String> buildParams(Settings settings, String target, String charset, String file, String volume) {
		HashMap<String, String> params = new HashMap<>();
		if (file!=null) {
			Table table = null;
			if (charset!=null) { 
				table = TableCatalog.newInstance().get(charset);
			}
			if (table==null) {
				table = TableCatalog.newInstance().get(DefaultTableProvider.class.getCanonicalName()+".TableType.EN_US");
				settings.getSetPref(Keys.charset, table.getIdentifier());
			}
			params.put("uri", file);
			if (volume!=null && !"".equals(volume)) {
				params.put("volume", volume);
			} else {
				params.put("volume", "1");
			}
			BrailleConverter as = table.newBrailleConverter();
			if (as.supportsEightDot()) {
				params.put("code", as.toText(BrailleConstants.BRAILLE_PATTERNS_256));
			} else {
				params.put("code", as.toText(BrailleConstants.BRAILLE_PATTERNS_64));
			}
			params.put("toggle-view-label", Messages.getString(L10nKeys.XSLT_TOGGLE_VIEW));
			params.put("return-label", Messages.getString(L10nKeys.XSLT_RETURN_LABEL));
			params.put("emboss-view-label", Messages.getString(L10nKeys.EMBOSS_VIEW));
			params.put("preview-view-label", Messages.getString(L10nKeys.PREVIEW_VIEW));
			params.put("find-view-label", Messages.getString(L10nKeys.MENU_OPEN));
			params.put("setup-view-label", Messages.getString(L10nKeys.MENU_SETUP));
			params.put("about-software-label", Messages.getString(L10nKeys.MENU_ABOUT_SOFTWARE));
			params.put("unknown-author-label", Messages.getString(L10nKeys.UNKNOWN_AUTHOR));
			params.put("unknown-title-label", Messages.getString(L10nKeys.UNKNOWN_TITLE));
			params.put("showing-pages-label", Messages.getString(L10nKeys.XSLT_SHOWING_PAGES));
			params.put("about-label", Messages.getString(L10nKeys.XSLT_ABOUT_LABEL));
			params.put("show-source", Messages.getString(L10nKeys.XSLT_VIEW_SOURCE));
			params.put("volume-label", Messages.getString(L10nKeys.XSLT_VOLUME_LABEL));
			params.put("section-label", Messages.getString(L10nKeys.XSLT_SECTION_LABEL));
			params.put("page-label", Messages.getString(L10nKeys.XSLT_PAGE_LABEL));
			params.put("sheets-label", Messages.getString(L10nKeys.XSLT_SHEETS_LABEL));
			params.put("viewing-label", Messages.getString(L10nKeys.XSLT_VIEWING_LABEL));
			params.put("go-to-page-label", Messages.getString(L10nKeys.XSLT_GO_TO_PAGE_LABEL));
			params.put("uri-string", target + "?file=" + file + "&charset=" + charset);
			try {
				params.put("brailleFont", URLDecoder.decode(settings.getString(Keys.brailleFont, ""), MainPage.ENCODING));
				params.put("textFont", URLDecoder.decode(settings.getString(Keys.textFont, ""), MainPage.ENCODING));
			} catch (UnsupportedEncodingException e) {
				// should never happen if encoding is UTF-8
			}
		}
		return params;
	}
}