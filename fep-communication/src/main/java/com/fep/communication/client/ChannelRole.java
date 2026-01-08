package com.fep.communication.client;

/**
 * Role of a channel in dual-channel FISC connection architecture.
 *
 * <p>In FISC dual-channel mode:
 * <ul>
 *   <li>SEND channel: dedicated to sending all outgoing messages</li>
 *   <li>RECEIVE channel: dedicated to receiving all incoming responses</li>
 * </ul>
 */
public enum ChannelRole {

    /**
     * Send channel - dedicated to sending all requests.
     * Messages: 0200 (Financial), 0400 (Reversal), 0800 (Network Management)
     */
    SEND,

    /**
     * Receive channel - dedicated to receiving all responses.
     * Messages: 0210 (Financial Response), 0410 (Reversal Response), 0810 (Network Response)
     */
    RECEIVE
}
