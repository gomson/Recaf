package me.coley.recaf.ui.component.panel;

import java.awt.BorderLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import org.objectweb.asm.tree.AbstractInsnNode;
import me.coley.recaf.Recaf;
import me.coley.recaf.ui.component.LabeledComponent;
import me.coley.recaf.ui.component.action.ActionButton;
import me.coley.recaf.ui.component.tree.ASMFieldTreeNode;
import me.coley.recaf.ui.component.tree.ASMInsnTreeNode;
import me.coley.recaf.ui.component.tree.ASMMethodTreeNode;
import me.coley.recaf.ui.component.tree.ASMTreeNode;
import me.coley.recaf.ui.component.tree.JavaTreeListener;
import me.coley.recaf.ui.component.tree.JavaTreeRenderer;
import me.coley.recaf.util.Misc;
import me.coley.recaf.util.StreamUtil;

@SuppressWarnings("serial")
public class SearchPanel extends JPanel {
	private static final String[] DEFAULT = new String[5];
	public static final int S_STRINGS = 0;
	public static final int S_FIELD = 10;
	public static final int S_METHOD = 20;
	public static final int S_CLASS_NAME = 30, S_CLASS_REF = 31;
	private final Recaf recaf = Recaf.INSTANCE;
	private final JTree tree = new JTree(new String[] {});

	public SearchPanel(int type) {
		this(type, DEFAULT);
	}

