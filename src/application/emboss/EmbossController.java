package application.emboss;


import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.daisy.braille.api.embosser.Embosser;
import org.daisy.braille.api.embosser.EmbosserWriter;
import org.daisy.braille.pef.PEFBook;
import org.daisy.braille.pef.PEFConverterFacade;
import org.daisy.braille.pef.PEFHandler;
import org.daisy.braille.pef.PEFHandler.Alignment;
import org.daisy.braille.pef.PrinterDevice;
import org.daisy.braille.pef.Range;

import application.l10n.Messages;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import shared.Configuration;
import shared.Settings;
import shared.Settings.Keys;

public class EmbossController {
	@FXML private VBox titles;
	@FXML private Label title;
	@FXML private Label author;
	@FXML private Label dimensions;
	@FXML private Button ok;
	@FXML private Button cancel;
	@FXML private RadioButton documentRadio;
	@FXML private RadioButton volumesRadio;
	@FXML private RadioButton pagesRadio;
	@FXML private ToggleGroup range;
	@FXML private TextField volumes;
	@FXML private TextField pages;
	@FXML private Spinner<?> copies;
	private static final String ENCODING = "utf-8";
	private static final Logger logger = Logger.getLogger(EmbossController.class.getCanonicalName());
	private PEFBook book;
	private ExecutorService exeService;
	enum Scope {
		DOCUMENT,
		VOLUME,
		PAGES
	}
	private Scope scope;
	
	public EmbossController() {
		exeService = Executors.newWorkStealingPool();
	}

	@FXML
	public void initialize() {
		scope = Scope.DOCUMENT;
		{
			RangeToggle dt = () -> {
				volumes.setDisable(true);
				pages.setDisable(true);
				scope = Scope.DOCUMENT;
			};
			documentRadio.setUserData(dt);
		}
		{
			RangeToggle vt = () -> {
				volumes.setDisable(false);
				pages.setDisable(true);
				scope = Scope.VOLUME;
			};
			volumesRadio.setUserData(vt);
		}
		{
			RangeToggle pt = () -> {
				volumes.setDisable(true);
				pages.setDisable(false);
				scope = Scope.PAGES;
			};
			pagesRadio.setUserData(pt);
		}
		range.selectedToggleProperty().addListener((ov, t1, t2)->{
			((RangeToggle)t2.getUserData()).toggleRange();
		});
		volumes.textProperty().addListener((o, v1, v2)->{
			 if (!v2.matches("([1-9]\\d*)?")) {
				 volumes.setText(v1);
			 }
		});
		pages.textProperty().addListener((o, v1, v2)->{
			 if (!v2.matches("([1-9]\\d*(-([1-9]\\d*)?)?)?")) {
				 pages.setText(v1);
			 }
		});
		copies.getEditor().textProperty().addListener((o, v1, v2)->{
			 if (!v2.matches("([1-9]\\d?)?")) {
				 copies.getEditor().setText(v1);
			 }
		});
		copies.getEditor().setOnKeyPressed(ev->{
			switch (ev.getCode()) {
				case DOWN:
					if ("".equals(copies.getEditor().getText())) {
						copies.getEditor().setText("1");
					} else {
						copies.decrement();
					}
					break;
				case UP:
					if ("".equals(copies.getEditor().getText())) {
						copies.getEditor().setText("1");
					} else {
						copies.increment();
					}
					break;
				default:
			}
		});
	}
	
	void setBook(PEFBook book) {
		this.book = book;
		titles.getChildren().clear();
		if (!book.getTitle().iterator().hasNext()) {
			//Unknown title
		} else {
			for (String t : book.getTitle()) {
				Label title = new Label(t);
				title.setFont(new Font("System Bold", 18));
				titles.getChildren().add(title);
			}
		}
		if (!book.getAuthors().iterator().hasNext()) {
			//Unknown author
		} else {
			StringJoiner sj = new StringJoiner(", ");
			for (String author : book.getAuthors()) {
				sj.add(author);
			}
			author.setText(sj.toString());
		}
		dimensions.setText(Messages.MESSAGE_BOOK_DIMENSIONS.localize(book.getMaxWidth(), book.getMaxHeight()));
	}
	
