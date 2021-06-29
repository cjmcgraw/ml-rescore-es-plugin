package com.accretivetg.ml.esplugin.models;

import com.google.protobuf.ByteString;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.TensorProto;
import org.tensorflow.framework.TensorShapeProto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Utils {
    public static class Strings {
        public static String mungeBadCharactersForStatsD(String s) {
            return s
                    .replace('.', '-')
                    .replace(':', '-');
        }
    }

    public static class Protobuf {
        public static List<ByteString> listStringToByteString(List<String> data) {
            return data
                    .stream()
                    .map(ByteString::copyFromUtf8)
                    .collect(Collectors.toUnmodifiableList());
        }

        public static TensorProto listToTensorProto(List<String> data) {
            return TensorProto
                    .newBuilder()
                    .setDtype(DataType.DT_STRING)
                    .setTensorShape(TensorShapeProto
                            .newBuilder()
                            .addDim(TensorShapeProto.Dim
                                    .newBuilder()
                                    .setSize(data.size())
                            )
                    )
                    .addAllStringVal(listStringToByteString(data))
                    .build();
        }

        public static Map<String, TensorProto> contextToContextProtos(Map<String, List<String>> context) {
            return context
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            (e) -> listToTensorProto(e.getValue())
                    ));
        }
    }
}
