package com.accretivetg.ml.esplugin.models;

import com.accretivetg.ml.esplugin.ItemIdDataType;
import com.google.common.primitives.Ints;
import com.google.protobuf.ByteString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.rest.RestStatus;
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
        private static final Logger log = LogManager.getLogger(Utils.Protobuf.class);
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

        public static TensorProto listToTensorProto(ItemIdDataType type, List<String> data) {
            TensorProto.Builder tensorBuilder = TensorProto
                    .newBuilder()
                    .setTensorShape(TensorShapeProto
                            .newBuilder()
                            .addDim(TensorShapeProto.Dim
                                    .newBuilder()
                                    .setSize(data.size())
                            )
                    );

            try {
                switch (type) {
                    case UINT32:
                        return tensorBuilder
                                .setDtype(DataType.DT_UINT32)
                                .addAllUint32Val(
                                        data
                                                .stream()
                                                .mapToInt(Integer::parseInt)
                                                .boxed()
                                                .collect(Collectors.toList())
                                )
                                .build();
                    case UINT64:
                        return tensorBuilder
                                .setDtype(DataType.DT_UINT64)
                                .addAllUint64Val(
                                        data
                                                .stream()
                                                .mapToLong(Long::parseLong)
                                                .boxed()
                                                .collect(Collectors.toList())
                                )
                                .build();
                    case INT32:
                        return tensorBuilder
                                .setDtype(DataType.DT_INT32)
                                .addAllIntVal(
                                        data
                                                .stream()
                                                .mapToInt(Integer::parseInt)
                                                .boxed()
                                                .collect(Collectors.toList())
                                )
                                .build();
                    case INT64:
                        return tensorBuilder
                                .setDtype(DataType.DT_INT64)
                                .addAllInt64Val(
                                        data
                                                .stream()
                                                .mapToLong(Long::parseLong)
                                                .boxed()
                                                .collect(Collectors.toList())
                                )
                                .build();
                    case STRING:
                        return listToTensorProto(data);
                    default:
                        throw new ElasticsearchStatusException(
                                "invalid ItemIdDataType given! " + type.toString(),
                                RestStatus.BAD_REQUEST
                        );
                }
            } catch(ElasticsearchStatusException exception) {
                throw exception;
            } catch (Exception exception) {
                log.error("Failed to parse itemIds for unknown reason!");
                log.catching(exception);
                throw new ElasticsearchStatusException(
                        "Failed to parse ItemId by DataType=" + type.toString(),
                        RestStatus.BAD_REQUEST,
                        exception
                );
            }
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
