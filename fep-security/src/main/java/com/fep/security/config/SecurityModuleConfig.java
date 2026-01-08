package com.fep.security.config;

import com.fep.security.crypto.CryptoService;
import com.fep.security.hsm.*;
import com.fep.security.key.KeyManager;
import com.fep.security.mac.MacService;
import com.fep.security.pin.PinBlockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for the security module.
 */
@Configuration
public class SecurityModuleConfig {

    @Value("${fep.security.hsm.vendor:SOFTWARE}")
    private String hsmVendor;

    @Value("${fep.security.hsm.host:localhost}")
    private String hsmHost;

    @Value("${fep.security.hsm.port:1500}")
    private int hsmPort;

    @Value("${fep.security.hsm.connection-timeout:5000}")
    private int connectionTimeout;

    @Value("${fep.security.hsm.read-timeout:10000}")
    private int readTimeout;

    @Bean
    public CryptoService cryptoService() {
        return new CryptoService();
    }

    @Bean
    public KeyManager keyManager(CryptoService cryptoService) {
        return new KeyManager(cryptoService);
    }

    @Bean
    public PinBlockService pinBlockService(CryptoService cryptoService, KeyManager keyManager) {
        return new PinBlockService(cryptoService, keyManager);
    }

    @Bean
    public MacService macService(CryptoService cryptoService, KeyManager keyManager) {
        return new MacService(cryptoService, keyManager);
    }

    @Bean
    @Primary
    public HsmAdapter hsmAdapter(CryptoService cryptoService, KeyManager keyManager,
                                  PinBlockService pinBlockService, MacService macService) {
        HsmVendor vendor = HsmVendor.valueOf(hsmVendor.toUpperCase());

        HsmConnectionConfig config = HsmConnectionConfig.builder()
                .vendor(vendor)
                .primaryHost(hsmHost)
                .primaryPort(hsmPort)
                .connectionTimeout(connectionTimeout)
                .readTimeout(readTimeout)
                .build();

        return switch (vendor) {
            case THALES -> new ThalesHsmAdapter(config);
            case SOFTWARE -> new SoftwareHsmAdapter(cryptoService, keyManager, pinBlockService, macService);
            default -> throw new IllegalArgumentException("Unsupported HSM vendor: " + vendor);
        };
    }
}
