package com.accretivetg.ml.esplugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.search.rescore.RescoreContext;
import org.elasticsearch.search.rescore.Rescorer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MLRescoreContext extends RescoreContext {
    private final String type;
    private final String name;
    private final String modelName;
    private final String domain;
    private final IndexFieldData<?> itemIdField;
    private final MLRescoreMode scoreMode;
    private final Map<String, List<String>> modelContext;
    private final String contextJson;

    public MLRescoreContext(
            int windowSize,
            Rescorer rescorer,
            String type,
            String name,
            String modelName,
            String domain,
            @Nullable
            IndexFieldData<?> itemIdField,
            Map<String, List<String>> unprocessedModelContext,
            MLRescoreMode scoreMode
    ) throws JsonProcessingException {
        super(windowSize, rescorer);
        this.modelContext = Maps.newHashMap();

        for(Map.Entry<String, List<String>> entry : unprocessedModelContext.entrySet()) {
            this.modelContext.put(
                    entry.getKey(),
                    entry.getValue()
                        .stream()
                        .sorted()
                        .collect(Collectors.toList())
                    );
        }

        ObjectMapper objMapper = new ObjectMapper();
        contextJson = objMapper.writeValueAsString(modelContext);

        this.type = type.toLowerCase();
        this.name = name.toLowerCase();
        this.domain = domain;
        this.itemIdField = itemIdField;
        this.scoreMode = scoreMode;
        this.modelName = modelName;
    }

    public Map<String, List<String>> getModelContext() {
        return modelContext;
    }

    public String getModelContextAsJson() {
        return contextJson;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDomain() {
        return domain;
    }

    public IndexFieldData<?> getItemIdField() {
        return itemIdField;
    }

    public String getModelName(){
        return modelName;
    }

    public MLRescoreMode getScoreMode() {
        return scoreMode;
    }
}
