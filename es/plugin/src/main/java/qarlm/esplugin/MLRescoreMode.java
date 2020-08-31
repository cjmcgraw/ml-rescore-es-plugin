package qarlm.esplugin;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;

import java.io.IOException;
import java.util.Locale;

public enum MLRescoreMode implements Writeable {
    Avg {
        @Override
        public float combine(float primary, float secondary) {
            return (primary + secondary) / 2;
        }

        @Override
        public String toString() {
            return "avg";
        }
    },
    Replace {
       @Override
       public float combine(float primary, float secondary) {
           return primary;
       }

       @Override
        public String toString() {
           return "replace";
       }
    },
    Total {
        @Override
        public float combine(float primary, float secondary) {
            return primary + secondary;
        }

        @Override
        public String toString() {
            return "sum";
        }
    },
    Multiply {
        @Override
        public float combine(float primary, float secondary) {
            return primary * secondary;
        }

        @Override
        public String toString() {
            return "product";
        }
    };

    public abstract float combine(float primary, float secondary);

    public static MLRescoreMode readFromStream(StreamInput in) throws IOException {
        return in.readEnum(MLRescoreMode.class);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(this);
    }

    public static MLRescoreMode fromString(String scoreMode) {
        for (MLRescoreMode mode : values()) {
            if (scoreMode.toLowerCase(Locale.ROOT).equals(mode.name().toLowerCase(Locale.ROOT))) {
                return mode;
            }
        }
        throw new IllegalArgumentException("illegal score_mode [" + scoreMode + "]");
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
