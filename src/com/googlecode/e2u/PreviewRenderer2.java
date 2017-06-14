package com.googlecode.e2u;

import java.io.File;
import java.util.Map;

import org.daisy.braille.pef.PEFBook;

import javafx.concurrent.Task;

public class PreviewRenderer2 {
	private final PEFBook book;
	private final Task<Void> t;
	private final StaxPreview pr;

	public PreviewRenderer2(PEFBook book) {
		this.book = book;
		this.pr = new StaxPreview(book);
		this.t = new Task<Void>() {

			@Override
			protected Void call() throws Exception {
				pr.staxParse();
				return null;
			}
		};
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
	}
	
	public void abort() {
		pr.abort();
	}

	public File getFile(int v) {
		if (v<1 || v>book.getVolumes()) {
			throw new IndexOutOfBoundsException();
		}
		try {
			while (pr.getSize()<v) {
				Thread.sleep(100);
			}
			return pr.getVolumes().get(v-1);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
