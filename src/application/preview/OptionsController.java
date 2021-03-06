package application.preview;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.daisy.dotify.api.tasks.CompiledTaskSystem;
import org.daisy.dotify.api.tasks.TaskOption;
import org.daisy.dotify.tasks.runner.RunnerResult;

import application.l10n.Messages;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;

/**
 * Provides a controller for options.
 * @author Joel Håkansson
 *
 */
public class OptionsController extends ScrollPane {
	private static final Logger logger = Logger.getLogger(OptionsController.class.getCanonicalName());
	@FXML private VBox vbox;
	@FXML private Button applyButton;
	@FXML private CheckBox monitorCheckbox;
	private boolean refreshRequested;
	private Set<TaskOption> values;

	/**
	 * Creates a new options controller.
	 */
	public OptionsController() {
		try {
			FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Options.fxml"), Messages.getBundle());
			fxmlLoader.setRoot(this);
			fxmlLoader.setController(this);
			fxmlLoader.load();
		} catch (IOException e) {
			logger.log(Level.WARNING, "Failed to load view", e);
		}
		refreshRequested = false;
	}
	
	/**
	 * Sets options.
	 * @param ts the task system
	 * @param opts the runner results
	 * @param prvOpts the previous options
	 */
	public void setOptions(CompiledTaskSystem ts, List<RunnerResult> opts, Map<String, Object> prvOpts) {
		clear();
		values = new HashSet<>();
		displayItems(ts.getName(), ts.getOptions(), prvOpts);
		for (RunnerResult r : opts) {
			displayItems(r.getTask().getName(), r.getTask().getOptions(), prvOpts);
		}
	}
	
	private void displayItems(String title, List<TaskOption> options, Map<String, Object> prvOpts) {
		if (options==null || options.isEmpty()) {
			return;
		}
		addGroupTitle(title);
		List<TaskOption> sortedOptions = options.stream()
				.sorted((o1, o2) -> {
					return o1.getKey().compareTo(o2.getKey());
				})
				.collect(Collectors.toList());
		for (TaskOption o : sortedOptions) {
			addItem(o, prvOpts);
		}
	}
	
	private void addGroupTitle(String name) {
		Label label = new Label(name);
		label.setTextFill(Paint.valueOf("#404040"));
		label.setFont(new Font("System Bold", 12));
		VBox.setMargin(label, new Insets(0, 0, 10, 0));
		vbox.getChildren().add(label);
	}
	
	private void addItem(TaskOption o, Map<String, Object> setOptions) {
		OptionItem item = new OptionItem(o, values.contains(o));
		Object value = setOptions.get(o.getKey());
		if (value!=null) {
			item.setValue(value.toString());
		}
		VBox.setMargin(item, new Insets(0, 0, 10, 0));
		vbox.getChildren().add(item);
		values.add(o);
	}
	
	private void clear() {
		vbox.getChildren().clear();
	}
	
	/**
	 * Gets the parameters.
	 * @return returns the parameters
	 */
	public Map<String, Object> getParams() {
		Map<String, Object> opts = new HashMap<>();
		for (Node n : vbox.getChildren()) {
			if (n instanceof OptionItem) {
				OptionItem o = (OptionItem)n;
				String value = o.getValue();
				if (value!=null&&!"".equals(value)) {
					opts.put(o.getKey(), value);
				}
				//TODO: warn if key already is included
			}
		}
		return opts;
	}
	
	@FXML void requestRefresh() {
		refreshRequested = true;
	}
	
	boolean isWatching() {
		return monitorCheckbox.isSelected();
	}
	
	boolean refreshRequested() {
		if (refreshRequested) {
			refreshRequested = false;
			return true;
		}
		return false;
	}

}
