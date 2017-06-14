package com.googlecode.e2u;

import java.io.File;
import java.util.List;

import org.daisy.braille.pef.PEFBook;

import javafx.concurrent.Task;

public class PreviewRenderer2 {
	private final StaxPreview parser;

	public PreviewRenderer2(PEFBook book) {
		this.parser = new StaxPreview(book);
		Task<Void> t = new Task<Void>() {

			@Override
			protected Void call() throws Exception {
				parser.staxParse();
				return null;
			}
		};
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
	}
	
	public void abort() {
		parser.abort();
	}

	public File getFile(int v) throws InterruptedException {
		if (v<1 || v>parser.getBook().getVolumes()) {
			throw new IndexOutOfBoundsException();
		}
		try {
			List<File> volumes = parser.getVolumes(); 
			while (volumes.size()<v) {
				Thread.sleep(100);
			}
			return volumes.get(v-1);
		} catch (InterruptedException e) {
			throw e;
		}
	}
}
