/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package net.java.sip.communicator.impl.gui.main.presence;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.*;

import javax.swing.*;
import javax.swing.Timer;

import net.java.sip.communicator.impl.gui.customcontrols.*;
import net.java.sip.communicator.impl.gui.i18n.*;
import net.java.sip.communicator.impl.gui.main.*;
import net.java.sip.communicator.impl.gui.utils.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;

/**
 * The <tt>StatusSelectorBox</tt> is a <tt>SIPCommSelectorBox</tt> that contains
 * the list of statuses for a protocol provider. This is where the user could
 * select its status.
 * 
 * @author Yana Stamcheva
 */
public class PresenceStatusSelectorBox
    extends StatusSelectorBox
{

    private Logger logger = Logger.getLogger(
            PresenceStatusSelectorBox.class.getName());

    private MainFrame mainFrame;

    private BufferedImage[] animatedImageArray;

    private Connecting connecting = new Connecting();

    private ProtocolProviderService protocolProvider;

    private Iterator statusIterator;

    private PresenceStatus offlineStatus;
    
    private PresenceStatus onlineStatus;
    
    private PresenceStatus lastSelectedStatus;
    
    private int accountIndex;
    
    /**
     * Creates an instance of <tt>StatusSelectorBox</tt> and initializes
     * the selector box with data.
     * 
     * @param mainFrame The main application window.
     * @param protocolProvider The protocol provider.
     * @param accountIndex If we have more than one account for a protocol,
     * each account has an index.
     */
    public PresenceStatusSelectorBox(MainFrame mainFrame,
            ProtocolProviderService protocolProvider,
            int accountIndex) {
        
        this.mainFrame = mainFrame;
        this.protocolProvider = protocolProvider;
        this.accountIndex = accountIndex;
        
        this.mainFrame.getProtocolPresence(protocolProvider);
        
        this.statusIterator = this.mainFrame
            .getProtocolPresence(protocolProvider).getSupportedStatusSet();
        
        this.setToolTipText(protocolProvider.getAccountID().getUserID());
        
        while(statusIterator.hasNext()) {
            PresenceStatus status = (PresenceStatus) statusIterator.next();
            int connectivity = status.getStatus();

            if(connectivity < 1) {
                this.offlineStatus = status;
            }
            else if((onlineStatus != null 
                            && (onlineStatus.getStatus() < connectivity)) 
                    || (onlineStatus == null 
                            && (connectivity > 50 && connectivity < 80))) {
                this.onlineStatus = status;
            }
            
            this.addItem(status.getStatusName(), 
                new ImageIcon(
                    ImageLoader.getBytesInImage(status.getStatusIcon())),
                new ItemActionListener());
        }
        this.setSelectedStatus(offlineStatus);
    }

    /**
     * Handles the <tt>ActionEvent</tt> triggered when one of the items
     * in the list is selected.
     */
    private class ItemActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() instanceof JMenuItem) {

                JMenuItem menuItem = (JMenuItem) e.getSource();
                
                OperationSetPresence presence = mainFrame
                        .getProtocolPresence(protocolProvider);

                Iterator statusSet = presence.getSupportedStatusSet();

                while (statusSet.hasNext()) {

                    PresenceStatus status = ((PresenceStatus) statusSet.next());

                    if (status.getStatusName().equals(menuItem.getText())
                            && !presence.getPresenceStatus().equals(status)) {

                        try {
                            if(protocolProvider.isRegistered()) {
                                if (status.isOnline()) {
                                    presence.publishPresenceStatus(
                                            status, "");
                                }
                                else {
                                    mainFrame.getLoginManager()
                                        .setManuallyDisconnected(true);
                                    protocolProvider.unregister();
                                }
                                setSelectedStatus(status);                                
                            }
                            else {
                                lastSelectedStatus = status; 
                                mainFrame.getLoginManager()
                                    .login(protocolProvider);
                            }
                            mainFrame.saveStatusInformation(
                                    protocolProvider, status);
                        }
                        catch (IllegalArgumentException e1) {

                            logger.error("Error - changing status", e1);

                        }
                        catch (IllegalStateException e1) {

                            logger.error("Error - changing status", e1);

                        }
                        catch (OperationFailedException e1) {

                            if (e1.getErrorCode() 
                                == OperationFailedException.GENERAL_ERROR) {
                                SIPCommMsgTextArea msgText 
                                = new SIPCommMsgTextArea(Messages
                                    .getString("statusChangeGeneralError"));
                                
                                JOptionPane.showMessageDialog(null, msgText,
                                        Messages.getString("generalError"),
                                        JOptionPane.ERROR_MESSAGE);
                            }
                            else if (e1.getErrorCode() 
                                    == OperationFailedException
                                        .NETWORK_FAILURE) {
                                SIPCommMsgTextArea msgText 
                                    = new SIPCommMsgTextArea(
                                        Messages.getString(
                                            "statusChangeNetworkFailure"));
                                
                                JOptionPane.showMessageDialog(
                                    null,
                                    msgText,
                                    Messages.getString("networkFailure"),
                                    JOptionPane.ERROR_MESSAGE);
                            } 
                            else if (e1.getErrorCode()
                                    == OperationFailedException
                                        .PROVIDER_NOT_REGISTERED) {
                                SIPCommMsgTextArea msgText 
                                    = new SIPCommMsgTextArea(
                                        Messages.getString(
                                            "statusChangeNetworkFailure"));
                                
                                JOptionPane.showMessageDialog(
                                    null,
                                    msgText,
                                    Messages.getString("networkFailure"),
                                    JOptionPane.ERROR_MESSAGE);
                            }
                            logger.error("Error - changing status", e1);
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Starts the timer that changes the images given by the array, thus
     * creating an animated image that indicates that the user is connecting.
     * 
     * @param images A <tt>BufferedImage</tt> array that contains all images
     * from which to create the animated image indicating the connecting state.
     */
    public void startConnecting(BufferedImage[] images) {

        this.animatedImageArray = images;

        this.setIcon(new ImageIcon(images[0]));

        this.connecting.start();
    }

    /**
     * Stops the timer that manages the connecting animated icon.
     */
    public void updateStatus(Object presenceStatus) {
        
        PresenceStatus status = (PresenceStatus) presenceStatus;
        
        OperationSetPresence presence = mainFrame
            .getProtocolPresence(protocolProvider);
        
        this.connecting.stop();
        
        this.setSelectedStatus(status);
        
        if(protocolProvider.isRegistered()) {
            try {
                presence.publishPresenceStatus(
                        status, "");
            }
            catch (IllegalArgumentException e1) {
                logger.error("Error - changing status", e1);
            }
            catch (IllegalStateException e1) {
                logger.error("Error - changing status", e1);
            }
            catch (OperationFailedException e1) {
                if (e1.getErrorCode() 
                    == OperationFailedException.GENERAL_ERROR) {
                    SIPCommMsgTextArea msgText 
                    = new SIPCommMsgTextArea(Messages
                        .getString("statusChangeGeneralError"));
                    
                    JOptionPane.showMessageDialog(null, msgText,
                            Messages.getString("generalError"),
                            JOptionPane.ERROR_MESSAGE);
                }
                else if (e1.getErrorCode() 
                        == OperationFailedException
                            .NETWORK_FAILURE) {
                    SIPCommMsgTextArea msgText 
                        = new SIPCommMsgTextArea(
                            Messages.getString(
                                "statusChangeNetworkFailure"));
                    
                    JOptionPane.showMessageDialog(
                        null,
                        msgText,
                        Messages.getString("networkFailure"),
                        JOptionPane.ERROR_MESSAGE);
                } 
                else if (e1.getErrorCode()
                        == OperationFailedException
                            .PROVIDER_NOT_REGISTERED) {
                    SIPCommMsgTextArea msgText 
                        = new SIPCommMsgTextArea(
                            Messages.getString(
                                "statusChangeNetworkFailure"));
                    
                    JOptionPane.showMessageDialog(
                        null,
                        msgText,
                        Messages.getString("networkFailure"),
                        JOptionPane.ERROR_MESSAGE);
                }
                logger.error("Error - changing status", e1);
            }
        }
    }

    /**
     * A <tt>Timer</tt> that creates an animated icon, which indicates the
     * connecting state.
     */
    private class Connecting extends Timer {

        public Connecting() {

            super(100, null);

            this.addActionListener(new TimerActionListener());
        }

        private class TimerActionListener implements ActionListener {

            private int j = 1;

            public void actionPerformed(ActionEvent evt) {

                PresenceStatusSelectorBox.this.setIcon(new ImageIcon(
                        animatedImageArray[j]));
                j = (j + 1) % animatedImageArray.length;
            }

        }
    }

    /**
     * Selects the given status in the status menu.
     * @param status the status to select
     */
    public void setSelectedStatus(PresenceStatus status)
    {
        Image statusImage = ImageLoader.getBytesInImage(status.getStatusIcon());
        
        this.setSelected(status.getStatusName(),
                new ImageIcon(statusImage));
    }

    /**
     * Returns the Offline status in this selector box.
     * @return the Offline status in this selector box
     */
    public PresenceStatus getOfflineStatus()
    {
        return offlineStatus;
    }

    /**
     * Returns the Online status in this selector box.
     * @return the Online status in this selector box
     */
    public PresenceStatus getOnlineStatus()
    {
        return onlineStatus;
    }

    /**
     * Returns the status that is currently selected.
     * @return the status that is currently selected
     */
    public PresenceStatus getLastSelectedStatus()
    {
        return lastSelectedStatus;
    }

    public int getAccountIndex()
    {
        return accountIndex;
    }

    public void setAccountIndex(int accountIndex)
    {
        this.accountIndex = accountIndex;
    }
    
    public void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        
        if(accountIndex > 0) {
            AntialiasingManager.activateAntialiasing(g);
            g.setColor(Color.DARK_GRAY);
            g.setFont(Constants.FONT.deriveFont(Font.BOLD, 9));
            g.drawString(new Integer(accountIndex).toString(), 20, 12);
        }
    }

    public void updateStatus()
    {}
}
