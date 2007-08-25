//  $Revision: 1.9 $
//  $Date: 2006/02/27 19:59:18 $
//  $Author: bizi $
//---------------------------------------------------------------------------
package org.hypergraphdb.viewer;

import giny.model.Edge;
import giny.model.Node;
import giny.view.GraphView;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import javax.swing.*;
import javax.swing.event.SwingPropertyChangeSupport;
import org.hypergraphdb.query.HGAtomPredicate;
import org.hypergraphdb.query.HGQueryCondition;
import org.hypergraphdb.viewer.actions.FitContentAction;
import org.hypergraphdb.viewer.actions.ZoomAction;
import org.hypergraphdb.viewer.giny.HGViewerRootGraph;
import org.hypergraphdb.viewer.giny.HGViewerFingRootGraph;
import org.hypergraphdb.viewer.hg.HGWNReader;
import org.hypergraphdb.viewer.layout.*;
import org.hypergraphdb.viewer.layout.SpringLayout;
import org.hypergraphdb.viewer.util.HGVNetworkNaming;
import org.hypergraphdb.viewer.view.GraphViewController;
import org.hypergraphdb.viewer.view.HGVNetworkView;
import org.hypergraphdb.viewer.view.HGVDesktop;
import phoebe.PGraphView;
import org.hypergraphdb.*;

/**
 * This class, HGViewer is <i>the</i> primary class in the API.
 * 
 * All Nodes and Edges must be created using the methods getHGVNode and
 * getHGVEdge, available only in this class. Once a node or edge is created
 * using these methods it can then be added to a HGVNetwork, where it can be
 * used algorithmically.<BR>
 * <BR>
 */
public abstract class HGViewer {
	public static String NETWORK_CREATED = "NETWORK_CREATED";
	public static String NETWORK_DESTROYED = "NETWORK_DESTROYED";
	public static String EXIT = "EXIT";
	// constants for tracking selection mode globally
	public static final int SELECT_NODES_ONLY = 1;
	public static final int SELECT_EDGES_ONLY = 2;
	public static final int SELECT_NODES_AND_EDGES = 3;
	// global to represent which selection mode is active
	private static int currentSelectionMode = SELECT_NODES_ONLY;
	// global flag to indicate if Squiggle is turned on
	private static boolean squiggleEnabled = false;
	/**
	 * The shared RootGraph between all Networks
	 */
	protected static HGViewerRootGraph/*RootGraph*/ rootGraph;
	protected static Object pcsO = new Object();
	protected static SwingPropertyChangeSupport pcs = 
		new SwingPropertyChangeSupport(pcsO);
	protected static Map<HGVNetwork, HGVNetworkView> networkMap;
	protected static HGVDesktop defaultDesktop;
	protected static HGVNetworkView currentView;
	protected static HGVNetwork currentNetwork;
	protected static Layout prefered_layout;
	protected static Set<Layout> layouts = new HashSet<Layout>();
	
	static {
		Layout l = new Radial(); 
		layouts.add(new GEM());
		layouts.add(new HierarchicalLayout());
		layouts.add(new SpringLayout());
		layouts.add(l);
		setPreferedLayout(l);
	}

	/**
	 * The GraphViewController for all NetworkViews that we know about
	 */
	protected static GraphViewController graphViewController;

	public static GraphViewController getGraphViewController() {
		if (graphViewController == null)
			graphViewController = new GraphViewController();
        return graphViewController;
	}

	static boolean embeded = false;
	public static boolean isEmbeded(){
		return embeded;
	}
	/**
	 * Shuts down HGViewer, after giving plugins time to react.
	 */
	public static void exit() {
		try {
			firePropertyChange(EXIT, null, "now");
		} catch (Exception e) {
			System.out.println("Errors on close, closed anyways.");
		}
		System.exit(0);
	}

	// --------------------//
	// Root Graph Methods
	// --------------------//
	/**
	 * Bound events are:
	 * <ol>
	 * <li>NETWORK_CREATED
	 * <li>NETWORK_DESTROYED
	 * </ol>
	 */
	public static SwingPropertyChangeSupport getSwingPropertyChangeSupport() {
		return pcs;
	}

