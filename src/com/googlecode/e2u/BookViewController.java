package com.googlecode.e2u;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.daisy.braille.pef.PEFBook;

import shared.Settings;

public class BookViewController {
	private BookReader bookReader;
	private AboutBookView aboutBookView;
	private PreviewController2 controller;
	private Settings settings;
	private final MenuSystem menu;
	
	public BookViewController(File f, Settings settings, MenuSystem menu) {
		if (f==null) {
	    	try {
	    		bookReader = new BookReader("resource-files/book.pef");
	    	} catch (URISyntaxException e) {
	    		e.printStackTrace();
	    	}
		} else {
			bookReader = new BookReader(f);
		}
    	aboutBookView = null;
    	controller = null;
    	this.settings = settings;
    	this.menu = menu;
	}
	
	public URI getBookURI() {
    	return bookReader.getResult().getURI();
    }
    
    public PEFBook getBook() {
    	return bookReader.getResult().getBook();
    }
    
    public boolean bookIsValid() {
    	return bookReader.getResult().isValid();
    }
    
    public ValidationView getValidationView() {
    	return new ValidationView(bookReader.getResult().getValidator());
    }
    
    public AboutBookView getAboutBookView() {
    	if (aboutBookView==null) {
    		aboutBookView = new AboutBookView(bookReader.getResult().getBook(), menu);
    	}
    	return aboutBookView;
    }
    
    public PreviewController2 getPreviewView() {
    	if (controller==null) {
    		controller = new PreviewController2(bookReader, settings);
    	}
    	return controller;
    }
    
    public void close() {
    	bookReader.cancel();
    }

}