	@FXML public void emboss() {
		Range rangeValue;
		try {
			rangeValue = getRange();
		} catch (Exception e) {
			Platform.runLater(()->{
				Alert alert = new Alert(AlertType.ERROR, e.getLocalizedMessage(), ButtonType.OK);
	    		alert.showAndWait();
			});
			return;
		}
		Settings settings = Settings.getSettings();
		Configuration conf = Configuration.getConfiguration(settings);
		String device = settings.getString(Keys.device);
		String align = settings.getString(Keys.align);
		int copiesValue = (Integer)copies.getValue();
		//TODO: localize
		String errorMessage = null;
		if (device==null) {
			errorMessage = "No device set.";
		} else if (!conf.settingOK()) {
			errorMessage = "Settings incomplete.";
		} else if (align==null) {
			errorMessage = "No alignment.";
		} else {
			try {
				EmbossTask et = new EmbossTask(book.getURI().toURL(), URLDecoder.decode(device, ENCODING), align, rangeValue, copiesValue, conf);
				et.setOnFailed(ev->{
					Alert alert = new Alert(AlertType.ERROR, et.getException().toString());
					alert.showAndWait();
				});
				et.setOnSucceeded(ev->{
					Alert alert = new Alert(AlertType.INFORMATION, "Embossing done.");
					alert.showAndWait();
				});
				exeService.submit(et);
			} catch (MalformedURLException | UnsupportedEncodingException e) {
				logger.log(Level.WARNING, "Failed to initiate embossing.", e);
			}
			closeWindow();
			return;
		}
		if (errorMessage!=null) {
			final String errorMsg = errorMessage;
			Platform.runLater(()->{
				Alert alert = new Alert(AlertType.ERROR, errorMsg, ButtonType.OK);
	    		alert.showAndWait();
			});
		}
	}
	
	static class EmbossTask extends Task<Void> {
		private final URL url;
		private final String deviceName;
		private final String align;
		private final Range range;
		private final int copies;
		private final Configuration conf;
		
		EmbossTask(URL url, String deviceName, String align, Range range, int copies, Configuration conf) {
			this.url = url;
			this.deviceName = deviceName;
			this.align = align;
			this.range = range;
			this.copies = copies;
			this.conf = conf;
		}

		@Override
		protected Void call() throws Exception {
			//TODO: include range (requires release of pef-tools v2.3.0)
			logger.info("Embossing " + copies + " copies on " + deviceName + " with alignment " + align);
			for (int i=0; i<copies; i++) {
				try (InputStream iss = url.openStream()) {
					//TODO: don't recreate objects for each copy unless necessary
					Embosser emb = conf.getConfiguredEmbosser();
					PrinterDevice bd = new PrinterDevice(deviceName, false);
					EmbosserWriter writer = emb.newEmbosserWriter(bd);
					
					PEFHandler.Builder phb = new PEFHandler.Builder(writer).
												range(range).
												offset(0);
					if (conf.supportsAligning()) {
				        Alignment alignment = Alignment.CENTER_INNER;
				        try {
				        	alignment = Alignment.valueOf(align.toUpperCase());
				        } catch (IllegalArgumentException e) {
				        	e.printStackTrace();
				        }
						phb.align(alignment);
					}
					new PEFConverterFacade(conf.getEmbosserCatalog()).parsePefFile(iss, phb.build());
				}
			}
			return null;
		}
	}

	private static class FailedToGetRangeException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = -4320557141555275970L;

		/**
		 * @param message
		 */
		public FailedToGetRangeException(String message) {
			super(message);
		}
		
	}

	private Range getRange() throws FailedToGetRangeException {
		switch (scope) {
			case DOCUMENT: {
				return new Range(1, book.getPages());
			}
			case PAGES: {
				String value = pages.getText();
				if ("".equals(value)) {
					throw new FailedToGetRangeException(Messages.ERROR_EMPTY_PAGE_RANGE.localize());
				}
				try {
					//TODO: it would be nice if the range could be verified against the actual number of pages
					return Range.parseRange(value);
				} catch (IllegalArgumentException e) {
					throw new FailedToGetRangeException(Messages.ERROR_FAILED_TO_PARSE_PAGE_RANGE.localize(value));
				}
			}
			case VOLUME: {
				String value = volumes.getText();
				if ("".equals(value)) {
					throw new FailedToGetRangeException(Messages.ERROR_EMPTY_VOLUME_NUMBER.localize());
				}
				try {
					int v = Integer.parseInt(value);
					if (v<1) {
						throw new FailedToGetRangeException(Messages.ERROR_VOLUME_NUMBER_LESS_THAN_ONE.localize());
					} else if (v>book.getVolumes()) {
						throw new FailedToGetRangeException(Messages.ERROR_VOLUME_NUMBER_OUT_OF_RANGE.localize(v, book.getVolumes()));
					} else {
						return new Range(book.getFirstPage(v), book.getLastPage(v));
					}
				} catch (NumberFormatException e) {
					throw new FailedToGetRangeException(Messages.ERROR_FAILED_TO_PARSE_VOLUME_NUMBER.localize(value));
				}
			}
			default:
				throw new FailedToGetRangeException("Error in code.");
		}
	}
	
	@FXML public void closeWindow() {
		((Stage)ok.getScene().getWindow()).close();
	}
	
	static interface RangeToggle {
		void toggleRange();
	}
	
}

