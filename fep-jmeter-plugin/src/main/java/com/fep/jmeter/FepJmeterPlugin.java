package com.fep.jmeter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the FEP JMeter Plugin.
 *
 * <p>This plugin provides JMeter components for testing FISC ISO 8583 transactions:
 * <ul>
 *   <li><b>FISC Connection Config</b> - Configuration element for FISC connection settings</li>
 *   <li><b>FISC ISO 8583 Sampler</b> - Sampler for sending ISO 8583 transactions</li>
 * </ul>
 *
 * <h2>Installation</h2>
 * <ol>
 *   <li>Build the plugin: <code>mvn clean package</code></li>
 *   <li>Copy <code>fep-jmeter-plugin-1.0.0-SNAPSHOT.jar</code> to JMeter's <code>lib/ext</code> directory</li>
 *   <li>Restart JMeter</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <ol>
 *   <li>Add a "FISC Connection Config" element to your Test Plan</li>
 *   <li>Configure the FISC server connection settings</li>
 *   <li>Add "FISC ISO 8583 Sampler" to your Thread Group</li>
 *   <li>Configure the transaction type and message fields</li>
 *   <li>Run the test</li>
 * </ol>
 *
 * <h2>Supported Transactions</h2>
 * <ul>
 *   <li>ECHO_TEST - Network echo test (0800)</li>
 *   <li>SIGN_ON - Network sign-on (0800)</li>
 *   <li>SIGN_OFF - Network sign-off (0800)</li>
 *   <li>WITHDRAWAL - Cash withdrawal (0200)</li>
 *   <li>TRANSFER - Fund transfer (0200)</li>
 *   <li>BALANCE_INQUIRY - Balance inquiry (0200)</li>
 *   <li>BILL_PAYMENT - Bill payment (0200)</li>
 * </ul>
 *
 * @version 1.0.0
 * @since 2024-01-01
 */
public class FepJmeterPlugin {

    private static final Logger log = LoggerFactory.getLogger(FepJmeterPlugin.class);

    public static final String VERSION = "1.0.0";
    public static final String NAME = "FEP JMeter Plugin";

    static {
        log.info("Loading {} v{}", NAME, VERSION);
        log.info("Components: FISC Connection Config, FISC ISO 8583 Sampler");
    }

    /**
     * Plugin entry point (for standalone execution/testing).
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  " + NAME + " v" + VERSION);
        System.out.println("==============================================");
        System.out.println();
        System.out.println("This is a JMeter plugin for testing FISC ISO 8583 transactions.");
        System.out.println();
        System.out.println("Installation:");
        System.out.println("  1. Copy this JAR to JMeter's lib/ext directory");
        System.out.println("  2. Restart JMeter");
        System.out.println("  3. Find new components under:");
        System.out.println("     - Config Elements > FISC Connection Config");
        System.out.println("     - Samplers > FISC ISO 8583 Sampler");
        System.out.println();
        System.out.println("Supported Transaction Types:");
        System.out.println("  - ECHO_TEST      : Network echo test (0800)");
        System.out.println("  - SIGN_ON        : Network sign-on (0800)");
        System.out.println("  - SIGN_OFF       : Network sign-off (0800)");
        System.out.println("  - WITHDRAWAL     : Cash withdrawal (0200)");
        System.out.println("  - TRANSFER       : Fund transfer (0200)");
        System.out.println("  - BALANCE_INQUIRY: Balance inquiry (0200)");
        System.out.println("  - BILL_PAYMENT   : Bill payment (0200)");
        System.out.println();
        System.out.println("For more information, see the project documentation.");
    }
}
