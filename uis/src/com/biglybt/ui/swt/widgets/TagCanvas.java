/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.widgets;

import java.io.File;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mainwindow.HSLColor;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.util.StringCompareUtils;

public class TagCanvas
	extends Canvas
	implements PaintListener, Listener
{
	private static final Point MAX_IMAGE_SIZE = new Point(40, 28);

	public interface TagButtonTrigger
	{
		void tagButtonTriggered(Tag tag, boolean doTag);

		Boolean tagSelectedOverride(Tag tag);
	}

	private static final int DEF_CURVE_WIDTH = 25;

	private static final int COMPACT_CURVE_WIDTH = 15;

	private static final int DEF_PADDING_IMAGE_X = 5;

	private static final int COMPACT_PADDING_IMAGE_X = 0;

	private static final int DEF_PADDING_IMAGE_Y = 2;

	private static final int COMPACT_PADDING_IMAGE_Y = 1;

	private static final int DEF_CONTENT_PADDING_Y = 2;

	private static final int COMPACT_CONTENT_PADDING_Y = 2;

	private static final int DEF_CONTENT_PADDING_X0 = 5;

	private static final int COMPACT_CONTENT_PADDING_X0 = 3;

	private static final int DEF_CONTENT_PADDING_X1 = 8;

	private static final int COMPACT_CONTENT_PADDING_X1 = 5;

	private static final int MIN_WIDTH = 25;

	private int paddingContentY = DEF_CONTENT_PADDING_Y;

	private int paddingContentX0 = DEF_CONTENT_PADDING_X0;

	private int paddingContentX1 = DEF_CONTENT_PADDING_X1;

	private int paddingImageX = DEF_PADDING_IMAGE_X;

	private int paddingImageY = DEF_PADDING_IMAGE_Y;

	private int curveWidth = DEF_CURVE_WIDTH;

	private Image image;

	private String imageID;

	private boolean selected;

	private final Tag tag;

	private final boolean isTagAuto;

	private String lastUsedName;

	private boolean grayed;

	private boolean disableAuto;

	private boolean enableWhenNoTaggables;

	private TagButtonTrigger trigger;

	private boolean compact;

	private boolean showImage = true;

	private Font font = null;

	private Color colorTagFaded;

	private Color colorTag;

	/** 
	 * Creates a Tag Canvas.<br/>  
	 * Auto Tags will be disabled.<br/>
	 * When Tag has no taggables, it will be disabled.
	 */
	public TagCanvas(Composite parent, Tag tag) {
		this(parent, tag, true, false);
	}

	public TagCanvas(Composite parent, Tag tag, boolean disableAuto,
			boolean enableWhenNoTaggables) {
		super(parent, SWT.DOUBLE_BUFFERED);
		this.tag = tag;
		this.enableWhenNoTaggables = enableWhenNoTaggables;

		boolean[] auto = tag.isTagAuto();
		isTagAuto = auto.length >= 2 && auto[0] && auto[1];
		if (isTagAuto) {
			font = FontUtils.getFontWithStyle(getFont(), SWT.ITALIC, 1.0f);
			setFont(font);
		}

		updateColors();

		setDisableAuto(disableAuto);

		addListener(SWT.MouseDown, this);
		addListener(SWT.MouseUp, this);
		addListener(SWT.KeyDown, this);
		addListener(SWT.FocusOut, this);
		addListener(SWT.FocusIn, this);
		addListener(SWT.Traverse, this);
		addListener(SWT.MouseHover, this);
		addListener(SWT.MouseExit, this);

		updateImage();
		addPaintListener(this);
	}

	@Override
	public Point computeSize(int wHint, int hHint, boolean changed) {
		if (lastUsedName == null) {
			lastUsedName = tag.getTagName(true);
		}

		GC gc = new GC(getDisplay());
		gc.setFont(getFont());

		gc.setTextAntialias(SWT.ON);
		GCStringPrinter sp = new GCStringPrinter(gc, lastUsedName,
				new Rectangle(0, 0, 9999, 9999), false, true, SWT.LEFT);
		sp.calculateMetrics();
		Point size = sp.getCalculatedSize();
		gc.dispose();

		if (size == null) {
			return super.computeSize(wHint, hHint, changed);
		}
		size.x += paddingContentX0 + paddingContentX1;
		size.y += paddingContentY + paddingContentY;

		if (showImage && image != null && !image.isDisposed()) {
			Rectangle bounds = image.getBounds();
			int imageW = (bounds.width * (size.y - paddingContentY - paddingContentY))
					/ bounds.height;
			size.x += imageW + paddingImageX;
		}

		size.x = Math.max(MIN_WIDTH, size.x);

		return size;
	}

	@Override
	public void handleEvent(Event e) {
		switch (e.type) {
			case SWT.MouseDown: {
				if (!getEnabled() || e.button != 1) {
					return;
				}
				if (trigger != null) {
					trigger.tagButtonTriggered(tag, !isSelected());
				}
				redraw();
				break;
			}
			case SWT.MouseUp: {
				if (!getEnabled() || e.button != 1) {
					return;
				}
				getAccessible().setFocus(ACC.CHILDID_SELF);
				break;
			}
			case SWT.MouseHover: {
				Utils.setTT(this, TagUtils.getTagTooltip(tag));
				break;
			}
			case SWT.MouseExit: {
				setToolTipText(null);
			}
			case SWT.FocusOut:
			case SWT.FocusIn: {
				redraw();
				break;
			}
			case SWT.Traverse: {
				if (!getEnabled()) {
					return;
				}
				switch (e.detail) {
					case SWT.TRAVERSE_PAGE_NEXT:
					case SWT.TRAVERSE_PAGE_PREVIOUS:
						e.doit = false;
						return;
					case SWT.TRAVERSE_RETURN:
						if (trigger != null) {
							trigger.tagButtonTriggered(tag, !isSelected());
							redraw();
						}
				}
				e.doit = true;
			}
			case SWT.KeyDown: {
				if (e.keyCode == SWT.F2 && (e.stateMask & SWT.MODIFIER_MASK) == 0) {
					if (!tag.getTagType().isTagTypeAuto()) {
						TagUIUtils.openRenameTagDialog(tag);
						e.doit = false;
					}
				}
			}
		}
	}

	public void setDisableAuto(boolean disableAuto) {
		if (this.disableAuto == disableAuto) {
			return;
		}
		this.disableAuto = disableAuto;

		boolean enable = !isTagAuto || !disableAuto;
		setEnabled(enable);
	}

	public boolean isDisableAuto() {
		return disableAuto;
	}

	public void setImage(Image newImage, String key) {
		if (!com.biglybt.ui.swt.imageloader.ImageLoader.isRealImage(newImage)) {
			return;
		}
		if (newImage == image && StringCompareUtils.equals(key, imageID)) {
			return;
		}
		if (imageID != null) {
			com.biglybt.ui.swt.imageloader.ImageLoader.getInstance().releaseImage(
					imageID);
		}
		this.image = newImage;
		this.imageID = key;
		requestLayout();
		redraw();
	}

	public Tag getTag() {
		return tag;
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean select) {
		setSelected(select, true);
	}

	private void setSelected(boolean select, boolean unGray) {
		if (select != selected) {
			selected = select;
			if (grayed && unGray) {
				grayed = false;
			}
			redraw();
		}
	}

	@Override
	public void paintControl(PaintEvent e) {
		if (lastUsedName == null) {
			return;
		}

		Rectangle clientArea = getClientArea();
		//System.out.println("paint " + lastUsedName + "; " + clientArea + "; " + e);

		boolean selected = isSelected();
		boolean focused = isFocusControl();

		Color colorOrigBG = e.gc.getBackground();

		Color colorText = Colors.getInstance().getReadableColor(selected?
						grayed ? colorTagFaded : colorTag : colorOrigBG);

		Point size = getSize();
		e.gc.setAntialias(SWT.ON);

		if (selected) {
			e.gc.setBackground(grayed ? colorTagFaded : colorTag);
			e.gc.fillRoundRectangle(-curveWidth, 0, size.x + curveWidth, size.y,
					curveWidth, curveWidth);
		}

		if (focused || !selected || grayed) {
			int lineWidth = 2;
			int width = size.x - (lineWidth / 2);
			e.gc.setLineWidth(lineWidth);
			if (focused) {
				e.gc.setForeground(selected ? colorOrigBG : colorTag);
				e.gc.setLineStyle(SWT.LINE_DOT);
			} else {
				e.gc.setForeground(colorTag);
				e.gc.setLineStyle(SWT.LINE_SOLID);
			}
			e.gc.drawRoundRectangle(-curveWidth, lineWidth - 1, width + curveWidth,
					size.y - lineWidth, curveWidth, curveWidth);
			e.gc.drawLine(lineWidth - 1, lineWidth, lineWidth - 1,
					size.y - lineWidth);
			e.gc.setLineWidth(1);
		}

		clientArea.x += paddingContentX0;
		clientArea.width = clientArea.width - paddingContentX0;
		if (showImage && image != null) {
			Rectangle bounds = image.getBounds();
			int imageH = size.y - paddingImageY - paddingImageY;
			int imageW = (bounds.width * imageH) / bounds.height;

			e.gc.drawImage(image, 0, 0, bounds.width, bounds.height, clientArea.x,
					clientArea.y + paddingImageY, imageW, imageH);
			clientArea.x += imageW + paddingImageX;
			clientArea.width -= imageW - paddingImageX;
		}
		e.gc.setForeground(colorText);
		clientArea.y += paddingContentY;
		clientArea.height -= (paddingContentY + paddingContentY);
		GCStringPrinter sp = new GCStringPrinter(e.gc, lastUsedName, clientArea,
				true, true, SWT.LEFT);
		sp.printString();
	}

	@Override
	public void dispose() {
		super.dispose();
		if (imageID != null) {
			com.biglybt.ui.swt.imageloader.ImageLoader.getInstance().releaseImage(
					imageID);
			imageID = null;
			image = null;
		}
		if (font != null) {
			font.dispose();
			font = null;
		}
	}

	public void updateName() {
		String tagName = tag.getTagName(true);
		if (!tagName.equals(lastUsedName)) {
			lastUsedName = null;
			requestLayout();
			redraw();
		}
	}

	public void setGrayed(boolean b) {
		if (b == grayed) {
			return;
		}
		grayed = b;
		redraw();
	}

	private void updateColors() {
		Display display = getDisplay();
		Color newColorTag = ColorCache.getColor(display, tag.getColor());
		colorTag = newColorTag == null ? getForeground() : newColorTag;

		HSLColor hslColor = new HSLColor();
		hslColor.initHSLbyRGB(colorTag.getRed(), colorTag.getGreen(),
				colorTag.getBlue());
		Color colorWidgetBG = getBackground();
		hslColor.blend(colorWidgetBG.getRed(), colorWidgetBG.getGreen(),
				colorWidgetBG.getBlue(), 0.75f);
		Color newColorTagFaded = ColorCache.getColor(display, hslColor.getRed(),
				hslColor.getGreen(), hslColor.getBlue());
		colorTagFaded = newColorTagFaded == null ? getForeground()
				: newColorTagFaded;
	}

	public void updateState(List<Taggable> taggables) {
		updateImage();
		updateName();
		updateColors();

		if (taggables == null) {
			setEnabled(enableWhenNoTaggables);
			if (!enableWhenNoTaggables) {
				setSelected(false, false);
				return;
			}
		}

		boolean hasTag = false;
		boolean hasNoTag = false;

		Boolean override = trigger == null ? null
				: trigger.tagSelectedOverride(tag);

		if (taggables != null && override == null) {
			for (Taggable taggable : taggables) {
				boolean curHasTag = tag.hasTaggable(taggable);
				if (!hasTag && curHasTag) {
					hasTag = true;
					if (hasNoTag) {
						break;
					}
				} else if (!hasNoTag && !curHasTag) {
					hasNoTag = true;
					if (hasTag) {
						break;
					}
				}
			}
		} else if (override != null) {
			hasNoTag = !override;
			hasTag = override;
		}

		boolean[] auto = tag.isTagAuto();

		boolean auto_add = auto[0];
		boolean auto_rem = auto[1];

		if (hasTag && hasNoTag) {
			setEnabled(!auto_add);

			setGrayed(true);
			setSelected(true, false);
		} else {

			if (auto_add && auto_rem) {
				setGrayed(!hasTag);
				setEnabled(false);
			} else {
				setEnabled((hasTag) || (!hasTag && !auto_add));
				setGrayed(false);
			}

			setSelected(hasTag, false);
		}
	}

	@Override
	public void setEnabled(boolean enabled) {
		boolean wasEnabled = isEnabled();
		super.setEnabled(enabled);
		if (wasEnabled != enabled) {
			redraw();
		}
	}

	private void updateImage() {
		String iconFile = tag.getImageFile();
		if (iconFile != null) {
			try {
				String resource = new File(iconFile).toURI().toURL().toExternalForm();

				ImageLoader.getInstance().getUrlImage(resource, MAX_IMAGE_SIZE,
						(image, key, returnedImmediately) -> {
							if (image == null) {
								return;
							}

							Utils.execSWTThread(() -> setImage(image, key));
						});
			} catch (Throwable e) {
				Debug.out(e);
			}
		} else {
			String id = tag.getImageID();
			if (id != null) {
				Image image = ImageLoader.getInstance().getImage(id);
				setImage(image, id);
			} else {
				setImage(null, null);
			}
		}
	}

	public void setEnableWhenNoTaggables(boolean enableWhenNoTaggables) {
		if (this.enableWhenNoTaggables == enableWhenNoTaggables) {
			return;
		}
		this.enableWhenNoTaggables = enableWhenNoTaggables;
		redraw();
	}

	public boolean getEnableWhenNoTaggables() {
		return enableWhenNoTaggables;
	}

	public void setTrigger(TagButtonTrigger trigger) {
		this.trigger = trigger;
	}

	public void setCompact(boolean compact) {
		if (this.compact == compact) {
			return;
		}
		this.compact = compact;
		if (compact) {
			paddingImageX = COMPACT_PADDING_IMAGE_X;
			paddingImageY = COMPACT_PADDING_IMAGE_Y;
			paddingContentY = COMPACT_CONTENT_PADDING_Y;
			paddingContentX0 = COMPACT_CONTENT_PADDING_X0;
			paddingContentX1 = COMPACT_CONTENT_PADDING_X1;
			curveWidth = COMPACT_CURVE_WIDTH;
			showImage = false;
		} else {
			paddingImageX = DEF_PADDING_IMAGE_X;
			paddingImageY = DEF_PADDING_IMAGE_Y;
			paddingContentY = DEF_CONTENT_PADDING_Y;
			paddingContentX0 = DEF_CONTENT_PADDING_X0;
			paddingContentX1 = DEF_CONTENT_PADDING_X1;
			curveWidth = DEF_CURVE_WIDTH;
			showImage = true;
		}
		requestLayout();
	}

	public boolean isCompact() {
		return compact;
	}
}
