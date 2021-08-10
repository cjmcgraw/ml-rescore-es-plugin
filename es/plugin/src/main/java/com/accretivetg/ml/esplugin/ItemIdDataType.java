package com.accretivetg.ml.esplugin;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;

import java.io.IOException;
import java.util.Locale;

public enum ItemIdDataType implements Writeable  {
    UINT32,
    UINT64,
    INT32,
    INT64,
    STRING;

    public static ItemIdDataType readFromStream(StreamInput in) throws IOException {
        return in.readEnum(ItemIdDataType.class);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(this);
    }

    public static ItemIdDataType fromString(String itemIdDataType) {
        for (ItemIdDataType type : values()) {
            if (itemIdDataType.toLowerCase(Locale.ROOT).equals(type.name().toLowerCase())) {
                return type;
            }
        }
        throw new IllegalArgumentException("illegal ItemIdDataType [" + itemIdDataType + "]");
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
