
/*
 * Author: Jonathan Link
 * Email: jonathanlink[d o t]email[a t]gmail[d o t]com
 * Date of creation: 13.11.2014
 * Version: 0.1
 * Description:
 * 
 * What does it DO:
 * This object converts the content of a PDF file into a String.
 * The layout of the texts is transcribed as near as the one in the PDF given at the input.
 * 
 * What does it NOT DO:
 * Vertical texts in the PDF file are not handled for the moment.
 * 
 * I would appreciate any feedback you could offer. (see my email address above)
 * 
 * LICENSE:
 * 
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Jonathan Link
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * 
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.pdfbox.util.TextPosition;
import org.apache.pdfbox.util.TextPositionComparator;

public class PDFLayoutTextStripper extends PDFTextStripper {

	public static final boolean DEBUG = false;
	public static final int OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT = 4;

	private double currentPageWidth;
	private TextPosition previousTextPosition;
	private List<TextLine> textLineList;

	public PDFLayoutTextStripper() throws IOException {
		super();
		// this.textLineList = new ArrayList<>();
	}

	@Override
	protected void processPage(PDPage page, COSStream content) throws IOException {
		PDRectangle pageRectangle = page.findMediaBox();
		if (pageRectangle != null) {
			this.setCurrentPageWidth(pageRectangle.getWidth());
			super.processPage(page, content);
			this.previousTextPosition = null;
			this.textLineList = new ArrayList<>();
		}
	}

	@Override
	protected void writePage() throws IOException{
		LinkedList<List<TextPosition>> localCharactersByArticle = new LinkedList<>(super.getCharactersByArticle());
		for (int i = 0; i < localCharactersByArticle.size(); i++) {
			List<TextPosition> textList = localCharactersByArticle.get(i);
			sortTextPositionList(textList);
			try {
				this.iterateThroughTextList(textList.iterator());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		this.writeToOutputStream(this.getTextLineList());
	}

	private void writeToOutputStream(final List<TextLine> localTextLineList) throws IOException {
		for (TextLine textLine : localTextLineList) {
			char[] line = textLine.getLine().toCharArray();
			super.getOutput().write(line);
			super.getOutput().write('\n');
			super.getOutput().flush();
		}
	}

	/*
	 * In order to get rid of the warning: TextPositionComparator class should
	 * implement Comparator<TextPosition> instead of Comparator
	 */
	@SuppressWarnings("unchecked")
	private static void sortTextPositionList(final List<TextPosition> textList) {
		TextPositionComparator comparator = new TextPositionComparator();
		Collections.sort(textList, comparator);
	}

	private static int computeAverageCharacterWidth(final List<TextPosition> textPositionList) {
		if (textPositionList.isEmpty()) {
			return 0;
		}
		double averageWidth = 0.0;
		for (TextPosition textPosition : textPositionList) {
			averageWidth += textPosition.getWidthOfSpace();
		}
		return (int) Math.floor(averageWidth) / textPositionList.size();
	}

	private void writeLine(final List<TextPosition> textPositionList) throws Exception{
		if (textPositionList.size() > 0) {
			TextLine textLine = this.addNewLine();
			boolean firstCharacterOfLineFound = false;
			for (TextPosition textPosition : textPositionList) {
				CharacterFactory characterFactory = new CharacterFactory(firstCharacterOfLineFound);
				Character character = characterFactory.createCharacterFromTextPosition(textPosition,
						this.getPreviousTextPosition());
				textLine.writeCharacterAtIndex(character);
				this.setPreviousTextPosition(textPosition);
				firstCharacterOfLineFound = true;
			}
		} else {
			this.addNewLine(); // white line
		}
	}

	private void iterateThroughTextList(Iterator<TextPosition> textIterator) throws Exception{
		List<TextPosition> textPositionList = new ArrayList<>();

		while (textIterator.hasNext()) {
			TextPosition textPosition = textIterator.next();
			int numberOfNewLines = this.getNumberOfNewLinesFromPreviousTextPosition(textPosition);
			if (numberOfNewLines == 0) {
				textPositionList.add(textPosition);
			} else {
				this.writeTextPositionList(textPositionList);
				this.createNewEmptyNewLines(numberOfNewLines);
				textPositionList.add(textPosition);
			}
			this.setPreviousTextPosition(textPosition);
		}
	}

	private void writeTextPositionList(final List<TextPosition> textPositionList) throws Exception{
		this.writeLine(textPositionList);
		textPositionList.clear();
	}

	private void createNewEmptyNewLines(int numberOfNewLines) {
		for (int i = 0; i < numberOfNewLines - 1; ++i) {
			this.addNewLine();
		}
	}

	private int getNumberOfNewLinesFromPreviousTextPosition(final TextPosition textPosition) {
		TextPosition localPreviousTextPosition = this.getPreviousTextPosition();
		if (localPreviousTextPosition == null) {
			return 1;
		}

		double textYPosition = Math.round(textPosition.getTextPos().getYPosition());
		double previousTextYPosition = Math.round(localPreviousTextPosition.getTextPos().getYPosition());

		if (textYPosition < previousTextYPosition) {
			double height = textPosition.getHeight();
			int numberOfLines = (int) (Math.floor(previousTextYPosition - textYPosition) / height);
			numberOfLines = Math.max(1, numberOfLines - 1); // exclude current
															// new line
			return numberOfLines;
		}

		return 0;
	}

	private TextLine addNewLine() {
		TextLine textLine = new TextLine(this.getCurrentPageWidth());
		this.textLineList.add(textLine);
		return textLine;
	}

	private TextPosition getPreviousTextPosition() {
		return this.previousTextPosition;
	}

	private void setPreviousTextPosition(final TextPosition setPreviousTextPosition) {
		this.previousTextPosition = setPreviousTextPosition;
	}

	private int getCurrentPageWidth() {
		return (int) Math.round(this.currentPageWidth);
	}

	private void setCurrentPageWidth(double currentPageWidth) {
		this.currentPageWidth = currentPageWidth;
	}

	private List<TextLine> getTextLineList() {
		return this.textLineList;
	}

}

