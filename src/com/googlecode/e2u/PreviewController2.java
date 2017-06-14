package com.googlecode.e2u;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;

import com.googlecode.e2u.BookReader.BookReaderResult;

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

}