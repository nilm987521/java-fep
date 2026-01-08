package com.fep.jmeter.config;

import org.apache.jmeter.testbeans.BeanInfoSupport;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for FiscConfigElement.
 *
 * <p>Defines the GUI properties for the FISC Connection Config element.
 */
public class FiscConfigElementBeanInfo extends BeanInfoSupport {

    private static final String CONNECTION_GROUP = "connection";
    private static final String BACKUP_GROUP = "backup";
    private static final String TIMEOUT_GROUP = "timeout";
    private static final String OPTIONS_GROUP = "options";

    public FiscConfigElementBeanInfo() {
        super(FiscConfigElement.class);

        createPropertyGroup(CONNECTION_GROUP, new String[]{
            FiscConfigElement.CONFIG_NAME,
            FiscConfigElement.PRIMARY_HOST,
            FiscConfigElement.PRIMARY_PORT,
            FiscConfigElement.INSTITUTION_ID
        });

        createPropertyGroup(BACKUP_GROUP, new String[]{
            FiscConfigElement.BACKUP_HOST,
            FiscConfigElement.BACKUP_PORT
        });

        createPropertyGroup(TIMEOUT_GROUP, new String[]{
            FiscConfigElement.CONNECTION_TIMEOUT,
            FiscConfigElement.READ_TIMEOUT,
            FiscConfigElement.IDLE_TIMEOUT
        });

        createPropertyGroup(OPTIONS_GROUP, new String[]{
            FiscConfigElement.AUTO_SIGN_ON,
            FiscConfigElement.AUTO_RECONNECT
        });

        // Config name
        PropertyDescriptor configNameProp = property(FiscConfigElement.CONFIG_NAME);
        configNameProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        configNameProp.setValue(DEFAULT, "default");
        configNameProp.setDisplayName("Configuration Name");
        configNameProp.setShortDescription("Unique name for this FISC configuration");

        // Primary host
        PropertyDescriptor primaryHostProp = property(FiscConfigElement.PRIMARY_HOST);
        primaryHostProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        primaryHostProp.setValue(DEFAULT, "localhost");
        primaryHostProp.setDisplayName("Primary Host");
        primaryHostProp.setShortDescription("Primary FISC server hostname or IP address");

        // Primary port
        PropertyDescriptor primaryPortProp = property(FiscConfigElement.PRIMARY_PORT);
        primaryPortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        primaryPortProp.setValue(DEFAULT, 9000);
        primaryPortProp.setDisplayName("Primary Port");
        primaryPortProp.setShortDescription("Primary FISC server port");

        // Institution ID
        PropertyDescriptor instIdProp = property(FiscConfigElement.INSTITUTION_ID);
        instIdProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        instIdProp.setValue(DEFAULT, "");
        instIdProp.setDisplayName("Institution ID");
        instIdProp.setShortDescription("Bank institution ID for FISC transactions");

        // Backup host
        PropertyDescriptor backupHostProp = property(FiscConfigElement.BACKUP_HOST);
        backupHostProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        backupHostProp.setValue(DEFAULT, "");
        backupHostProp.setDisplayName("Backup Host");
        backupHostProp.setShortDescription("Backup FISC server hostname (optional)");

        // Backup port
        PropertyDescriptor backupPortProp = property(FiscConfigElement.BACKUP_PORT);
        backupPortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        backupPortProp.setValue(DEFAULT, 0);
        backupPortProp.setDisplayName("Backup Port");
        backupPortProp.setShortDescription("Backup FISC server port (0 = disabled)");

        // Connection timeout
        PropertyDescriptor connTimeoutProp = property(FiscConfigElement.CONNECTION_TIMEOUT);
        connTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        connTimeoutProp.setValue(DEFAULT, 10000);
        connTimeoutProp.setDisplayName("Connection Timeout (ms)");
        connTimeoutProp.setShortDescription("TCP connection timeout in milliseconds");

        // Read timeout
        PropertyDescriptor readTimeoutProp = property(FiscConfigElement.READ_TIMEOUT);
        readTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        readTimeoutProp.setValue(DEFAULT, 30000);
        readTimeoutProp.setDisplayName("Read Timeout (ms)");
        readTimeoutProp.setShortDescription("Response timeout in milliseconds");

        // Idle timeout
        PropertyDescriptor idleTimeoutProp = property(FiscConfigElement.IDLE_TIMEOUT);
        idleTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        idleTimeoutProp.setValue(DEFAULT, 30000);
        idleTimeoutProp.setDisplayName("Idle Timeout (ms)");
        idleTimeoutProp.setShortDescription("Idle timeout before sending heartbeat");

        // Auto sign-on
        PropertyDescriptor autoSignOnProp = property(FiscConfigElement.AUTO_SIGN_ON);
        autoSignOnProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        autoSignOnProp.setValue(DEFAULT, Boolean.TRUE);
        autoSignOnProp.setDisplayName("Auto Sign-On");
        autoSignOnProp.setShortDescription("Automatically sign on after connecting");

        // Auto reconnect
        PropertyDescriptor autoReconnectProp = property(FiscConfigElement.AUTO_RECONNECT);
        autoReconnectProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        autoReconnectProp.setValue(DEFAULT, Boolean.TRUE);
        autoReconnectProp.setDisplayName("Auto Reconnect");
        autoReconnectProp.setShortDescription("Automatically reconnect on connection loss");
    }
}