	/**
	 * Return the HGViewerRootGraph
	 */
	public static HGViewerRootGraph getRootGraph() {
		if (rootGraph == null)
			rootGraph = new HGViewerFingRootGraph();
		return rootGraph;
	}

	/**
	 * @param alias
	 *            an alias of a node
	 * @return will return a node, if one exists for the given alias
	 */
	public static HGVNode getHGVNode(HGPersistentHandle handle) {
		return getHGVNode(handle, false);
	}

	/**
	 * @param alias
	 *            an alias of a node
	 * @param create
	 *            will create a node if one does not exist
	 * @return will always return a node, if <code>create</code> is true
	 */
	public static HGVNode getHGVNode(HGPersistentHandle handle, boolean create) {
		HGVNode node = HGViewer.getRootGraph().getNode(handle);
		if (node != null) return node;
		if (!create)
			return null;
		int n = HGViewer.getRootGraph().createNode();
		node = (HGVNode) HGViewer.getRootGraph().getNode(n);
		node.setHandle(handle);
		// System.out.println( "Create node: " + alias + " node: " +
		// HGViewer.getRootGraph().getNode(alias));
		return node;
	}

	/**
	 * Gets the first HGVEdge found.
	 * 
	 * @param node_1
	 *            one end of the edge
	 * @param node_2
	 *            the other end of the edge
	 * @param type
	 *            the type of the edge
	 * @param create
	 *            will create an edge if one does not exist
	 * @return returns an existing HGVEdge if present, or creates one if
	 *         <code>create</code> is true, otherwise returns null.
	 */
	public static HGVEdge getHGVEdge(Node node_1, Node node_2, boolean create)
	{
		// System.out.println("node_1: " + node_1 + " node_2: " + node_2
		// + "attribute_value" + create + "create");
		if (getRootGraph().getEdgeCount() != 0) {
			int[] n1Edges = getRootGraph().getAdjacentEdgeIndicesArray(
					node_1.getRootGraphIndex(), true, true, true);
			for (int i = 0; i < n1Edges.length; i++) {
				HGVEdge edge = (HGVEdge) getRootGraph().getEdge(n1Edges[i]);
				HGVNode otherNode = (HGVNode) edge.getTarget();
				if (otherNode.getRootGraphIndex() == node_1.getRootGraphIndex()) {
					otherNode = (HGVNode) edge.getSource();
				}
				if (otherNode.getRootGraphIndex() == node_2.getRootGraphIndex()) {
					// TODO: maybe we could add an option to hide those
					// reflexive pointers
					// i.e. to not perform the following check
					//if (type.equals(edge.getHandle()))
					//	return edge;
					//if (HGViewer.getBooleanProperty("hideReflexiveEdges", true))
					return edge;
				}
			}// for i
		}
		if (!create)
			return null;
		// create the edge
		HGVEdge edge = (HGVEdge) getRootGraph().getEdge(
				getRootGraph().createEdge(node_1, node_2));
		return edge;
	}

	/**
	 * @param source_alias
	 *            an alias of a node
	 * @param edge_name
	 *            the name of the node
	 * @param target_alias
	 *            an alias of a node
	 * @return will always return an edge
	 */
	public static HGVEdge getHGVEdge(HGPersistentHandle source_alias,
			HGPersistentHandle target_alias) 
	{
		// System.out.println("getHGVEdge - source: " + source_alias + " edge: "
		// + edge_name
		// + " target:" + target_alias);
		// edge does not exist, create one
		// System.out.print( "*" );
		HGVNode source = getHGVNode(source_alias, true);
		HGVNode target = getHGVNode(target_alias, true);
		if (source == null || target == null)
			return null; // TODO: ???
		// throw new NullPointerException("Can't create HGVEdge - source: " +
		// source + " target: " + target);
		return getHGVEdge(source, target, true);
	}

	// --------------------//
	// Network Methods
	// --------------------//
	/**
	 * Return the Network that currently has the Focus. Can be different from
	 * getCurrentNetworkView
	 */
	public static HGVNetwork getCurrentNetwork() {
		return currentNetwork;
	}