class TextLine {

	private static final char SPACE_CHARACTER = ' ';
	private int lineLength;
	private String line;
	private int lastIndex;

	public TextLine(int lineLength) {
		this.line = "";
		this.lineLength = lineLength / PDFLayoutTextStripper.OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT;
		this.completeLineWithSpaces();
	}

	public void writeCharacterAtIndex(final Character character) throws Exception{
		character.setIndex(this.computeIndexForCharacter(character));
		int index = character.getIndex();
		char characterValue = character.getCharacterValue();
		if (this.indexIsInBounds(index) && this.line.charAt(index) == SPACE_CHARACTER) {
			this.line = this.line.substring(0, index) + characterValue
					+ this.line.substring(index + 1, this.getLineLength());
		}
	}

	public int getLineLength() {
		return this.lineLength;
	}

	public String getLine() {
		return this.line;
	}

	private int computeIndexForCharacter(final Character character) throws Exception {
			int index = character.getIndex();
			boolean isCharacterPartOfPreviousWord = character.isCharacterPartOfPreviousWord();
			boolean isCharacterAtTheBeginningOfNewLine = character.isCharacterAtTheBeginningOfNewLine();
			boolean isCharacterCloseToPreviousWord = character.isCharacterCloseToPreviousWord();

			if (!this.indexIsInBounds(index)) {
				return -1;
			}
			if (isCharacterPartOfPreviousWord && !isCharacterAtTheBeginningOfNewLine) {
				index = this.findMinimumIndexWithSpaceCharacterFromIndex(index);
			} else if (isCharacterCloseToPreviousWord) {
				if (this.line.charAt(index) != SPACE_CHARACTER) {
					index = index + 1;
				} else {
					index = this.findMinimumIndexWithSpaceCharacterFromIndex(index) + 1;
				}
			}
			index = this.getNextValidIndex(index, isCharacterPartOfPreviousWord);
			return index;
	}

