package application.emboss;


import java.util.StringJoiner;

import org.daisy.braille.pef.PEFBook;

import application.l10n.Messages;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

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
	@FXML private Spinner copies;
	private PEFBook book;

	@FXML
	public void initialize() {
		{
			RangeToggle dt = () -> {
				volumes.setDisable(true);
				pages.setDisable(true);
			};
			documentRadio.setUserData(dt);
		}
		{
			RangeToggle vt = () -> {
				volumes.setDisable(false);
				pages.setDisable(true);
			};
			volumesRadio.setUserData(vt);
		}
		{
			RangeToggle pt = () -> {
				volumes.setDisable(true);
				pages.setDisable(false);
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
	
	@FXML
	public void closeWindow() {
		((Stage)ok.getScene().getWindow()).close();
	}
	
	static interface RangeToggle {
		void toggleRange();
	}
	
}