	/**
	 */
	public static void setCurrentNetwork(HGVNetwork net) {
		if (getNetworkMap().containsKey(net))
			currentNetwork = net;
		// System.out.println( "Currentnetworkid is: "+currentNetworkID+ " set
		// from : "+id );
	}

	/**
	 * @return true if there is network view, false if not
	 */
	public static boolean setCurrentView(HGVNetworkView view) {
		currentView = view;
		if(view == null) return false;
		if (getNetworkMap().containsKey(view.getNetwork())) {
			currentNetwork = view.getNetwork();
			return true;
		}
		return false;
	}

	/**
	 * Return a List of all available HGVNetworks
	 */
	public static Set<HGVNetwork> getNetworkSet() {
		return new java.util.LinkedHashSet<HGVNetwork>(getNetworkMap().keySet());
	}

	public static HGVNetwork getNetworkByFile(File file) {
		for (HGVNetwork n : getNetworkMap().keySet()) {
			if(n.getHyperGraph() != null &&
					file.equals(new File(
							n.getHyperGraph().getStore().getDatabaseLocation())))
				return n;
		}
		return null;
	}

	/**
	 * @return a HGVNetworkView for the given HGVNetworkView, if one exists, otherwise
	 *         returns null
	 */
	public static HGVNetworkView getNetworkView(HGVNetwork net) {
		return getNetworkMap().get(net);
	}

	/**
	 * @return if a view exists for a given network id
	 */
	public static boolean viewExists(HGVNetwork net) {
		return getNetworkMap().get(net) != null;
	}

	/**
	 * Return the HGVNetworkView that currently has the focus. Can be different
	 * from getCurrentNetwork
	 */
	public static HGVNetworkView getCurrentView() {
		return currentView;
	}

	/**
	 * @return the reference to the One CytoscapeDesktop
	 */
	public static HGVDesktop getDesktop() {
		if (defaultDesktop == null)
			defaultDesktop = new HGVDesktop();
		return defaultDesktop;
	}

	/**
	 * This Map has keys that are Strings ( network_ids ) and values that are
	 * networks.
	 */
	public static Map<HGVNetwork, HGVNetworkView> getNetworkMap() {
		if (networkMap == null) {
			networkMap = new HashMap<HGVNetwork, HGVNetworkView>();
		}
		return networkMap;
	}

	
	/**
	 * destroys the given network
	 */
	public static void destroyNetwork(HGVNetwork network) {
		destroyNetwork(network, false);
	}

	/**
	 * destroys the given network
	 * 
	 * @param network
	 *            the network tobe destroyed
	 * @param destroy_unique
	 *            if this is true, then all Nodes and Edges that are in this
	 *            network, but no other are also destroyed.
	 */
	public static void destroyNetwork(HGVNetwork network, boolean destroy_unique) {
		
		HyperGraph hg = network.getHyperGraph();
		boolean last_net = true;
		for(HGVNetwork n: getNetworkMap().keySet())
			if(!n.equals(network) && hg.equals(n.getHyperGraph())){
				last_net = false;break;
			}
		if(last_net) hg.close();
		if (viewExists(network))
			destroyNetworkView(network);
		getNetworkMap().remove(network);
		firePropertyChange(NETWORK_DESTROYED, null, network);
		
		if (destroy_unique) {
			ArrayList<Node> nodes = new ArrayList<Node>();
			ArrayList<Edge> edges = new ArrayList<Edge>();
			Collection networks = networkMap.values();
			Iterator nodes_i = network.nodesIterator();
			Iterator edges_i = network.edgesIterator();
			while (nodes_i.hasNext()) {
				Node node = (Node) nodes_i.next();
				boolean add = true;
				for (Iterator n_i = networks.iterator(); n_i.hasNext();) {
					HGVNetwork net = (HGVNetwork) n_i.next();
					if (net.containsNode(node)) {
						add = false;
						continue;
					}
				}
				if (add) {
					nodes.add(node);
					getRootGraph().removeNode(node);
				}
			}
			while (edges_i.hasNext()) {
				Edge edge = (Edge) edges_i.next();
				boolean add = true;
				for (Iterator n_i = networks.iterator(); n_i.hasNext();) {
					HGVNetwork net = (HGVNetwork) n_i.next();
					if (net.containsEdge(edge)) {
						add = false;
						continue;
					}
				}
				if (add) {
					edges.add(edge);
					getRootGraph().removeEdge(edge);
				}
			}
			getRootGraph().removeNodes(nodes);
			getRootGraph().removeEdges(edges);
		}
		// theoretically this should not be set to null till after the events
		// firing is done
		network = null;
	}