	private boolean isSpaceCharacterAtIndex(int index) {
		return this.line.charAt(index) != SPACE_CHARACTER;
	}

	private boolean isNewIndexGreaterThanLastIndex(int index) {
		int localLastIndex = this.getLastIndex();
		return index > localLastIndex;
	}

	private int getNextValidIndex(int index, boolean isCharacterPartOfPreviousWord) {
		int nextValidIndex = index;
		int localLastIndex = this.getLastIndex();
		if (!this.isNewIndexGreaterThanLastIndex(index)) {
			nextValidIndex = localLastIndex + 1;
		}
		if (!isCharacterPartOfPreviousWord && this.isSpaceCharacterAtIndex(index - 1)) {
			nextValidIndex = nextValidIndex + 1;
		}
		this.setLastIndex(nextValidIndex);
		return nextValidIndex;
	}

	private int findMinimumIndexWithSpaceCharacterFromIndex(int index) {
		int newIndex = index;
		while (newIndex >= 0 && this.line.charAt(newIndex) == SPACE_CHARACTER) {
			newIndex = newIndex - 1;
		}
		return newIndex + 1;
	}

	private boolean indexIsInBounds(int index) {
		return (index >= 0 && index < this.lineLength);
	}

	private void completeLineWithSpaces() {
		StringBuilder sb = new StringBuilder(this.line);
		for (int i = 0; i < this.getLineLength(); ++i) {
			sb.append(SPACE_CHARACTER);
		}
		this.line = sb.toString();
	}

	private int getLastIndex() {
		return this.lastIndex;
	}

	private void setLastIndex(int lastIndex) {
		this.lastIndex = lastIndex;
	}

}

class Character {

	private char characterValue;
	private int index;
	private boolean isCharacterPartOfPreviousWord;
	private boolean isFirstCharacterOfAWord;
	private boolean isCharacterAtTheBeginningOfNewLine;
	private boolean isCharacterCloseToPreviousWord;

	public Character(char characterValue, int index, boolean isCharacterPartOfPreviousWord,
			boolean isFirstCharacterOfAWord, boolean isCharacterAtTheBeginningOfNewLine,
			boolean isCharacterPartOfASentence) {
		this.characterValue = characterValue;
		this.index = index;
		this.isCharacterPartOfPreviousWord = isCharacterPartOfPreviousWord;
		this.isFirstCharacterOfAWord = isFirstCharacterOfAWord;
		this.isCharacterAtTheBeginningOfNewLine = isCharacterAtTheBeginningOfNewLine;
		this.isCharacterCloseToPreviousWord = isCharacterPartOfASentence;
		if (PDFLayoutTextStripper.DEBUG) {
			System.out.println(this.toString());
		}
	}

	public char getCharacterValue() {
		return this.characterValue;
	}

	public int getIndex() {
		return this.index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public boolean isCharacterPartOfPreviousWord() {
		return this.isCharacterPartOfPreviousWord;
	}

	public boolean isFirstCharacterOfAWord() {
		return this.isFirstCharacterOfAWord;
	}

	public boolean isCharacterAtTheBeginningOfNewLine() {
		return this.isCharacterAtTheBeginningOfNewLine;
	}

	public boolean isCharacterCloseToPreviousWord() {
		return this.isCharacterCloseToPreviousWord;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.index);
		sb.append(' ');
		sb.append(this.characterValue);
		sb.append(" isCharacterPartOfPreviousWord=");
		sb.append(this.isCharacterPartOfPreviousWord);
		sb.append(" isFirstCharacterOfAWord=");
		sb.append(this.isFirstCharacterOfAWord);
		sb.append(" isCharacterAtTheBeginningOfNewLine=");
		sb.append(this.isCharacterAtTheBeginningOfNewLine);
		sb.append(" isCharacterPartOfASentence=");
		sb.append(this.isCharacterCloseToPreviousWord);
		sb.append(" isCharacterCloseToPreviousWord=");
		sb.append(this.isCharacterCloseToPreviousWord);
		return sb.toString();

	}

}

class CharacterFactory {

