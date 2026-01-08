package com.fep.integration.converter;

/**
 * Interface for converting between different message formats.
 *
 * @param <S> source type
 * @param <T> target type
 */
public interface MessageConverter<S, T> {

    /**
     * Converts source message to target format.
     *
     * @param source the source message
     * @return the converted target message
     */
    T convert(S source);

    /**
     * Checks if this converter supports the given source type.
     *
     * @param sourceType the source type class
     * @return true if supported
     */
    boolean supports(Class<?> sourceType);
}
