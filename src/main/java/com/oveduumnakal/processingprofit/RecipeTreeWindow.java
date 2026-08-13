/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oveduumnakal.processingprofit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * A standalone, resizable window showing the full graphical make-or-buy recipe tree for one product: node
 * boxes (item icon + quantity, and how each crafted step is made) connected parent-to-child top-down, a
 * left panel of filter checkboxes, and a bottom summary of the total materials and leftovers. Opened from
 * a recipe row's pop-out button. The tree is rebuilt off the EDT by a {@link TreeSupplier} whenever a
 * filter changes, so it always reflects the current price/level/holdings context.
 */
public class RecipeTreeWindow extends JFrame
{
	/**
	 * Builds a fresh tree for the given filter state. Supplied by the plugin so the window stays free of
	 * engine wiring; invoked on a background thread.
	 */
	public interface TreeSupplier
	{
		/**
		 * Builds the tree.
		 *
		 * @param options      the force-buy and depth overrides
		 * @param excludeOwned whether to collapse already-held materials
		 * @return the built tree
		 */
		ChainTree build(TreeOptions options, boolean excludeOwned);
	}

	private static final Color HIGH = new Color(100, 220, 100);
	private static final Color GOLD = new Color(255, 200, 0);
	private static final Color MUTED = new Color(150, 150, 150);
	private static final Color BOX_BG = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color BOX_BORDER = new Color(90, 90, 90);
	private static final Color LINE = new Color(110, 110, 110);

	private static final int BOX_H = 40;
	private static final int ICON_W = 20;
	private static final int ICON_H = 18;
	private static final int H_GAP = 16;
	private static final int V_GAP = 30;
	private static final int ROW_H = BOX_H + V_GAP;
	private static final int PAD = 28;
	private static final int DEPTH_LIMIT = 3;

	private final transient TreeSupplier supplier;
	private final transient ItemManager itemManager;
	private final TreeCanvas canvas;
	private final JPanel summary;

	private boolean excludeOwned = true;
	private final Set<MaterialClass> forceBuyClasses = EnumSet.noneOf(MaterialClass.class);
	private final Set<Integer> forceBuyIds = new HashSet<>();
	private boolean limitDepth;