	private TextPosition previousTextPosition;
	private boolean firstCharacterOfLineFound;
	private boolean isCharacterPartOfPreviousWord;
	private boolean isFirstCharacterOfAWord;
	private boolean isCharacterAtTheBeginningOfNewLine;
	private boolean isCharacterCloseToPreviousWord;

	public CharacterFactory(boolean firstCharacterOfLineFound) {
		this.firstCharacterOfLineFound = firstCharacterOfLineFound;
	}

	public Character createCharacterFromTextPosition(final TextPosition textPosition,
			final TextPosition localPreviousTextPosition) {
		this.setPreviousTextPosition(localPreviousTextPosition);
		this.isCharacterPartOfPreviousWord = this.isCharacterPartOfPreviousWord(textPosition);
		this.isFirstCharacterOfAWord = this.isFirstCharacterOfAWord(textPosition);
		this.isCharacterAtTheBeginningOfNewLine = this.isCharacterAtTheBeginningOfNewLine(textPosition);
		this.isCharacterCloseToPreviousWord = this.isCharacterCloseToPreviousWord(textPosition);
		char character = getCharacterFromTextPosition(textPosition);
		int index = (int) textPosition.getTextPos().getXPosition()
				/ PDFLayoutTextStripper.OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT;
		return new Character(character, index, this.isCharacterPartOfPreviousWord, this.isFirstCharacterOfAWord,
				this.isCharacterAtTheBeginningOfNewLine, this.isCharacterCloseToPreviousWord);
	}

	private boolean isCharacterAtTheBeginningOfNewLine(final TextPosition textPosition) {
		if (!this.firstCharacterOfLineFound) {
			return true;
		}
		TextPosition localPreviousTextPosition = this.getPreviousTextPosition();
		double previousTextYPosition = localPreviousTextPosition.getTextPos().getYPosition();
		return (Math.round(textPosition.getTextPos().getYPosition()) < Math.round(previousTextYPosition));
	}

	private boolean isFirstCharacterOfAWord(final TextPosition textPosition) {
		if (!this.firstCharacterOfLineFound) {
			return true;
		}
		double numberOfSpaces = numberOfSpacesBetweenTwoCharacters(this.previousTextPosition, textPosition);
		return (numberOfSpaces > 1) || this.isCharacterAtTheBeginningOfNewLine(textPosition);
	}

	private boolean isCharacterCloseToPreviousWord(final TextPosition textPosition) {
		if (!this.firstCharacterOfLineFound) {
			return false;
		}
		double numberOfSpaces = numberOfSpacesBetweenTwoCharacters(this.previousTextPosition, textPosition);
		int widthOfSpace = (int) Math.ceil(textPosition.getWidthOfSpace());
		return (numberOfSpaces > 1 && numberOfSpaces <= widthOfSpace);
	}

	private boolean isCharacterPartOfPreviousWord(final TextPosition textPosition) {
		TextPosition localPreviousTextPosition = this.getPreviousTextPosition();
		if (" ".equals(localPreviousTextPosition.getCharacter())) {
			return false;
		}
		double numberOfSpaces = numberOfSpacesBetweenTwoCharacters(localPreviousTextPosition, textPosition);
		return (numberOfSpaces <= 1);
	}

	private static double numberOfSpacesBetweenTwoCharacters(final TextPosition textPosition1,
			final TextPosition textPosition2) {
		double previousTextXPosition = textPosition1.getTextPos().getXPosition();
		double previousTextWidth = textPosition1.getWidth();
		double previousTextEndXPosition = (previousTextXPosition + previousTextWidth);
		return Math.abs(Math.round(textPosition2.getTextPos().getXPosition() - previousTextEndXPosition));
	}

	private static char getCharacterFromTextPosition(final TextPosition textPosition) {
		String string = textPosition.getCharacter();
		char character = string.charAt(0);
		return character;
	}

	private TextPosition getPreviousTextPosition() {
		return this.previousTextPosition;
	}

	private void setPreviousTextPosition(final TextPosition previousTextPosition) {
		this.previousTextPosition = previousTextPosition;
	}

}
