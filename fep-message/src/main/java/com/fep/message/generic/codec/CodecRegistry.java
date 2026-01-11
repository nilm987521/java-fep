package com.fep.message.generic.codec;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for GenericCodec implementations.
 * Provides singleton access to registered codecs by name.
 */
@Slf4j
public class CodecRegistry {

    private static final Map<String, GenericCodec> codecs = new ConcurrentHashMap<>();

    static {
        // Register built-in codecs
        register(new AsciiCodec());
        register(new BcdCodec());
        register(new HexCodec());
        register(new BinaryCodec());
        register(new EbcdicCodec());
        register(new PackedDecimalCodec());
    }

    /**
     * Registers a codec.
     *
     * @param codec the codec to register
     */
    public static void register(GenericCodec codec) {
        String name = codec.getName().toUpperCase();
        codecs.put(name, codec);
        log.debug("Registered codec: {}", name);
    }

    /**
     * Gets a codec by name.
     *
     * @param name the codec name (case-insensitive)
     * @return the codec
     * @throws IllegalArgumentException if codec not found
     */
    public static GenericCodec get(String name) {
        GenericCodec codec = codecs.get(name.toUpperCase());
        if (codec == null) {
            throw new IllegalArgumentException("Unknown codec: " + name);
        }
        return codec;
    }

    /**
     * Gets a codec by name, with fallback to default.
     *
     * @param name         the codec name (case-insensitive)
     * @param defaultCodec the default codec name if not found
     * @return the codec
     */
    public static GenericCodec getOrDefault(String name, String defaultCodec) {
        GenericCodec codec = codecs.get(name.toUpperCase());
        if (codec == null) {
            codec = codecs.get(defaultCodec.toUpperCase());
        }
        return codec;
    }

    /**
     * Checks if a codec is registered.
     *
     * @param name the codec name
     * @return true if registered
     */
    public static boolean hasCodec(String name) {
        return codecs.containsKey(name.toUpperCase());
    }

    /**
     * Gets all registered codec names.
     *
     * @return array of codec names
     */
    public static String[] getRegisteredCodecs() {
        return codecs.keySet().toArray(new String[0]);
    }
}