	/**
	 * destroys the networkview, including any layout information
	 */
	public static void destroyNetworkView(HGVNetworkView view) {
		// System.out.println( "destroying: "+view.getIdentifier()+" :
		// "+getNetworkViewMap().get( view.getIdentifier() ) );
		getNetworkMap().put(view.getNetwork(), null);
		// System.out.println( "gone from hash: "+view.getIdentifier()+" :
		// "+getNetworkViewMap().get( view.getIdentifier() ) );
		firePropertyChange(HGVDesktop.NETWORK_VIEW_DESTROYED, null, view);
		// theoretically this should not be set to null till after the events
		// firing is done
		view = null;
		// TODO: do we want here?
		System.gc();
	}
	
	/**
	 * destroys the networkview, including any layout information
	 */
	public static void destroyNetworkView(HGVNetwork network) {
		HGVNetworkView view = getNetworkMap().get(network);
		if(view == null) return;
		getNetworkMap().put(network, null);
		firePropertyChange(HGVDesktop.NETWORK_VIEW_DESTROYED, null, view);
	}

	
	protected static void addNetwork(HGVNetwork network) {
		addNetwork(network, null);
	}

	protected static void addNetwork(HGVNetwork network, HGVNetwork parent) {
		System.out.println("HGVNetwork Added: " + network.getIdentifier());
		getNetworkMap().put(network, null);
		HyperGraph db = network.getHyperGraph();
		if (db != null) {
			network.setTitle(HGVNetworkNaming.getSuggestedNetworkTitle(
					db.getStore().getDatabaseLocation()));
		}
		if (parent != null) {
			network.setHyperGraph(parent.getHyperGraph());
		}
		firePropertyChange(NETWORK_CREATED, parent, network);
		if (network.getNodeCount() < AppConfig.getInstance().getViewThreshold()) {
			createNetworkView(network);
		}
	}

	/**
	 * Creates a new Network
	 * 
	 * @param nodes   the indeces of nodes
	 * @param edges   the indeces of edges
	 * @param title   the title of the new network.
	 */
	public static HGVNetwork createNetwork(int[] nodes, int[] edges, HyperGraph hg) {
		HGVNetwork network = getRootGraph().createNetwork(nodes, edges);
		network.setHyperGraph(hg);
		addNetwork(network);
		return network;
	}

	/**
	 * Creates a new Network
	 * 
	 * @param nodes   a collection of nodes
	 * @param edges   a collection of edges
	 * @param db      the HyperGraph of the new network.
	 */
	public static HGVNetwork createNetwork(Collection nodes, Collection edges,
			HyperGraph db) {
		HGVNetwork network = getRootGraph().createNetwork(nodes, edges);
		network.setHyperGraph(db);
		addNetwork(network);
		return network;
	}

	/**
	 * Creates a new Network, that inherits from the given ParentNetwork
	 * 
	 */
	public static HGVNetwork createNetwork(int[] nodes, int[] edges, HyperGraph db,
			HGVNetwork parent) {
		HGVNetwork network = getRootGraph().createNetwork(nodes, edges);
		network.setHyperGraph(db);
		addNetwork(network, parent);
		return network;
	}

