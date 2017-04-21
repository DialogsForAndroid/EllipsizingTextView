package io.github.dialogsforandroid.ellipsizingtextview;

/*
 * Copyright (C) 2011 Micah Hainline
 *               2012 Triposo
 *               2015,2017 Simon Warta (Kullo GmbH)
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
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatTextView;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;

import net.kullo.android.R;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EllipsizingTextView extends AppCompatTextView {
    private final String ELLIPSIS = getResources().getString(R.string.text_view_ellipsis);
    private static final Pattern DEFAULT_END_PUNCTUATION = Pattern.compile("[\\.,\u2026;\\:\\s]*$", Pattern.DOTALL);

    public interface EllipsizeListener {
        void ellipsizeStateChanged(boolean ellipsized);
    }

    private final List<EllipsizeListener> ellipsizeListeners = new LinkedList<>();
    @Nullable private Boolean isEllipsized = null;
    private boolean isDirty = true;
    private boolean programmaticChange;
    private CharSequence fullText;
    private int maxLines;
    private float lineSpacingMultiplier = 1.0f;
    private float lineAdditionalVerticalPadding = 0.0f;
    /**
     * The end punctuation which will be removed when appending #ELLIPSIS.
     */
    private Pattern endPunctuationPattern;

    public EllipsizingTextView(Context context) {
        this(context, null);
    }

    public EllipsizingTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EllipsizingTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        super.setEllipsize(null);
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] { android.R.attr.maxLines });
        setMaxLines(a.getInt(0, Integer.MAX_VALUE));
        a.recycle();
        setEndPunctuationPattern(DEFAULT_END_PUNCTUATION);
    }

    public void setEndPunctuationPattern(Pattern pattern) {
        this.endPunctuationPattern = pattern;
    }

    /**
     * Reset to defaults, e.g. when used in a list where views are recycled
     */
    public void prepareForReuse() {
        ellipsizeListeners.clear();
        isEllipsized = null;
        isDirty = true;
    }

    public void addEllipsizeListener(EllipsizeListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        ellipsizeListeners.add(listener);
    }

    public void removeEllipsizeListener(EllipsizeListener listener) {
        ellipsizeListeners.remove(listener);
    }

    @Override
    public void setMaxLines(int maxLines) {
        super.setMaxLines(maxLines);
        this.maxLines = maxLines;
        isDirty = true;
    }

    @SuppressLint("Override")
    public int getMaxLines() {
        return maxLines;
    }

    public boolean ellipsizingLastFullyVisibleLine() {
        return maxLines == Integer.MAX_VALUE;
    }

    @Override
    public void setLineSpacing(float add, float mult) {
        this.lineAdditionalVerticalPadding = add;
        this.lineSpacingMultiplier = mult;
        super.setLineSpacing(add, mult);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int before,
                                 int after) {
        super.onTextChanged(text, start, before, after);
        if (!programmaticChange) {
            fullText = text;
            isDirty = true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (ellipsizingLastFullyVisibleLine()) {
            isDirty = true;
        }
    }

    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        if (ellipsizingLastFullyVisibleLine()) {
            isDirty = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isDirty) {
            resetText();
        }
        super.onDraw(canvas);
    }

    private void resetText() {
        CharSequence workingText = fullText;
        boolean ellipsized = false;
        Layout layout = createWorkingLayout(workingText);
        int linesCount = getLinesCount();
        if (layout.getLineCount() > linesCount) {
            // We have more lines of text than we are allowed to display.
            workingText = fullText.subSequence(0, layout.getLineEnd(linesCount - 1));
            while (createWorkingLayout(workingText + ELLIPSIS).getLineCount() > linesCount) {
                int lastSpace = workingText.toString().lastIndexOf(' ');
                if (lastSpace == -1) {
                    break;
                }
                workingText = workingText.subSequence(0, lastSpace);
            }
            // We should do this in the loop above, but it's cheaper this way.
            if (workingText instanceof Spannable) {
                SpannableStringBuilder builder = new SpannableStringBuilder(workingText);
                Matcher matcher = endPunctuationPattern.matcher(workingText);
                if (matcher.find()) {
                    builder.replace(matcher.start(), workingText.length(), ELLIPSIS);
                }
                workingText = builder;
            } else {
                workingText = endPunctuationPattern.matcher(workingText).replaceFirst("");
                workingText = workingText + ELLIPSIS;
            }

            ellipsized = true;
        }
        if (!workingText.equals(getText())) {
            programmaticChange = true;
            try {
                setText(workingText);
            } finally {
                programmaticChange = false;
            }
        }
        isDirty = false;
        if (isEllipsized == null || isEllipsized != ellipsized) {
            isEllipsized = ellipsized;
            for (EllipsizeListener listener : ellipsizeListeners) {
                listener.ellipsizeStateChanged(ellipsized);
            }
        }
    }

    /**
     * Get how many lines of text we are allowed to display.
     */
    private int getLinesCount() {
        if (ellipsizingLastFullyVisibleLine()) {
            int fullyVisibleLinesCount = getFullyVisibleLinesCount();
            if (fullyVisibleLinesCount == -1) {
                return 1;
            } else {
                return fullyVisibleLinesCount;
            }
        } else {
            return maxLines;
        }
    }

    /**
     * Get how many lines of text we can display so their full height is visible.
     */
    private int getFullyVisibleLinesCount() {
        Layout layout = createWorkingLayout("");
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        int lineHeight = layout.getLineBottom(0);
        return height / lineHeight;
    }

    private Layout createWorkingLayout(CharSequence workingText) {
        return new StaticLayout(workingText, getPaint(),
                getWidth() - getPaddingLeft() - getPaddingRight(),
                Alignment.ALIGN_NORMAL, lineSpacingMultiplier,
                lineAdditionalVerticalPadding, false /* includepad */);
    }

    @Override
    public void setEllipsize(TruncateAt where) {
        // Ellipsize settings are not respected
    }
}
