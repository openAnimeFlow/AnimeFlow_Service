package com.ligg.flowclient.service;

import io.jsonwebtoken.io.AbstractDeserializer;
import io.jsonwebtoken.io.AbstractSerializer;
import io.jsonwebtoken.io.Deserializer;
import io.jsonwebtoken.io.Serializer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.io.Reader;
import java.util.Map;

/** Uses Jackson 3 for JJWT without loading its Jackson 2 integration module. */
final class Jackson3JwtCodec {
    private Jackson3JwtCodec() {
    }
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    static final Serializer<Map<String, ?>> SERIALIZER = new AbstractSerializer<>() {
        @Override
        protected void doSerialize(Map<String, ?> value, OutputStream out) {
            MAPPER.writeValue(out, value);
        }
    };
    static final Deserializer<Map<String, ?>> DESERIALIZER = new AbstractDeserializer<>() {
        @Override
        protected Map<String, ?> doDeserialize(Reader reader) {
            return MAPPER.readValue(reader, new TypeReference<Map<String, ?>>() { });
        }
    };


}
