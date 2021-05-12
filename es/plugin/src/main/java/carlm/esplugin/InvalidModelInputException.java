package carlm.esplugin;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.rest.RestStatus;

public class InvalidModelInputException extends ElasticsearchStatusException {
    public InvalidModelInputException(String msg, RestStatus status, Throwable cause, Object... args) {
        super(msg, status, cause, args);
    }
}