	/**
	 * Builds the window for a product.
	 *
	 * @param product     the finished product name, shown in the title
	 * @param itemManager supplies item icons
	 * @param supplier    builds the tree for the current filters
	 */
	public RecipeTreeWindow(String product, ItemManager itemManager, TreeSupplier supplier)
	{
		super("Recipe tree — " + product);
		this.itemManager = itemManager;
		this.supplier = supplier;
		this.canvas = new TreeCanvas();
		this.summary = new JPanel();

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(900, 660);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());
		getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildFilters(), BorderLayout.WEST);

		JScrollPane scroll = new JScrollPane(canvas,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getHorizontalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
		summary.setBackground(ColorScheme.DARK_GRAY_COLOR);
		summary.setBorder(new EmptyBorder(8, 12, 10, 12));
		add(summary, BorderLayout.SOUTH);

		rebuild();
	}

	/**
	 * Opens the window on the Swing event thread.
	 *
	 * @param product     the finished product name
	 * @param itemManager supplies item icons
	 * @param supplier    builds the tree for the current filters
	 */
	public static void open(String product, ItemManager itemManager, TreeSupplier supplier)
	{
		SwingUtilities.invokeLater(() ->
		{
			RecipeTreeWindow window = new RecipeTreeWindow(product, itemManager, supplier);
			window.setVisible(true);
		});
	}

	private JPanel buildFilters()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(12, 12, 12, 12));
		panel.setPreferredSize(new Dimension(178, 0));

		JLabel header = new JLabel("Filters");
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(GOLD);
		header.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(header);
		panel.add(spacer());

		panel.add(check("Exclude already-owned", excludeOwned, v ->
		{
			excludeOwned = v;
			rebuild();
		}));
		panel.add(spacer());

		for (MaterialClass cls : MaterialClass.values())
			panel.add(classCheck(cls));

		panel.add(spacer());
		panel.add(check("Limit depth (" + DEPTH_LIMIT + ")", limitDepth, v ->
		{
			limitDepth = v;
			rebuild();
		}));

		panel.add(spacer());
		JLabel hint = new JLabel("<html>Click a node to<br>force-buy that item.</html>");
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(MUTED);
		hint.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(hint);

		return panel;
	}

	private JCheckBox check(String label, boolean selected, Consumer<Boolean> onToggle)
	{
		JCheckBox box = new JCheckBox(label, selected);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setForeground(Color.WHITE);
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setFocusPainted(false);
		box.setAlignmentX(LEFT_ALIGNMENT);
		box.addActionListener(e -> onToggle.accept(box.isSelected()));
		return box;
	}

	private JCheckBox classCheck(MaterialClass cls)
	{
		return check("Buy " + cls.label().toLowerCase(), false, v ->
		{
			if (v)
				forceBuyClasses.add(cls);
			else
				forceBuyClasses.remove(cls);

			rebuild();
		});
	}

	private static Component spacer()
	{
		return Box.createVerticalStrut(8);
	}

	private TreeOptions currentOptions()
	{
		int depth = limitDepth ? DEPTH_LIMIT : ChainTreeBuilder.MAX_DEPTH;
		Set<MaterialClass> classes = forceBuyClasses.isEmpty()
				? EnumSet.noneOf(MaterialClass.class) : EnumSet.copyOf(forceBuyClasses);
		return new TreeOptions(new HashSet<>(forceBuyIds), classes, depth);
	}

	private void rebuild()
	{
		TreeOptions options = currentOptions();
		boolean owned = excludeOwned;
		new SwingWorker<ChainTree, Void>()
		{
			@Override
			protected ChainTree doInBackground()
			{
				return supplier.build(options, owned);
			}

			@Override
			protected void done()
			{
				try
				{
					ChainTree tree = get();
					canvas.setTree(tree);
					updateSummary(tree);
				}
				catch (Exception e)
				{
					// build failed; keep the previously rendered tree
				}
			}
		}.execute();
	}

	private void updateSummary(ChainTree tree)
	{
		summary.removeAll();
		summary.add(summaryLine("Total cost", tree.getTotals(), false));
		if (!tree.getLeftovers().isEmpty())
		{
			summary.add(Box.createVerticalStrut(4));
			summary.add(summaryLine("Leftovers", tree.getLeftovers(), true));
		}

		summary.revalidate();
		summary.repaint();
	}

	private JLabel summaryLine(String title, List<MaterialLine> lines, boolean leftover)
	{
		StringJoiner joiner = new StringJoiner(", ");
		for (MaterialLine line : lines)
			joiner.add(line.getQty() + "× " + line.getName());

		String body = joiner.length() == 0 ? "none" : joiner.toString();
		JLabel label = new JLabel("<html><b style='color:#ffc800'>" + title + ":</b> " + body + "</html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(leftover ? GOLD : Color.WHITE);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private void toggleForceBuy(int itemId)
	{
		if (itemId <= 0)
			return;

		if (forceBuyIds.contains(itemId))
			forceBuyIds.remove(itemId);
		else
			forceBuyIds.add(itemId);

		rebuild();
	}

	/**
	 * The scrolling canvas that lays out and paints the graphical tree: a tidy top-down layout where each
	 * node is centred over its children, connected by elbow lines, with item icons drawn in each box.
	 */
	private final class TreeCanvas extends JComponent
	{
		private final transient Map<Integer, Image> icons = new HashMap<>();
		private transient Laid root;
		private transient List<Laid> flat = new ArrayList<>();

		TreeCanvas()
		{
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			MouseAdapter mouse = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					Laid hit = nodeAt(e.getPoint());
					if (hit != null)
						toggleForceBuy(hit.node.getItemId());
				}

				@Override
				public void mouseMoved(MouseEvent e)
				{
					boolean over = nodeAt(e.getPoint()) != null;
					setCursor(Cursor.getPredefinedCursor(over ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		void setTree(ChainTree tree)
		{
			List<ChainNode> roots = tree.getRoots();
			root = roots.isEmpty() ? null : toLaid(roots.get(0), 0);
			relayout();
			revalidate();
			repaint();
		}

		private Laid nodeAt(Point p)
		{
			for (Laid laid : flat)
			{
				if (p.x >= laid.x && p.x <= laid.x + laid.boxW && p.y >= laid.y && p.y <= laid.y + BOX_H)
					return laid;
			}

			return null;
		}

		private Laid toLaid(ChainNode node, int depth)
		{
			Laid laid = new Laid(node, depth);
			for (ChainNode child : node.getChildren())
				laid.kids.add(toLaid(child, depth + 1));

			return laid;
		}

		private void relayout()
		{
			flat = new ArrayList<>();
			if (root == null)
			{
				setPreferredSize(new Dimension(200, 120));
				return;
			}

			FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
			measure(root, fm);
			place(root, PAD);
			flatten(root);

			int maxRight = 0;
			int maxBottom = 0;
			for (Laid laid : flat)
			{
				maxRight = Math.max(maxRight, (int) (laid.x + laid.boxW));
				maxBottom = Math.max(maxBottom, (int) (laid.y + BOX_H));
			}

			setPreferredSize(new Dimension(maxRight + PAD, maxBottom + PAD));
		}

		private void measure(Laid laid, FontMetrics fm)
		{
			laid.boxW = boxWidth(laid.node, fm);
			if (laid.kids.isEmpty())
			{
				laid.subtreeW = laid.boxW;
				return;
			}

			double childrenW = -H_GAP;
			for (Laid kid : laid.kids)
			{
				measure(kid, fm);
				childrenW += kid.subtreeW + H_GAP;
			}

			laid.subtreeW = Math.max(laid.boxW, childrenW);
		}

		private void place(Laid laid, double left)
		{
			laid.y = PAD + laid.depth * ROW_H;
			if (laid.kids.isEmpty())
			{
				laid.x = left + (laid.subtreeW - laid.boxW) / 2;
				return;
			}

			double childrenW = -H_GAP;
			for (Laid kid : laid.kids)
				childrenW += kid.subtreeW + H_GAP;

			double cursor = left + (laid.subtreeW - childrenW) / 2;
			for (Laid kid : laid.kids)
			{
				place(kid, cursor);
				cursor += kid.subtreeW + H_GAP;
			}

			Laid first = laid.kids.get(0);
			Laid last = laid.kids.get(laid.kids.size() - 1);
			double firstCentre = first.x + first.boxW / 2;
			double lastCentre = last.x + last.boxW / 2;
			laid.x = (firstCentre + lastCentre) / 2 - laid.boxW / 2;
		}

		private void flatten(Laid laid)
		{
			flat.add(laid);
			for (Laid kid : laid.kids)
				flatten(kid);
		}

		private int boxWidth(ChainNode node, FontMetrics fm)
		{
			int title = fm.stringWidth(nodeTitle(node));
			String sub = nodeSubtitle(node);
			int subW = sub == null ? 0 : fm.stringWidth(sub);
			return Math.max(72, ICON_W + 6 + Math.max(title, subW) + 16);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ColorScheme.DARK_GRAY_COLOR);
			g2.fillRect(0, 0, getWidth(), getHeight());

			if (root == null)
			{
				g2.setColor(MUTED);
				g2.setFont(FontManager.getRunescapeSmallFont());
				g2.drawString("No craftable breakdown for this recipe.", PAD, PAD + 12);
				g2.dispose();
				return;
			}

			for (Laid laid : flat)
				paintConnectors(g2, laid);

			for (Laid laid : flat)
				paintNode(g2, laid);

			g2.dispose();
		}

		private void paintConnectors(Graphics2D g2, Laid parent)
		{
			if (parent.kids.isEmpty())
				return;

			g2.setColor(LINE);
			int px = (int) (parent.x + parent.boxW / 2);
			int py = (int) (parent.y + BOX_H);
			int midY = py + V_GAP / 2;
			g2.drawLine(px, py, px, midY);
			for (Laid kid : parent.kids)
			{
				int cx = (int) (kid.x + kid.boxW / 2);
				int cy = (int) kid.y;
				g2.drawLine(px, midY, cx, midY);
				g2.drawLine(cx, midY, cx, cy);
			}
		}

		private void paintNode(Graphics2D g2, Laid laid)
		{
			int x = (int) laid.x;
			int y = (int) laid.y;
			int w = (int) laid.boxW;
			ChainNode node = laid.node;

			g2.setColor(BOX_BG);
			g2.fillRoundRect(x, y, w, BOX_H, 8, 8);
			g2.setColor(node.isCovered() ? HIGH : BOX_BORDER);
			g2.drawRoundRect(x, y, w, BOX_H, 8, 8);

			Image icon = iconFor(node.getItemId());
			if (icon != null)
				g2.drawImage(icon, x + 4, y + (BOX_H - ICON_H) / 2, ICON_W, ICON_H, null);

			int textX = x + ICON_W + 8;
			g2.setFont(FontManager.getRunescapeSmallFont());
			String title = nodeTitle(node);
			String sub = nodeSubtitle(node);
			if (sub == null)
			{
				FontMetrics fm = g2.getFontMetrics();
				int baseline = y + BOX_H / 2 + 4;
				g2.setColor(node.isCovered() ? HIGH : Color.WHITE);
				g2.drawString(title, textX, baseline);
				if (node.isCovered())
				{
					int tw = fm.stringWidth(title);
					int strikeY = baseline - fm.getAscent() / 2;
					g2.drawLine(textX, strikeY, textX + tw, strikeY);
				}
			}
			else
			{
				g2.setColor(node.isCovered() ? HIGH : Color.WHITE);
				g2.drawString(title, textX, y + 16);
				g2.setColor(MUTED);
				g2.drawString(sub, textX, y + 30);
			}
		}

		private Image iconFor(int itemId)
		{
			Image cached = icons.get(itemId);
			if (cached != null)
				return cached;

			if (itemManager == null || itemId <= 0)
				return null;

			AsyncBufferedImage img = itemManager.getImage(itemId);
			icons.put(itemId, img);
			img.onLoaded(this::repaint);
			return img;
		}

		private String nodeTitle(ChainNode node)
		{
			return node.getQty() + "× " + node.getLabel();
		}

		private String nodeSubtitle(ChainNode node)
		{
			if (node.isCovered())
				return null;

			if (node.getViaSkill() != null)
			{
				String base = "via " + node.getViaSkill();
				if (!node.getTools().isEmpty())
					base += " · " + String.join(", ", node.getTools());

				return node.isTruncated() ? base + " …" : base;
			}

			if (node.isViaBuy())
				return node.getBuyUnit() > 0
						? "buy " + QuantityFormatter.quantityToStackSize(node.getBuyUnit()) + " ea" : "gather";

			return null;
		}
	}

	/**
	 * A node with its computed layout geometry: its box position/width, its subtree width, and its laid-out
	 * children. Mutable scratch state used only during a single layout pass.
	 */
	private static final class Laid
	{
		private final transient ChainNode node;
		private final int depth;
		private final transient List<Laid> kids = new ArrayList<>();
		private double x;
		private double y;
		private double boxW;
		private double subtreeW;

		Laid(ChainNode node, int depth)
		{
			this.node = node;
			this.depth = depth;
		}
	}
}
