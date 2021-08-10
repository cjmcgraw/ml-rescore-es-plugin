package com.accretivetg.ml.esplugin;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.rest.RestStatus;

public class InvalidModelInputException extends ElasticsearchStatusException {
    public InvalidModelInputException(String msg, RestStatus status, Throwable cause, Object... args) {
        super(msg, status, cause, args);
    }

    public InvalidModelInputException(String msg, RestStatus status, Object... args) {
        super(msg, status, args);
    }
}