	public SearchPanel(int type, String[] defaults) {
		setLayout(new BorderLayout());
		if (defaults.length == 0) {
			defaults = DEFAULT;
		}
		JPanel pnlInput = new JPanel(), pnlOutput = new JPanel();
		pnlInput.setLayout(new BoxLayout(pnlInput, BoxLayout.Y_AXIS));
		pnlOutput.setLayout(new BorderLayout());
		JScrollPane scrollTree = new JScrollPane(tree);
		pnlOutput.add(scrollTree, BorderLayout.CENTER);
		tree.setCellRenderer(new JavaTreeRenderer());
		JavaTreeListener sel = new JavaTreeListener();
		tree.addTreeSelectionListener(sel);
		tree.addMouseListener(sel);
		tree.addTreeExpansionListener(sel);
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pnlInput, pnlOutput);
		split.setResizeWeight(0.67);
		ActionButton btn = null;
		// @formatter:off
		switch (type) {
		case S_STRINGS: {
			JTextField text;
			JCheckBox caseSense;
			pnlInput.add(new LabeledComponent("String", text = new JTextField(defaults[0])));
			pnlInput.add(caseSense = new JCheckBox("Case sensitive", Boolean.parseBoolean(defaults[1])));
			pnlInput.add(btn = new ActionButton("Search", () -> searchString(text.getText(), caseSense.isSelected())));
			break;
		}
		case S_FIELD: {
			JTextField name, desc;
			pnlInput.add(new LabeledComponent("Field name", name = new JTextField(defaults[0])));
			pnlInput.add(new LabeledComponent("Field desc", desc = new JTextField(defaults[1])));
			pnlInput.add(btn = new ActionButton("Search", () -> searchField(name.getText(), desc.getText())));
			break;
		}
		case S_METHOD: {
			JTextField name, desc;
			pnlInput.add(new LabeledComponent("Method name", name = new JTextField(defaults[0])));
			pnlInput.add(new LabeledComponent("Method desc", desc = new JTextField(defaults[1])));
			pnlInput.add(btn = new ActionButton("Search", () -> searchMethod(name.getText(), desc.getText())));
			break;
		}
		case S_CLASS_NAME: {
			JTextField clazz;
			JCheckBox ex;
			pnlInput.add(new LabeledComponent("Class name", clazz = new JTextField(defaults[0])));
			pnlInput.add(ex = new JCheckBox("Exact match", Boolean.parseBoolean(defaults[1])));
			pnlInput.add(btn = new ActionButton("Search", () -> searchClass(clazz.getText(), ex.isSelected())));
			break;
		}
		case S_CLASS_REF: {
			JTextField clazz, name, desc;
			JCheckBox ex;
			pnlInput.add(new LabeledComponent("Class owner", clazz = new JTextField(defaults[0])));
			pnlInput.add(new LabeledComponent("Member name", name = new JTextField(defaults[1])));
			pnlInput.add(new LabeledComponent("Member desc", desc = new JTextField(defaults[2])));
			pnlInput.add(ex = new JCheckBox("Exact match", Boolean.parseBoolean(defaults[3])));
			pnlInput.add(btn = new ActionButton("Search", () -> searchClassRef(clazz.getText(), name.getText(), desc.getText(), ex
					.isSelected())));
			break;
		}
		}
		// @formatter:on
		add(split, BorderLayout.CENTER);
		// Defaults not given, implied the search was intended from
		// instantiation.
		if (defaults != DEFAULT) {
			btn.doClick();
		}
	}

	private void searchString(String text, boolean caseSensitive) {
		DefaultTreeModel model = setup();
		search((n) -> {
			for (MethodNode m : n.methods) {
				for (AbstractInsnNode ain : m.instructions.toArray()) {
					if (ain.getType() == AbstractInsnNode.LDC_INSN) {
						LdcInsnNode ldc = (LdcInsnNode) ain;
						// TODO: Allow users to search for non-string LDC
						// values.
						if (!(ldc.cst instanceof String)) {
							continue;
						}
						String s = (String) ldc.cst;
						if ((caseSensitive && s.contains(text)) || (!caseSensitive && (s.toLowerCase().contains(text
								.toLowerCase())))) {
							// Get tree node for class
							ASMTreeNode genClass = Misc.getOrCreateNode(model, n);

							// Get or create tree node for method
							ASMTreeNode genMethod = genClass.getChild(m.name);
							if (genMethod == null) {
								genClass.addChild(m.name, genMethod = new ASMMethodTreeNode(m.name + m.desc, n, m));
								genClass.add(genMethod);
							}
							// Add opcode node to method tree node
							genMethod.add(new ASMInsnTreeNode(m.instructions.indexOf(ain) + ": '" + s + "'", n, m, ain));
						}
					}
				}
			}
		});
		setTreeModel(model);
	}

	private void searchField(String name, String desc) {
		DefaultTreeModel model = setup();
		search((n) -> {
			for (FieldNode f : n.fields) {
				if (f.name.contains(name) && f.desc.contains(desc)) {
					ASMTreeNode genClass = Misc.getOrCreateNode(model, n);
					ASMTreeNode genMethod = genClass.getChild(f.name);
					if (genMethod == null) {
						genMethod = new ASMFieldTreeNode(f.desc + " " + f.name, n, f);
					}
					genClass.add(genMethod);
				}
			}
		});
		setTreeModel(model);
	}

	private void searchMethod(String name, String desc) {
		DefaultTreeModel model = setup();
		search((n) -> {
			for (MethodNode m : n.methods) {
				if (m.name.contains(name) && m.desc.contains(desc)) {
					ASMTreeNode genClass = Misc.getOrCreateNode(model, n);
					ASMTreeNode genMethod = genClass.getChild(m.name);
					if (genMethod == null) {
						genMethod = new ASMMethodTreeNode(m.name + m.desc, n, m);
					}
					genClass.add(genMethod);
				}
			}
		});
		setTreeModel(model);
	}

	private void searchClass(String text, boolean exact) {
		DefaultTreeModel model = setup();
		search((n) -> {
			if (exact ? n.name.equals(text) : n.name.contains(text)) {
				Misc.getOrCreateNode(model, n);
			}
		});
		setTreeModel(model);
	}

	private void searchClassRef(String owner, String name, String desc, boolean exact) {
		DefaultTreeModel model = setup();
		search((n) -> {
			for (MethodNode m : n.methods) {
				for (AbstractInsnNode ain : m.instructions.toArray()) {
					if (ain.getType() == AbstractInsnNode.FIELD_INSN) {
						FieldInsnNode fin = (FieldInsnNode) ain;
						if ((exact && (fin.owner.equals(owner) && fin.name.equals(name) && fin.desc.equals(desc))) || (!exact
								&& (fin.owner.contains(owner) && fin.name.contains(name) && fin.desc.contains(desc)))) {
							ASMTreeNode genClass = Misc.getOrCreateNode(model, n);
							// Get or create tree node for method
							ASMTreeNode genMethod = genClass.getChild(m.name);
							if (genMethod == null) {
								genClass.addChild(m.name, genMethod = new ASMMethodTreeNode(m.name + m.desc, n, m));
								genClass.add(genMethod);
							}
							// Add opcode node to method tree node
							genMethod.add(new ASMInsnTreeNode(m.instructions.indexOf(ain) + ": " + fin.name, n, m, ain));
						}
					} else if (ain.getType() == AbstractInsnNode.METHOD_INSN) {
						MethodInsnNode min = (MethodInsnNode) ain;
						if ((exact && (min.owner.equals(owner) && min.name.equals(name) && min.desc.equals(desc))) || (!exact
								&& (min.owner.contains(owner) && min.name.contains(name) && min.desc.contains(desc)))) {
							// Get tree node for class
							ASMTreeNode genClass = Misc.getOrCreateNode(model, n);
							// Get or create tree node for method
							ASMTreeNode genMethod = genClass.getChild(m.name);
							if (genMethod == null) {
								genClass.addChild(m.name, genMethod = new ASMMethodTreeNode(m.name + m.desc, n, m));
								genClass.add(genMethod);
							}
							// Add opcode node to method tree node
							genMethod.add(new ASMInsnTreeNode(m.instructions.indexOf(ain) + ": " + min.name, n, m, ain));
						}
					}
				}
			}
		});
		setTreeModel(model);
	}

	private void setTreeModel(DefaultTreeModel model) {
		tree.setModel(model);
		expandAllNodes(tree, 0, tree.getRowCount());
	}

	/**
	 * Setup and return the tree model for a search.
	 *
	 * @return
	 */
	private DefaultTreeModel setup() {
		String jarName = recaf.currentJar.getName();
		ASMTreeNode root = new ASMTreeNode(jarName, null);
		DefaultTreeModel model = new DefaultTreeModel(root);
		model.setRoot(root);
		return model;
	}

	private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
		for (int i = startingIndex; i < rowCount; ++i) {
			tree.expandRow(i);
		}
		if (tree.getRowCount() != rowCount) {
			expandAllNodes(tree, rowCount, tree.getRowCount());
		}
	}

	/**
	 * Search and pass classnodes through the given function.
	 *
	 * @param model
	 * @param func
	 */
	private void search(Consumer<ClassNode> func) {
		List<String> names = StreamUtil.listOfSortedJavaNames(recaf.jarData.classes.keySet());
		for (String className : names) {
			ClassNode node = recaf.jarData.classes.get(className);
			func.accept(node);
		}
	}
}