	/**
	 * Creates a new Network, that inherits from the given ParentNetwork
     */
	public static HGVNetwork createNetwork(Collection nodes, Collection edges,
			HyperGraph db, HGVNetwork parent) {
		HGVNetwork network = getRootGraph().createNetwork(nodes, edges);
		network.setHyperGraph(db);
		addNetwork(network, parent);
		return network;
	}

	
	/**
	 * Creates a HGVNetworkView, but doesn't do anything with it. Ifnn's you
	 * want to use it
	 * 
	 * @param network   the network to create a view of
	 */
	public static HGVNetworkView createNetworkView(HGVNetwork network) {
		return createNetworkView(network, network.getTitle());
	}

	/**
	 * Creates a HGVNetworkView, but doesn't do anything with it. Ifnn's you
	 * want to use it
	 * 
	 * @param network  the network to create a view of
	 */
	public static HGVNetworkView createNetworkView(HGVNetwork network,
			String title) {
		if (network == null) return null;
		if (viewExists(network)) {
			return getNetworkView(network);
		}
		final HGVNetworkView view = new HGVNetworkView(network, title);
		view.setIdentifier(network.getIdentifier());
		getNetworkMap().put(network, view);
		view.setTitle(title);
		firePropertyChange(HGVDesktop.NETWORK_VIEW_CREATED, null, view);
		// Instead of calling fitContent(), access PGraphView directly.
		// This enables us to disable animation. Modified by Ethan Cerami.
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				view.getCanvas().getCamera().animateViewToCenterBounds(
						view.getCanvas().getLayer().getFullBounds(), true, 0);
				// if Squiggle function enabled, enable it on the view
				if (squiggleEnabled)
					view.getSquiggleHandler().beginSquiggling();
				// set the selection mode on the view
				setSelectionMode(currentSelectionMode, view);
				HGViewer.getPreferedLayout().applyLayout();
			}
		});
		view.redrawGraph();
		return view;
	}

	public static void firePropertyChange(String property_type,
			Object old_value, Object new_value) {
		PropertyChangeEvent e = new PropertyChangeEvent(pcsO, property_type,
				old_value, new_value);
		System.out.println( "HGViewer FIRING : " + property_type );
		//for(PropertyChangeListener l: getSwingPropertyChangeSupport().getPropertyChangeListeners())
		//	System.out.println("" + l);
		getSwingPropertyChangeSupport().firePropertyChange(e);
	}

	private static void setSquiggleState(boolean isEnabled) {
		// enable Squiggle on all network views
		PGraphView view;
		Map networkViewMap = getNetworkMap();
		for (Iterator iter = networkViewMap.values().iterator(); iter.hasNext();) {
			view = (PGraphView) iter.next();
			if(view == null) continue;
			if (isEnabled) {
				view.getSquiggleHandler().beginSquiggling();
			} else {
				view.getSquiggleHandler().stopSquiggling();
			}
		}
	}

	/**
	 * Utility method to enable Squiggle function.
	 */
	public static void enableSquiggle() {
		// set the global flag to indicate that Squiggle is enabled
		squiggleEnabled = true;
		setSquiggleState(true);
	}

	/**
	 * Utility method to disable Squiggle function.
	 */
	public static void disableSquiggle() {
		// set the global flag to indicate that Squiggle is disabled
		squiggleEnabled = false;
		setSquiggleState(false);
	}

	/**
	 * Returns the value of the global flag to indicate whether the Squiggle
	 * function is enabled.
	 */
	public static boolean isSquiggleEnabled() {
		return squiggleEnabled;
	}

	/**
	 * Gets the selection mode value.
	 */
	public static int getSelectionMode() {
		return currentSelectionMode;
	}

	/**
	 * Sets the specified selection mode on all views.
	 * 
	 * @param selectionMode
	 *            SELECT_NODES_ONLY, SELECT_EDGES_ONLY, or
	 *            SELECT_NODES_AND_EDGES.
	 */
	public static void setSelectionMode(int selectionMode) {
		// set the selection mode on all the views
		Map networkViewMap = getNetworkMap();
		for (Iterator iter = networkViewMap.values().iterator(); iter.hasNext();) {
			PGraphView view = (PGraphView) iter.next();
			if(view == null) continue;
			setSelectionMode(selectionMode, view);
		}
		// update the global indicating the selection mode
		currentSelectionMode = selectionMode;
	}

	/**
	 * Utility method to set the selection mode on the specified GraphView.
	 * 
	 * @param selectionMode
	 *            SELECT_NODES_ONLY, SELECT_EDGES_ONLY, or
	 *            SELECT_NODES_AND_EDGES.
	 * @param view
	 *            the GraphView to set the selection mode on.
	 */
	public static void setSelectionMode(int selectionMode, GraphView view) {
		// first, disable node and edge selection on the view
		view.disableNodeSelection();
		view.disableEdgeSelection();
		// then, based on selection mode, enable node and/or edge selection
		switch (selectionMode) {
		case SELECT_NODES_ONLY:
			view.enableNodeSelection();
			break;
		case SELECT_EDGES_ONLY:
			view.enableEdgeSelection();
			break;
		case SELECT_NODES_AND_EDGES:
			view.enableNodeSelection();
			view.enableEdgeSelection();
			break;
		}
	}

	public static Set<Layout> getLayouts() {
		return layouts;
	}

	public static void addLayout(Layout layout) {
		layouts.add(layout);
	}

	public static Layout getPreferedLayout() {
		return prefered_layout;
	}

	public static void setPreferedLayout(Layout pref_layout) {
		prefered_layout = pref_layout;
	}

	public static HGVNetworkView getStandaloneView(HyperGraph hg, HGHandle h, int depth,
			HGAtomPredicate cond) {
		embeded = true;
		try {
			final HGWNReader reader = new HGWNReader(hg);
			reader.read(h, depth, cond);
			final int[] nodes = reader.getNodeIndicesArray();
			final int[] edges = reader.getEdgeIndicesArray();
			int realThreshold = AppConfig.getInstance().getViewThreshold();
			AppConfig.getInstance().setViewThreshold(0);
			HGVNetwork network = HGViewer.createNetwork(nodes, edges, hg);
            network.setTitle(hg.getStore().getDatabaseLocation());
            AppConfig.getInstance().setViewThreshold(realThreshold);
			return createView(network);
		} catch (IOException ex) {
           ex.printStackTrace();
		}
		return null;
	}

	private static HGVNetworkView createView(HGVNetwork network) {
		final HGVNetworkView view = new HGVNetworkView(network, network
				.getTitle());
		view.setIdentifier(network.getIdentifier());
		getNetworkMap().put(network, view);
		HGViewer.setCurrentView(view);
		view.setTitle(network.getTitle());

		// if Squiggle function enabled, enable squiggling on the created view
		if (HGViewer.isSquiggleEnabled()) {
			view.getSquiggleHandler().beginSquiggling();
		}

		// set the selection mode on the view
		HGViewer.setSelectionMode(HGViewer.getSelectionMode(), view);
        HGViewer.getGraphViewController().addGraphView(view);
		view.redrawGraph();

		//TODO: remove after implementing ActionManager 
		view.getCanvas().getInputMap().put(
				KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK),
				"FitContent");
		view.getCanvas().getActionMap().put("FitContent",
				new FitContentAction());
		view.getCanvas().getInputMap().put(
				KeyStroke.getKeyStroke(KeyEvent.VK_UP, ActionEvent.CTRL_MASK),
				"Zoom In");
		view.getCanvas().getActionMap().put("Zoom In", new ZoomAction(1.1));
		view.getCanvas().getInputMap()
				.put(
						KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,
								ActionEvent.CTRL_MASK), "Zoom Out");
		view.getCanvas().getActionMap().put("Zoom Out", new ZoomAction(0.9));
		/////////////
		
		addKeyBindings(view);
		return view;
	}
	
	private static void addKeyBindings(HGVNetworkView view){
		for(Action a: ActionManager.actions.values())
		{
		   KeyStroke key = (KeyStroke) a.getValue(Action.ACCELERATOR_KEY);
		   if(key != null){
			   String name = (String) a.getValue(Action.NAME);  
			   view.getCanvas().getInputMap().put(key, name);
			   view.getCanvas().getActionMap().put(name, a);
			   System.out.println("Action: " + name + ":" + key);
		   }//else
			   //System.out.println("Action: " + name + ":" + key);
		}
	}

}
