/*
 * Copyright 2007 - 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.jailer.ui.syntaxtextarea;

import java.awt.Dialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Segment;

import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.ReplaceDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import net.sf.jailer.ui.databrowser.metadata.MDTable;
import net.sf.jailer.ui.databrowser.metadata.MetaDataPanel;
import net.sf.jailer.util.Pair;

/**
 * Text area for SQL documents.
 * 
 * @author Ralf Wisser
 */
@SuppressWarnings("serial")
public class RSyntaxTextAreaWithSQLSyntaxStyle extends RSyntaxTextArea implements SearchListener {

	private FindDialog findDialog;
	private ReplaceDialog replaceDialog;
	private final boolean withExecuteActions;

	/**
	 * Key stokes.
	 */
	public static KeyStroke KS_RUN_BLOCK = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK);
	public static KeyStroke KS_RUN_ALL = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK);
	public static KeyStroke KS_FORMAT = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.SHIFT_DOWN_MASK|InputEvent.CTRL_DOWN_MASK);
	public static KeyStroke KS_SELECTTABLE = KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0);
	public static KeyStroke KS_ZOOMIN = KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK);
	public static KeyStroke KS_ZOOMOUT = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK);
	public static KeyStroke KS_ZOOMRESET = KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK);

	/**
	 * Actions.
	 */
	public final Action runBlock;
	public final Action runAll;
	public final Action explain;
	public final Action formatSQL;
	private final Action selectTableAction;
	private final Action zoomIn;
	private final Action zoomOut;
	private final Action zoomReset;
	
	public RSyntaxTextAreaWithSQLSyntaxStyle(boolean withExecuteActions, boolean withSelectTableAction) {
		this.withExecuteActions = withExecuteActions;
		setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
		setAutoIndentEnabled(true);
		// setTabsEmulated(true);
		
		// load images
		loadImages();
		
		zoomIn = new AbstractAction("Zoom In") {
			{
				putValue(ACCELERATOR_KEY, KS_ZOOMIN);
				InputMap im = getInputMap();
				im.put(KS_ZOOMIN, this);
				ActionMap am = getActionMap();
				am.put(this, this);
			}

			@Override
			public void actionPerformed(ActionEvent e) {
				zoom(1.25);
			}
		};

		zoomOut = new AbstractAction("Zoom Out") {
			{
				putValue(ACCELERATOR_KEY, KS_ZOOMOUT);
				InputMap im = getInputMap();
				im.put(KS_ZOOMOUT, this);
				ActionMap am = getActionMap();
				am.put(this, this);
			}

			@Override
			public void actionPerformed(ActionEvent e) {
				zoom(1 / 1.25);
			}
		};
		
		zoomReset = new AbstractAction("Restore Default Zoom") {
			{
				putValue(ACCELERATOR_KEY, KS_ZOOMRESET);
				InputMap im = getInputMap();
				im.put(KS_ZOOMRESET, this);
				ActionMap am = getActionMap();
				am.put(this, this);
			}

			@Override
			public void actionPerformed(ActionEvent e) {
				zoom(0);
			}
		};

		formatSQL = new AbstractAction("Format SQL") {
			{
				putValue(ACCELERATOR_KEY, KS_FORMAT);
				InputMap im = getInputMap();
				im.put(KS_FORMAT, this);
				ActionMap am = getActionMap();
				am.put(this, this);
			}

			@Override
			public void actionPerformed(ActionEvent e) {
				formatSQL();
			}
		};

		runBlock = new AbstractAction("Run selected SQL") {
			{
				putValue(ACCELERATOR_KEY, KS_RUN_BLOCK);
				InputMap im = getInputMap();
				im.put(KS_RUN_BLOCK, this);
				ActionMap am = getActionMap();
				am.put(this, this);
			}

			@Override
			public void actionPerformed(ActionEvent e) {
				RSyntaxTextAreaWithSQLSyntaxStyle.this.runBlock();
			}
		};
		
		explain = new AbstractAction("Explain Plan") {
			@Override
			public void actionPerformed(ActionEvent e) {
				RSyntaxTextAreaWithSQLSyntaxStyle.this.explainBlock();
			}
		};

		runAll = new AbstractAction("Run all SQL") {
			{
				putValue(ACCELERATOR_KEY, KS_RUN_ALL);
				InputMap im = getInputMap();
				im.put(KS_RUN_ALL, this);
				ActionMap am = getActionMap();
				am.put(this, this);
			}

			@Override
			public void actionPerformed(ActionEvent e) {
				RSyntaxTextAreaWithSQLSyntaxStyle.this.runAll();
			}
		};
		
		selectTableAction = withSelectTableAction? new AbstractAction("Select Table") {
			{
				putValue(ACCELERATOR_KEY, KS_SELECTTABLE);
				InputMap im = getInputMap();
				im.put(KS_SELECTTABLE, this);
				ActionMap am = getActionMap();
				am.put(this, this);
			}

			@Override
			public void actionPerformed(ActionEvent e) {
				MDTable selectedTable = getSelectedTable();
				if (selectedTable != null) {
					RSyntaxTextAreaWithSQLSyntaxStyle.this.selectTable(selectedTable);
				}
			}
		} : null;

		InputMap im = getInputMap();
		im.put(KS_RUN_BLOCK, runBlock);
		ActionMap am = getActionMap();
		am.put(runBlock, runBlock);

		im = getInputMap();
		im.put(KS_RUN_ALL, runAll);
		am = getActionMap();
		am.put(runAll, runAll);
		
		if (selectTableAction != null) {
			im = getInputMap();
			im.put(KS_SELECTTABLE, selectTableAction);
			am = getActionMap();
			am.put(selectTableAction, selectTableAction);
		}
		
		setMarkOccurrences(true);

		addCaretListener(new CaretListener() {
			@Override
			public void caretUpdate(CaretEvent e) {
				updateMenuItemState();
				if (selectTableAction != null) {
					selectTableAction.setEnabled(true);
				}
			}
		});

		setHighlightCurrentLine(true);
		setFadeCurrentLineHighlight(true);
		
		createPopupMenu();
		updateMenuItemState();
	}

	private Double initialFontSize = null;
	private Double currentFontSize = null;
	
	private void zoom(double factor) {
		if (currentFontSize == null) {
			currentFontSize = (double) getFont().getSize();
		}
		if (initialFontSize == null) {
			initialFontSize = currentFontSize;
		}
		if (factor == 0.0) {
			currentFontSize = initialFontSize;
		} else {
			currentFontSize = currentFontSize * factor;
			if (currentFontSize.intValue() == getFont().getSize()) {
				if (factor > 1) {
					++currentFontSize;
				} else {
					--currentFontSize;
				}
			}
			currentFontSize = Math.max(currentFontSize, 4);
			currentFontSize = Math.min(currentFontSize, 4 * initialFontSize);
		}
		setFont(new Font(getFont().getName(), getFont().getStyle(), currentFontSize.intValue()));
	}

	private ImageIcon scaleToLineHeight(ImageIcon imageIcon) {
		double s = getLineHeight() / (double) imageIcon.getIconHeight();
		return new ImageIcon(imageIcon.getImage().getScaledInstance((imageIcon.getIconWidth()), (int)(imageIcon.getIconHeight() * s + 0.5), Image.SCALE_SMOOTH));
	}

	protected MDTable getSelectedTable() {
		return null;
	}
	
	protected void selectTable(MDTable mdTable) {
	}

	protected boolean canExplain() {
		return false;
	}

	/**
	 * Overridden to toggle the enabled state of various menu items.
	 */
	@Override
	protected void configurePopupMenu(JPopupMenu popupMenu) {
		if (selectTableAction != null) {
			try {
				selectTableAction.setEnabled(getSelectedTable() != null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Overridden to add menu items related to formatting.
	 *
	 * @return the popup menu
	 */
	@Override
	protected JPopupMenu createPopupMenu() {
		JPopupMenu menu = super.createPopupMenu();

		JMenuItem item = new JMenuItem(formatSQL);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				formatSQL();
			}
		});
		menu.add(item, 0);
		if (selectTableAction != null) {
			menu.add(new JMenuItem(selectTableAction), 1);
		}
		menu.add(new JSeparator(), 2);

		menu.add(new JMenuItem(new ShowFindDialogAction()), 0);
		menu.add(new JMenuItem(new ShowReplaceDialogAction()), 1);
		menu.add(new JSeparator(), 2);

		if (withExecuteActions) {
			item = new JMenuItem(runBlock);
			menu.add(item, 0);
			item = new JMenuItem(runAll);
			menu.add(item, 1);
			item = new JMenuItem(explain);
			menu.add(item, 2);
			menu.add(new JSeparator(), 3);
		}

		menu.addSeparator();
		menu.add(zoomIn);
		menu.add(zoomOut);
		menu.add(zoomReset);
		
		return menu;
	}

	/**
	 * Folding is not supported.
	 */
	@Override
	protected void appendFoldingMenu(JPopupMenu popup) {
	}

	/**
	 * Replaces statement(s) at caret position.
	 *
	 * @param replacement
	 *            the replacement
	 * @param singleStatement
	 *            <code>true</code> to replace only one statement
	 */
	public void replaceCurrentStatement(String replacement, boolean singleStatement) {
		Pair<Integer, Integer> loc = getCurrentStatementLocation(singleStatement, false, null, false);
		if (loc != null) {
			try {
				int from = loc.a;
				int to = loc.b;
				if (to >= getLineCount()) {
					to = getLineCount() - 1;
				}
				int start = getLineStartOffset(from);
				int end = getLineEndOffset(to);
				replaceRange(replacement, start, end);
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Gets statement(s) at caret position.
	 * 
	 * @param singleStatement
	 *            <code>true</code> to get only one statement
	 * @return pair of start and end line number
	 */
	public String getCurrentStatement(boolean singleStatement) {
		Pair<Integer, Integer> loc = getCurrentStatementLocation(singleStatement, false, null, false);
		if (loc != null) {
			return getText(loc.a, loc.b, true);
		}
		return "";
	}

	/**
	 * Gets text between two lines.
	 * 
	 * @param complete
	 *            if <code>false</code>, return text from start line to current
	 *            caret position
	 */
	public String getText(int from, int to, boolean complete) {
		Segment txt = new Segment();
		try {
			if (to >= getLineCount()) {
				to = getLineCount() - 1;
			}
			int off = getLineStartOffset(from);
			getDocument().getText(off, (complete ? getLineEndOffset(to) : getCaretPosition()) - off, txt);
		} catch (BadLocationException e) {
			return "";
		}
		return txt.toString();
	}

	/**
	 * Does the text have any non-WS characters?
	 */
	public boolean isTextEmpty(int from, int to) {
		Segment txt = new Segment();
		try {
			if (to >= getLineCount()) {
				to = getLineCount() - 1;
			}
			for (int i = from; i <= to; ++i) {
				int off = getLineStartOffset(i);
				getDocument().getText(off, getLineEndOffset(to) - off, txt);
				if (txt.toString().trim().length() > 0) {
					return false;
				}
			}
		} catch (BadLocationException e) {
			return false;
		}
		return true;
	}

	/**
	 * Gets start- and end-line number of statement fragment at caret position, if any.
	 * 
	 * @return pair of (start and end line number) and (begin- end-offset)
	 */
	public Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> getCurrentStatementFragmentLocation() {
		int begin = Math.min(getCaret().getDot(), getCaret().getMark());
		int end = Math.max(getCaret().getDot(), getCaret().getMark());
		if (begin == end || end - begin > 1000000) {
			return null;
		}
		Segment txt = new Segment();
		try {
			getDocument().getText(begin, end - begin, txt);
			Pattern pattern = Pattern.compile("(\\n\\s*\\n)|(;\\s*(\\n\\r?|$))", Pattern.DOTALL);
			Matcher matcher = pattern.matcher(txt);
			if (!matcher.find()) {
				return new Pair<Pair<Integer, Integer>, Pair<Integer, Integer>>(
						new Pair<Integer, Integer>(getLineOfOffset(begin), getLineOfOffset(end)),
						new Pair<Integer, Integer>(begin, end)
						);
			}
		} catch (BadLocationException e) {
			return null;
		}
		return null;
	}

	/**
	 * Gets start- and end-line number of statement(s) at caret position.
	 * 
	 * @param eosLines if not <code>null</code>, put end-of-statement line numbers into
	 * @return pair of start and end line number
	 */
	public Pair<Integer, Integer> getCurrentStatementLocation(Set<Integer> eosLines) {
		return getCurrentStatementLocation(getCaret().getDot() != getCaret().getMark(), false, eosLines, false);
	}

	/**
	 * Gets start- and end-line number of statement(s) at caret position.
	 * 
	 * @param singleStatement <code>true</code> to get only one statement
	 * @param eosLines if not <code>null</code>, put end-of-statement line numbers into
	 * @return pair of start and end line number
	 */
	public Pair<Integer, Integer> getCurrentStatementLocation(boolean singleStatement, boolean currentLineMayBeEmpty, Set<Integer> eosLines, boolean startAtLineAbove) {
		try {
			int y = getLineOfOffset(Math.min(getCaret().getDot(), getCaret().getMark()));
			int caretBegin = y;
			int start = y;
			while (start > 0) {
				int startM1Off = getLineStartOffset(start - 1);
				Segment txt = new Segment();
				getDocument().getText(startM1Off, getLineEndOffset(start - 1) - startM1Off, txt);
				String sLine = txt.toString().trim();
				boolean endsWithSemicolon = sLine.endsWith(";");
				if (endsWithSemicolon && eosLines != null) {
					eosLines.add(start - 1);
				}
				if (sLine.length() == 0 && !singleStatement) {
					if (eosLines != null) {
						eosLines.add(start - 2);
						eosLines.add(-start);
					}
				}
				if ((/*!singleStatement &&*/ sLine.length() == 0) || (singleStatement && endsWithSemicolon)) {
					if (start != y || !startAtLineAbove || !endsWithSemicolon) {
						break;
					}
				}
				--start;
			}
			
			int end = getLineOfOffset(Math.max(getCaret().getDot(), getCaret().getMark()));
			int caretEnd = end;
			int lineCount = getLineCount();
			
			int l = caretBegin;
			boolean eosSeen = false;
			while (l < caretEnd && l < lineCount) {
				int lOff = getLineStartOffset(l);
				Segment txt = new Segment();
				getDocument().getText(lOff, getLineEndOffset(l) - lOff, txt);
				String sLine = txt.toString().trim();
				boolean endsWithSemicolon = sLine.endsWith(";");
				if (endsWithSemicolon) {
					if (eosLines != null) {
						eosLines.add(l);
					}
					eosSeen = true;
				} else if (sLine.length() > 0) {
					eosSeen = false;
				}
				if (sLine.length() == 0 && !singleStatement) {
					if (eosLines != null) {
						eosLines.add(l - 1);
						eosLines.add(-l - 1);
					}
				}
				++l;
			}

			if (!eosSeen) {
				while (end < lineCount) {
					int endOff = getLineStartOffset(end);
					Segment txt = new Segment();
					getDocument().getText(endOff, getLineEndOffset(end) - endOff, txt);
					String sLine = txt.toString().trim();
					if ((/*!singleStatement &&*/ sLine.length() == 0) && !(currentLineMayBeEmpty && end == y)) {
						if (end > start) {
							--end;
						}
						break;
					}
					boolean endsWithSemicolon = sLine.endsWith(";");
					if (endsWithSemicolon && eosLines != null) {
						eosLines.add(end);
					}
					if (sLine.length() == 0 && !singleStatement) {
						if (eosLines != null) {
							eosLines.add(end - 1);
							eosLines.add(-end - 1);
						}
					}
					if (singleStatement && endsWithSemicolon) {
						break;
					}
					++end;
				}
			}
			if (end == lineCount && end > 0) {
				--end;
			}
			l = start;
			boolean inStatement = false;
			boolean nonEmptyLineSeen = false;
			while (l <= end && l < lineCount) {
				int lOff = getLineStartOffset(l);
				Segment txt = new Segment();
				getDocument().getText(lOff, getLineEndOffset(l) - lOff, txt);
				String sLine = txt.toString().trim();
				boolean endsWithSemicolon = sLine.endsWith(";");
				if (sLine.length() > 0) {
					nonEmptyLineSeen = true;
				}
				if (!nonEmptyLineSeen) {
					if (l + 1 < lineCount) {
						start = l + 1;
					}
				}
				if (endsWithSemicolon) {
					inStatement = false;
				} else if (sLine.length() == 0) {
					if (!inStatement) {
						if (eosLines != null) {
							eosLines.add(-l - 1);
						}
					}
				} else {
					inStatement = true;
				}
				++l;
			}
			return new Pair<Integer, Integer>(start, end);
		} catch (BadLocationException e) {
			e.printStackTrace();
			return null;
		}
	}

	private List<CaretListener> listenerList = new ArrayList<CaretListener>();

	@Override
	public void addCaretListener(CaretListener caretListener) {
		listenerList.add(caretListener);
		super.addCaretListener(caretListener);
	}

	@Override
	public void removeCaretListener(CaretListener caretListener) {
		listenerList.remove(caretListener);
		super.removeCaretListener(caretListener);
	}

	private void runWithoutCaretEvents(Runnable runnable) {
		ArrayList<CaretListener> removedListener = new ArrayList<CaretListener>(listenerList);
		for (CaretListener l: listenerList) {
			super.removeCaretListener(l);
		}
		listenerList.clear();
		try {
			runnable.run();
		} finally {
			for (CaretListener l: removedListener) {
				addCaretListener(l);
			}
			forceCaretEvent();
		}
	}

	public void forceCaretEvent() {
		// force caret event
		int cPos = getCaretPosition();
		if (cPos > 0) {
			setCaretPosition(cPos - 1);
		} else if (getDocument().getLength() > cPos) {
			setCaretPosition(cPos + 1);
		}
		setCaretPosition(cPos);
	}

	@Override
	public void undoLastAction() {
		runWithoutCaretEvents(new Runnable() {
			@Override
			public void run() {
				RSyntaxTextAreaWithSQLSyntaxStyle.super.undoLastAction();
			}
		});
	}

	@Override
	public void redoLastAction() {
		runWithoutCaretEvents(new Runnable() {
			@Override
			public void run() {
				RSyntaxTextAreaWithSQLSyntaxStyle.super.redoLastAction();
			}
		});
	}

	/**
	 * Listens for events from our search dialogs and actually does the dirty
	 * work.
	 */
	@Override
	public void searchEvent(SearchEvent e) {

		SearchEvent.Type type = e.getType();
		final SearchContext context = e.getSearchContext();
		SearchResult result = null;

		switch (type) {
		default: // Prevent FindBugs warning later
		case MARK_ALL:
			result = SearchEngine.markAll(this, context);
			break;
		case FIND:
			result = SearchEngine.find(this, context);
			if (!result.wasFound()) {
				UIManager.getLookAndFeel().provideErrorFeedback(this);
			}
			break;
		case REPLACE:
			result = SearchEngine.replace(this, context);
			if (!result.wasFound()) {
				UIManager.getLookAndFeel().provideErrorFeedback(this);
			}
			break;
		case REPLACE_ALL:
			final SearchResult[] resultBag = new SearchResult[1];
			runWithoutCaretEvents(new Runnable() {
				@Override
				public void run() {
					resultBag[0] = SearchEngine.replaceAll(RSyntaxTextAreaWithSQLSyntaxStyle.this, context);
				}
			});
			result = resultBag[0];
			JOptionPane.showMessageDialog(null, result.getCount() + " occurrences replaced.");
			break;
		}

		String text = null;
		if (result.wasFound()) {
			text = "Text found; occurrences marked: " + result.getMarkedCount();
		} else if (type == SearchEvent.Type.MARK_ALL) {
			if (result.getMarkedCount() > 0) {
				text = "Occurrences marked: " + result.getMarkedCount();
			} else {
				text = "";
			}
		} else {
			text = "Text not found";
			JOptionPane.showMessageDialog(null, text);
		}
	}

	private class ShowFindDialogAction extends AbstractAction {

		public ShowFindDialogAction() {
			super("Find...");
			int c = getToolkit().getMenuShortcutKeyMask();
			KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, c);
			putValue(ACCELERATOR_KEY, keyStroke);
			InputMap im = getInputMap();
			im.put(keyStroke, this);
			ActionMap am = getActionMap();
			am.put(this, this);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			initDialogs();
			if (replaceDialog.isVisible()) {
				replaceDialog.setVisible(false);
			}
			findDialog.setVisible(true);
		}
	}

	private class ShowReplaceDialogAction extends AbstractAction {

		public ShowReplaceDialogAction() {
			super("Replace...");
			int c = getToolkit().getMenuShortcutKeyMask();
			KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_H, c);
			putValue(ACCELERATOR_KEY, keyStroke);
			InputMap im = getInputMap();
			im.put(keyStroke, this);
			ActionMap am = getActionMap();
			am.put(this, this);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			initDialogs();
			if (findDialog.isVisible()) {
				findDialog.setVisible(false);
			}
			replaceDialog.setVisible(true);
		}
	}

	protected void runBlock() {
	}

	protected void explainBlock() {
	}

	protected void runAll() {
	}

	private void initDialogs() {
		if (findDialog == null) {
			Window owner = SwingUtilities.getWindowAncestor(this);
			if (owner instanceof Dialog) {
				findDialog = new FindDialog((Dialog) owner, this);
				replaceDialog = new ReplaceDialog((Dialog) owner, this);
			} else if (owner instanceof Frame) {
				findDialog = new FindDialog((Frame) owner, this);
				replaceDialog = new ReplaceDialog((Frame) owner, this);
			} else {
				findDialog = new FindDialog((Frame) null, this);
				replaceDialog = new ReplaceDialog((Frame) null, this);
			}

			// This ties the properties of the two dialogs together (match case,
			// regex, etc.).
			SearchContext context = findDialog.getSearchContext();
			replaceDialog.setSearchContext(context);
		}
	}

	public void updateMenuItemState() {
		updateMenuItemState(true, true);
	}

	public void updateMenuItemState(boolean allowRun, boolean setLineHighlights) {
		Set<Integer> eosLines = new HashSet<Integer>();
		Pair<Integer, Integer> loc;
		Pair<Integer, Integer> locFragmentOffset = null;
		Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> locFragment = getCurrentStatementFragmentLocation();
		if (locFragment != null) {
			loc = locFragment.a;
			locFragmentOffset = locFragment.b;
		} else {
			loc = getCurrentStatementLocation(eosLines);
		}
		runBlock.setEnabled(allowRun && loc != null && !isTextEmpty(loc.a, loc.b));
		explain.setEnabled(canExplain() && allowRun && loc != null && !isTextEmpty(loc.a, loc.b));
		runAll.setEnabled(allowRun && RSyntaxTextAreaWithSQLSyntaxStyle.this.getDocument().getLength() > 0);
		if (allowRun && setLineHighlights) {
			if (!pending.get()) {
				removeAllLineHighlights();
				loadImages();
				setHighlightCurrentLine(true);
				if (gutter != null) {
					gutter.removeAllTrackingIcons();
					try {
						boolean el = false;
						if (loc.a != loc.b || !getText(loc.a, loc.b, true).trim().isEmpty()) {
							for (int l = loc.a; l <= loc.b; ++l) {
								if (eosLines.contains(-l - 1)) {
									// empty line
									el = true;
									continue;
								}
								ImageIcon theIcon;
								boolean beginn = l == loc.a || eosLines.contains(l - 1) || el;
								boolean end = l == loc.b || eosLines.contains(l);
								el = false;
								if (beginn) {
									theIcon = end? iconBeginEnd : iconBegin;
								} else if (end) {
									theIcon = iconEnd;
								} else {
									theIcon = icon;
								}
								if (locFragmentOffset != null) {
									if (theIcon == icon) {
										theIcon = iconf;
									}
									if (theIcon == iconBegin) {
										theIcon = iconBeginf;
									}
									if (theIcon == iconBeginEnd) {
										theIcon = iconBeginEndf;
									}
									if (theIcon == iconEnd) {
										theIcon = iconEndf;
									}
								}
								gutter.addLineTrackingIcon(l, theIcon);
							}
						}
					} catch (BadLocationException e) {
					}
				}
				if (loc.b - loc.a > 10000) {
					stopped.set(false);
					pending.set(true);
					new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e) {
							}
							pending.set(false);
							if (!stopped.get()) {
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {
										updateMenuItemState(true, true);
									}
								});
							}
						}
					}).start();
				} else {
					stopped.set(true);
				}
			}
		}
	}

	/**
	 * Formats SQL statement at caret position.
	 */
	private void formatSQL() {
		String currentStatement = getCurrentStatement(true);
		Pattern pattern = Pattern.compile("(.*?)(;\\s*(\\n\\r?|$))", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(currentStatement + ";");
		boolean result = matcher.find();
		if (result) {
			StringBuffer sb = new StringBuffer();
			do {
				matcher.appendReplacement(sb,
						Matcher.quoteReplacement(new BasicFormatterImpl().format(matcher.group(1)))
								+ matcher.group(2));
				result = matcher.find();
			} while (result);
			matcher.appendTail(sb);
			if (sb.length() > 0) {
				sb.setLength(sb.length() - 1);
			}
			pattern = Pattern.compile(".*?[^;](\\s*)$", Pattern.DOTALL);
			matcher = pattern.matcher(currentStatement);
			String tail = matcher.matches() ? matcher.group(1) : "";
			replaceCurrentStatement(sb.toString() + tail, true);
		}
	}

	private final AtomicBoolean stopped = new AtomicBoolean(false);
	private final AtomicBoolean pending = new AtomicBoolean(false);
	private Gutter gutter;

	public void setGutter(Gutter gutter) {
		this.gutter = gutter;
	}
	
	public void setLineTrackingIcon(int line, Icon theIcon) {
		if (gutter != null) {
			try {
				gutter.removeAllTrackingIcons();
				gutter.addLineTrackingIcon(line, theIcon);
			} catch (BadLocationException e) {
			}
		}
	}
	
	private Integer lastLineHeight;
	
	private void loadImages() {
		if (lastLineHeight == null || lastLineHeight != getLineHeight()) {
			lastLineHeight = getLineHeight();
			try {
				String dir = "/net/sf/jailer/ui/resource";
				icon = scaleToLineHeight(new ImageIcon(MetaDataPanel.class.getResource(dir + "/sqlconsole.png")));
	    	    iconBegin = scaleToLineHeight(new ImageIcon(MetaDataPanel.class.getResource(dir + "/sqlconsolebegin.png")));
				iconBeginEnd = scaleToLineHeight(new ImageIcon(MetaDataPanel.class.getResource(dir + "/sqlconsolebeginend.png")));
				iconEnd = scaleToLineHeight(new ImageIcon(MetaDataPanel.class.getResource(dir + "/sqlconsoleend.png")));
				iconf = scaleToLineHeight(new ImageIcon(MetaDataPanel.class.getResource(dir + "/sqlconsolef.png")));
	    	    iconBeginf = scaleToLineHeight(new ImageIcon(MetaDataPanel.class.getResource(dir + "/sqlconsolebeginf.png")));
				iconBeginEndf = scaleToLineHeight(new ImageIcon(MetaDataPanel.class.getResource(dir + "/sqlconsolebeginendf.png")));
				iconEndf = scaleToLineHeight(new ImageIcon(MetaDataPanel.class.getResource(dir + "/sqlconsoleendf.png")));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private ImageIcon icon;
	private ImageIcon iconBegin;
	private ImageIcon iconBeginEnd;
	private ImageIcon iconEnd;
	private ImageIcon iconf;
	private ImageIcon iconBeginf;
	private ImageIcon iconBeginEndf;
	private ImageIcon iconEndf;

}
