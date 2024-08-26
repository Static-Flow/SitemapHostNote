package main.java;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.InvocationType;
import main.java.com.staticflow.BurpGuiControl;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;


/**
 * This extension provides the ability to add a note to the hostname of a target in the Site map.
 * _How_ it does this is explained further below, but essentially it crawls the Swing Tree looking for the selected
 * host and then adds your note to it.
 */
public class SitemapHostNote implements BurpExtension, ContextMenuItemsProvider {

    // reference to the Site map Jtree
    private JTree tree;
    // reference to the montoya APIs since BurpSuite won't provide a Singleton
    private MontoyaApi montoya;

    /**
     * This method initializes the extension. First we register ourselves as a context-menu handler, then we plumb the
     * depths of the BurpSuite Swing tree to find the JTree component that handles the Site map for use later.
     * @param montoyaApi MontoyaAPIs provided by Burp Suite
     */
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        montoya = montoyaApi;
        // register our context menu, created in provideMenuItems()
        montoyaApi.userInterface().registerContextMenuItemsProvider(this);
        // Leveraging my Burp GUI lib we find all JTabbedPanes underneath the root Frame
        List<Component> rootTabPanes = BurpGuiControl.findAllComponentsOfType(montoya.userInterface().swingUtils().suiteFrame(), JTabbedPane.class);
        FOUND:
        for(Component rootTabbedPane : rootTabPanes) {
            // for each tab pane
            if (((JTabbedPane) rootTabbedPane).getTabCount() > 2) {
                // if there's more than 2 tabs (this rules out other JTabbedPanes like the Events/Issues tabs at bottom)
                for (int i = 0; i < ((JTabbedPane) rootTabbedPane).getTabCount(); i++) {
                    // for each tab in the tabbedPane
                    if (Objects.equals(((JTabbedPane) rootTabbedPane).getTitleAt(i), "Target")) {
                        // if it's the Target tab
                        // find all JTabbedPanes under the Target tab
                        List<Component> targetTabChildTabPanes = BurpGuiControl.findAllComponentsOfType((Container) rootTabbedPane, JTabbedPane.class);
                        for (Component targetTabChildTabPane : targetTabChildTabPanes) {
                            // for each TabbedPane that is a child of the "Target" Tab
                            if (((JTabbedPane) targetTabChildTabPane).getTabCount() == 4 && ((JTabbedPane) targetTabChildTabPane).getTitleAt(0).equals("Site map")) {
                                /*
                                 if the TabbedPane has 4 tabs and the first one is "Site map", find the first JTree
                                 child element and store it for use later.
                                 */
                                tree = (JTree) BurpGuiControl.findFirstComponentOfType((Container) targetTabChildTabPane, JTree.class);
                                break FOUND;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * This method contains the bulk of the functionality in this extension. Unfortunately to make something like this
     * work we have to do some gross reflection and Swing tree walking. I've attempted to write this in such a way that
     * it survives Burp Suite code changes but this will <i>eventually</i> stop working. Please make an issue if this has
     * stopped working.
     * <br><br>
     * At a high level this does the following: <br><br>
     * 1. walks the Site map JTree children MutableTreeNodes <br>
     * 2. calls a custom no-arg method which returns the custom UserObject which contains the details of the site-map host <br>
     * 3. within that custom UserObject class searches for a private String field on its super class <br>
     * 4. modifies the field so it's accessible and checks if it matches the user supplied host <br>
     * 5. if we've found the right field, prompt the user for the note to add <br>
     * 6. add the note to the end of the hostname string.
     *
     * @param selectedHost The hostname of the target the user selected
     */
    private void findHostComponent(String selectedHost) {
        int childCount = tree.getModel().getChildCount(tree.getModel().getRoot());
        DONE:
        for (int i = 0; i < childCount; i++) {
            // for each child TreeNode
            Object childTreeNode = tree.getModel().getChild(tree.getModel().getRoot(),i); // Custom mutableTreeNode
            for(Method treeNodeMethod : childTreeNode.getClass().getMethods()) {
                // for each method on the custom TreeNode class
                if(treeNodeMethod.getReturnType().equals(Object.class) && treeNodeMethod.getParameterCount() == 0) {
                    // if there's a no-arg method that returns an Object
                    try {
                        // invoke the method to get the custom UserObject within the TreeNode
                        Object userObject = treeNodeMethod.invoke(childTreeNode);// Custom UserObject Class
                        for(Field userObjectField : userObject.getClass().getFields()) {
                            // for each field on the custom UserObject
                            Object userObjectFieldValue = userObjectField.get(userObject);
                            if (userObjectFieldValue != null) {
                                // if the field has a value
                                if (userObjectFieldValue.getClass().getSuperclass() != null) {
                                    // if the superclass of the field value is not null
                                    for (Field userObjectSuperClassField : userObjectFieldValue.getClass().getSuperclass().getDeclaredFields()) {
                                        // for each declared field on the superclass
                                        // set it to be accessible
                                        if (userObjectSuperClassField.getType().isAssignableFrom(String.class)) {
                                            userObjectSuperClassField.setAccessible(true);
                                            // if the superclass field is a String
                                            String userObjectHostUrl = (String) userObjectSuperClassField.get(userObjectFieldValue);
                                            if (userObjectHostUrl.startsWith(selectedHost)) {
                                                // if the String field matches the user supplied protocol + host
                                                // get the note the user wants to add to the host
                                                int noteSpaceIndex = userObjectHostUrl.indexOf(" ");
                                                if (noteSpaceIndex != -1) {
                                                    String noteText = JOptionPane.showInputDialog(
                                                            montoya.userInterface().swingUtils().suiteFrame(),
                                                            "Enter note:",
                                                            userObjectHostUrl.substring(noteSpaceIndex+1).replace("(","").replace(")",""));
                                                    // if the String field contains a space then we have a note already on this host
                                                    String hostString = userObjectHostUrl.substring(0, noteSpaceIndex);
                                                    userObjectSuperClassField.set(userObjectFieldValue, hostString + " (" + noteText + ")");
                                                } else {
                                                    String noteText = JOptionPane.showInputDialog(
                                                            montoya.userInterface().swingUtils().suiteFrame(),
                                                            "Enter note:");
                                                    // if the String field has no space then it's the first note
                                                    userObjectSuperClassField.set(userObjectFieldValue, userObjectHostUrl + " (" + noteText + ")");
                                                }
                                                // quit searching
                                                break DONE;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        // this shouldn't happen but if it does make an github issue
                        montoya.logging().logToOutput(e.toString());
                    }
                }
            }
        }
    }

    /**
     * This method handles the creation of our custom menu. It triggers if the right-click happens within the Site map
     * Tree. When the custom menu is clicked, {@link #findHostComponent(String)} is called with the hostname of the first
     * request under the site map entry the user clicked. If finding the Site map JTree didn't work, this returns null.
     * Please create an issue if you get a "No tree found" error.
     * @param event {@link burp.api.montoya.ui.contextmenu} The event which spawned the context-menu creation
     * @return {@link List<Component>} The list of nested components to return as the menu
     */
    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        if(event.isFrom(InvocationType.SITE_MAP_TREE)) {
            JMenuItem menu = new JMenuItem("Add Host Note");
            menu.addActionListener(e -> {
                if(tree != null) {
                    if(event.selectedRequestResponses().getFirst().httpService().secure()) {
                        findHostComponent("https://"+event.selectedRequestResponses().getFirst().httpService().host());
                    } else {
                        findHostComponent("http://"+event.selectedRequestResponses().getFirst().httpService().host());

                    }
                } else {
                    montoya.logging().logToError("No tree found");
                }
            });
            return List.of(menu);
        }
        return null;
    }
}
